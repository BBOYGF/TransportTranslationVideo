package com.app.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开头视频自动生成器。
 *
 * <p>原来需要手工执行 {@code movy .\videos\heginning.js}，再在浏览器里点 render，
 * 最后把下载下来的 webm 选进界面。现在只要选一张图片，本类会：</p>
 * <ol>
 *     <li>把图片复制到 movy 项目的 videos 目录；</li>
 *     <li>根据模板生成 heginning.js（自动替换图片名和标题）；</li>
 *     <li>启动 movy（webpack-dev-server）；</li>
 *     <li>用 headless Chrome 打开页面并调用 window.movy.startRender() 触发录制；</li>
 *     <li>等 webm 下载完成后复制到 ./video/ 目录并返回。</li>
 * </ol>
 *
 * @Author OverCode
 */
public class BeginningVideoGenerator {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * movy 项目目录，可用 -Dmovy.project.dir=xxx 覆盖
     */
    public static final String MOVY_PROJECT_DIR =
            System.getProperty("movy.project.dir", "E:\\JavaProject\\video_handing_beginning");

    /**
     * movy 入口脚本名（生成在 videos 目录下）
     */
    private static final String ENTRY_FILE_NAME = "heginning.js";

    /**
     * 模板路径（classpath）
     */
    private static final String TEMPLATE_PATH = "template/beginning.js.tpl";

    /**
     * dev server 端口
     */
    private static final int PORT = 8123;

    /**
     * CCapture 保存的文件名 == document.title == entry 名
     */
    private static final String RENDERED_FILE_NAME = "heginning.webm";

    /**
     * 渲染默认缩放（图片大小），可按需调整
     */
    public static final String DEFAULT_SCALE = "18";

    public static final String DEFAULT_TEXT1 = "Jev TypeSafe AI";
    public static final String DEFAULT_TEXT2 = "CEO 最新播客";

    /**
     * 匹配一个字符串字面量（支持单/双引号，且不会被转义引号截断）
     */
    private static final Pattern FIRST_STRING_PATTERN =
            Pattern.compile("([\"'])(?:[^\"'\\\\]|\\\\.)*\\1");

    /**
     * 是否使用无头浏览器渲染。无头更安静，但走软件渲染会慢一些；
     * 机器显卡好、想更快时改成 false 会弹出一个 Chrome 窗口。
     */
    private boolean headless = true;

    public BeginningVideoGenerator setHeadless(boolean headless) {
        this.headless = headless;
        return this;
    }

    /**
     * 根据图片自动生成开头视频。
     *
     * @param image 用作开头画面的图片（就是脚本里的那一张图）
     * @param text1 开头主标题，为空用默认值
     * @param text2 开头副标题，为空用默认值
     * @return 生成好的 webm 文件（位于程序运行目录 ./video 下）
     */
    public File generate(File image, String text1, String text2) throws Exception {
        if (image == null || !image.exists()) {
            throw new IllegalArgumentException("图片不存在：" + image);
        }
        File projectDir = new File(MOVY_PROJECT_DIR);
        if (!projectDir.exists()) {
            throw new IllegalStateException("movy 项目目录不存在：" + MOVY_PROJECT_DIR
                    + "，可通过 -Dmovy.project.dir=路径 指定");
        }
        File videosDir = FileUtil.mkdir(new File(projectDir, "videos"));

        // 0、补齐 CCapture 脚本。
        // movy 的 webpack 配置从 <movy>/node_modules/ccapture.js/build 提供该脚本，
        // 但 npm 会把 ccapture.js 提升(hoist)到顶层 node_modules，
        // 结果页面上 /CCapture.all.min.js 变成 404，startRender() 会报 "CCapture is not defined"。
        // 这里把脚本复制到同为静态资源目录的 videos 下兜底。
        ensureCCaptureScript(projectDir, videosDir);

        // 1、复制图片到 videos 目录
        String imageName = FileNameUtils.sanitizeFileName(image.getName());
        File targetImage = new File(videosDir, imageName);
        copyIfNeeded(image, targetImage);
        logger.info("图片已复制到：{}", targetImage.getAbsolutePath());

        // 2、替换入口脚本里的参数（图片名 + 两个标题）。
        // 【重要】只替换参数，脚本的动画部分一律不动 —— 这样用户自己改过的脚本不会被覆盖。
        String text1Value = StrUtil.blankToDefault(text1, DEFAULT_TEXT1);
        String text2Value = StrUtil.blankToDefault(text2, DEFAULT_TEXT2);
        File entryJs = new File(videosDir, ENTRY_FILE_NAME);
        String script;
        if (entryJs.exists()) {
            script = replaceScriptParams(FileUtil.readUtf8String(entryJs), imageName, text1Value, text2Value);
            logger.info("已替换已有脚本的参数（图片/主标题/副标题），其余内容保持不变：{}", entryJs.getAbsolutePath());
        } else {
            // 首次运行时 movy 项目里还没有脚本，用内置模板创建一份
            script = readTemplate()
                    .replace("{{IMAGE}}", imageName)
                    .replace("{{SCALE}}", DEFAULT_SCALE)
                    .replace("{{TEXT1}}", escapeJs(text1Value))
                    .replace("{{TEXT2}}", escapeJs(text2Value));
            logger.info("未找到已有脚本，用内置模板创建：{}", entryJs.getAbsolutePath());
        }
        FileUtil.writeUtf8String(script, entryJs);
        logger.info("脚本参数：图片={} 主标题={} 副标题={}", imageName, text1Value, text2Value);

        // 3、准备下载目录
        File downloadDir = FileUtil.mkdir(new File(videosDir, "downloads"));
        FileUtil.del(new File(downloadDir, RENDERED_FILE_NAME));

        // 4、启动 movy 服务
        freePort(PORT);
        Process process = startMovy(projectDir);
        WebDriver driver = null;
        try {
            waitServerReady();
            driver = createDriver(downloadDir);
            driver.get("http://localhost:" + PORT + "/index.html");
            waitMovyReady(driver);
            logger.info("开始渲染开头视频……");
            ((JavascriptExecutor) driver).executeScript("window.movy.startRender();");
            waitRenderFinished(driver, downloadDir);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
            stopProcess(process);
        }

        File rendered = new File(downloadDir, RENDERED_FILE_NAME);
        if (!rendered.exists()) {
            throw new IllegalStateException("渲染结束但没有找到生成的视频：" + rendered.getAbsolutePath());
        }

        // 5、复制到程序目录，避免被 movy 目录清理掉
        File outputDir = FileUtil.mkdir(new File("./video"));
        File output = new File(outputDir, "beginning-" + System.currentTimeMillis() + ".webm");
        copyIfNeeded(rendered, output);
        logger.info("开头视频生成成功：{}", output.getAbsolutePath());
        return output;
    }

    /**
     * 复制文件，源和目标为同一路径时跳过（hutool 在相同路径时会直接抛异常）
     */
    private void copyIfNeeded(File source, File target) {
        if (source.getAbsoluteFile().equals(target.getAbsoluteFile())) {
            return;
        }
        FileUtil.copy(source, target, true);
    }

    /**
     * 确保页面能加载到 CCapture.all.min.js。
     * movy 项目里 ccapture.js 被 npm 提升到顶层 node_modules，
     * 而 movy 的 webpack 配置只从 <movy>/node_modules/ccapture.js/build 提供，
     * 因此需要把脚本放到 videos 目录（同样被作为静态资源目录）里兜底。
     */
    private void ensureCCaptureScript(File projectDir, File videosDir) {
        String scriptName = "CCapture.all.min.js";
        File target = new File(videosDir, scriptName);
        File[] candidates = new File[]{
                new File(projectDir, "node_modules/movy/node_modules/ccapture.js/build/" + scriptName),
                new File(projectDir, "node_modules/ccapture.js/build/" + scriptName)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                if (!target.exists() || target.length() != candidate.length()) {
                    FileUtil.copy(candidate, target, true);
                    logger.info("已补齐 {}：{}", scriptName, target.getAbsolutePath());
                }
                return;
            }
        }
        logger.warn("未找到 {}，如果页面报 CCapture is not defined，请在 movy 项目执行 npm install", scriptName);
    }

    /**
     * 创建渲染用的浏览器。
     *
     * <p>渲染用的用户目录里没有登录态，坏了直接删掉重建最省事，
     * 所以这里失败时会清理该目录后重试一次
     * （常见失败：上次非正常退出把 profile 弄坏，报 "chrome not reachable"）。</p>
     */
    private ChromeDriver createRenderDriver(ChromeOptions options, String userDataDirPath) {
        try {
            return new ChromeDriver(options);
        } catch (Exception first) {
            logger.warn("渲染用浏览器启动失败，清理用户数据目录后重试一次：{}", first.getMessage());
            try {
                ChromeDriverUtil.closeProfileChrome(userDataDirPath);
                FileUtil.del(userDataDirPath);
                FileUtil.mkdir(userDataDirPath);
            } catch (Exception e) {
                logger.warn("清理渲染用用户数据目录失败：{}", e.getMessage());
            }
            try {
                return new ChromeDriver(options);
            } catch (Exception second) {
                throw new IllegalStateException("渲染用浏览器启动失败（已尝试清理用户数据目录 "
                        + userDataDirPath + "）：" + second.getMessage(), second);
            }
        }
    }

    /**
     * 只替换脚本里的「图片名」和「两个标题」，其余内容（整段动画）原样保留。
     *
     * <p>匹配规则：逐行处理、跳过注释行；</p>
     * <ul>
     *     <li>含 {@code mo.addImage(} 的行 → 替换该行第一个字符串字面量（图片名）</li>
     *     <li>含 {@code mo.addText(} 的行 → 第 1 个替换成主标题，第 2 个替换成副标题</li>
     * </ul>
     * <p>跳过注释行很关键：原脚本里有注释掉的 {@code mo.addText} 示例，不能动它们。</p>
     *
     * @param script    现有脚本内容
     * @param imageName 新的图片文件名
     * @param text1     主标题
     * @param text2     副标题
     * @return 替换后的脚本内容
     */
    String replaceScriptParams(String script, String imageName, String text1, String text2) {
        String[] lines = script.split("\n", -1);
        int textIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            if (trimmed.contains("mo.addImage(")) {
                lines[i] = replaceFirstStringLiteral(lines[i], imageName);
            } else if (trimmed.contains("mo.addText(")) {
                textIndex++;
                lines[i] = replaceFirstStringLiteral(lines[i], textIndex == 1 ? text1 : text2);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 替换一行里的第一个字符串字面量（保留原来的引号风格）
     */
    private String replaceFirstStringLiteral(String line, String value) {
        Matcher matcher = FIRST_STRING_PATTERN.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        String quote = matcher.group(1);
        String escaped = value.replace("\\", "\\\\").replace(quote, "\\" + quote);
        return line.substring(0, matcher.start()) + quote + escaped + quote + line.substring(matcher.end());
    }

    private String readTemplate() {
        try (java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("找不到模板：" + TEMPLATE_PATH);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取模板失败：" + TEMPLATE_PATH, e);
        }
    }

    /**
     * 转义 JS 字符串中的引号与反斜杠
     */
    private String escapeJs(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }

    /**
     * 规范化路径（去掉 "./" 之类的相对段），保证和 Chrome 命令行里的路径一致
     */
    private String normalizePath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (Exception e) {
            return new File(path).getAbsolutePath();
        }
    }

    /**
     * 启动 movy，注意 --open= 是为了阻止开发服务器自动打开系统浏览器
     */
    private Process startMovy(File projectDir) throws Exception {
        File logFile = FileUtil.mkdir(new File("./logs")).toPath().resolve("movy.log").toFile();
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd", "/c", "node",
                "node_modules\\movy\\bin\\movy.js",
                "videos\\" + ENTRY_FILE_NAME,
                "--port", String.valueOf(PORT),
                "--open=");
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        Process process = processBuilder.start();
        logger.info("movy 已启动，日志：{}", logFile.getAbsolutePath());
        return process;
    }

    /**
     * 等待 dev server 起来（webpack 首次编译需要时间）
     */
    private void waitServerReady() {
        String url = "http://localhost:" + PORT + "/index.html";
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(3).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                int status = HttpUtil.createGet(url).timeout(3000).execute().getStatus();
                if (status == 200) {
                    logger.info("movy 服务已就绪：{}", url);
                    return;
                }
            } catch (Exception ignored) {
                // 还没编译好，继续等
            }
            sleep(2000);
        }
        throw new IllegalStateException("等待 movy 服务超时，请查看 ./logs/movy.log");
    }

    private WebDriver createDriver(File downloadDir) {
        ChromeDriverUtil.prepare();
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
            // 无头环境用 SwiftShader 软件渲染 WebGL
            options.addArguments("--enable-unsafe-swiftshader");
            options.addArguments("--use-gl=angle");
            options.addArguments("--use-angle=swiftshader");
        }
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        // 用规范化后的绝对路径：一是避免 "./temp/..." 这种写法，二是要和占用检测用的路径保持一致
        String userDataDirPath = normalizePath("./temp/chrome-movy");
        FileUtil.mkdir(new File(userDataDirPath));
        options.addArguments("--user-data-dir=" + userDataDirPath);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir.getAbsolutePath());
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        options.setExperimentalOption("prefs", prefs);

        // 上次渲染的浏览器如果没关干净，会占用同一个用户数据目录，导致新实例直接退出
        if (ChromeDriverUtil.isProfileInUse(userDataDirPath)) {
            logger.warn("上次渲染用的浏览器还开着，先关闭：{}", userDataDirPath);
            ChromeDriverUtil.closeProfileChrome(userDataDirPath);
        }
        ChromeDriver driver = createRenderDriver(options, userDataDirPath);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("behavior", "allow");
            params.put("downloadPath", downloadDir.getAbsolutePath());
            driver.executeCdpCommand("Page.setDownloadBehavior", params);
        } catch (Exception e) {
            logger.debug("设置下载目录 CDP 命令失败，已回退到 prefs：{}", e.getMessage());
        }
        return driver;
    }

    /**
     * 等页面里的 movy 初始化完成
     */
    private void waitMovyReady(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Wait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofMinutes(3))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(Exception.class);
        wait.until(d -> Boolean.TRUE.equals(
                js.executeScript("return typeof window.movy !== 'undefined' && window.movy !== null;")));
        logger.info("movy 页面加载完成，准备渲染");
    }

    /**
     * 等渲染结束并且文件下载完成
     */
    private void waitRenderFinished(WebDriver driver, File downloadDir) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        File rendered = new File(downloadDir, RENDERED_FILE_NAME);
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(20).toMillis();
        long lastSize = -1;
        int stableCount = 0;
        while (System.currentTimeMillis() < deadline) {
            boolean rendering = Boolean.TRUE.equals(js.executeScript("return window.movy.isRendering === true;"));
            if (!rendering) {
                if (rendered.exists() && rendered.length() > 0) {
                    if (rendered.length() == lastSize) {
                        stableCount++;
                    } else {
                        stableCount = 0;
                        lastSize = rendered.length();
                    }
                    // 文件大小连续两次不变，认为写入完成
                    if (stableCount >= 2) {
                        logger.info("渲染完成，文件大小：{} 字节", rendered.length());
                        return;
                    }
                } else {
                    // 还在保存，或下载还没落盘
                    logger.info("渲染已结束，等待视频文件落盘……");
                }
            }
            sleep(3000);
        }
        throw new IllegalStateException("渲染超时（20 分钟），请查看 ./logs/movy.log");
    }

    /**
     * 结束 node 进程及其子进程。
     * 先用 taskkill /T 杀进程树，再 destroy，避免 node 变成孤儿进程一直占着端口。
     */
    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            Process killer = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(process.pid()))
                    .redirectErrorStream(true)
                    .start();
            killer.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("taskkill 结束 movy 进程失败：{}", e.getMessage());
        }
        if (process.isAlive()) {
            process.destroy();
        }
    }

    /**
     * 启动前释放端口，避免上次异常退出留下的 movy 进程占着端口
     */
    private void freePort(int port) {
        try {
            Process process = new ProcessBuilder("cmd", "/c", "netstat -ano | findstr :" + port)
                    .redirectErrorStream(true)
                    .start();
            Set<String> pids = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("LISTENING")) {
                        String[] parts = line.trim().split("\\s+");
                        pids.add(parts[parts.length - 1]);
                    }
                }
            }
            for (String pid : pids) {
                if ("0".equals(pid)) {
                    continue;
                }
                logger.info("端口 {} 已被 PID {} 占用，先结束它", port, pid);
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", pid).redirectErrorStream(true).start()
                        .waitFor(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.debug("释放端口 {} 失败：{}", port, e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 干掉可能残留的 movy 相关 node 进程（异常退出后手动调用）
     */
    public void killLeftoverMovy() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c",
                    "wmic process where \"commandline like '%movy%'\" get processid"});
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String pid = line.trim();
                    if (pid.matches("\\d+")) {
                        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "taskkill", "/F", "/T", "/PID", pid});
                        logger.info("已结束残留 movy 进程：{}", pid);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("清理残留 movy 进程失败：{}", e.getMessage());
        }
    }
}
