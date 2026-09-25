package com.app.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.http.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChromeDriver 自动检测 / 自动更新工具。
 *
 * <p>Chrome 每次自动升级后旧驱动就会报
 * "This version of ChromeDriver only supports Chrome version xxx"，
 * 本类负责：</p>
 * <ol>
 *     <li>自动读取本机 Chrome 版本（注册表）；</li>
 *     <li>优先使用本地缓存 driver/&lt;版本&gt;/chromedriver.exe；</li>
 *     <li>缓存缺失时从 Chrome for Testing 官方接口下载对应 win64 驱动并解压；</li>
 *     <li>以上任意环节失败时，清空 webdriver.chrome.driver，
 *         交给 Selenium 4 内置的 Selenium Manager 自动匹配（兜底）。</li>
 * </ol>
 *
 * <p>人工下载地址（Chrome for Testing Stable）：
 * https://googlechromelabs.github.io/chrome-for-testing/#stable</p>
 *
 * @Author OverCode
 */
public final class ChromeDriverUtil {

    private static final Logger log = LoggerFactory.getLogger(ChromeDriverUtil.class);

    /**
     * Chrome for Testing 稳定版页面（人工下载地址，仅用于日志提示）
     */
    public static final String CFT_STABLE_PAGE = "https://googlechromelabs.github.io/chrome-for-testing/#stable";

    /**
     * Chrome for Testing 已知可用版本 + 各平台下载地址（自动更新用的接口）
     */
    private static final String CFT_KNOWN_GOOD_JSON =
            "https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json";

    /**
     * 本地驱动缓存目录，相对程序运行目录
     */
    private static final File DRIVER_DIR = new File("driver");

    /**
     * 接口 JSON 的本地缓存名（网络异常时离线兜底）
     */
    private static final String CFT_CACHE_FILE = "known-good-versions-with-downloads.json";

    private static final String DRIVER_EXE = "chromedriver.exe";

    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");

    private static volatile boolean resolved = false;

    private static volatile String driverPath;

    private ChromeDriverUtil() {
    }

    /**
     * 准备 ChromeDriver：自动判断版本并自动下载匹配的驱动。
     *
     * @return 本地驱动的绝对路径；返回 null 时表示交给 Selenium Manager 自动管理
     */
    public static synchronized String prepare() {
        if (resolved) {
            return driverPath;
        }
        resolved = true;

        String chromeVersion = detectChromeVersion();
        if (StrUtil.isBlank(chromeVersion)) {
            log.warn("未检测到本机 Chrome 版本，交给 Selenium Manager 自动匹配驱动");
            System.clearProperty("webdriver.chrome.driver");
            return null;
        }
        log.info("检测到本机 Chrome 版本：{}", chromeVersion);

        File cached = findCachedDriver(chromeVersion);
        if (cached == null) {
            try {
                cached = downloadAndExtract(chromeVersion);
            } catch (Exception e) {
                log.error("自动下载 ChromeDriver 失败，将交给 Selenium Manager 自动匹配驱动。人工下载地址：{}",
                        CFT_STABLE_PAGE, e);
            }
        }

        if (cached != null) {
            System.setProperty("webdriver.chrome.driver", cached.getAbsolutePath());
            driverPath = cached.getAbsolutePath();
            log.info("使用本地 ChromeDriver：{}", driverPath);
            return driverPath;
        }

        System.clearProperty("webdriver.chrome.driver");
        return null;
    }

    // ==================== 用户数据目录占用检查 ====================

    /**
     * 把用户数据目录路径传给 PowerShell 用的环境变量名（避免命令行引号转义问题）
     */
    private static final String PROFILE_PATTERN_ENV = "OVERCODE_CHROME_PROFILE_PATTERN";

    /**
     * 第二个模式：原始写法（可能带 ".\" 等相对段），两个模式取并集，命中率更高
     */
    private static final String PROFILE_PATTERN_ENV_2 = "OVERCODE_CHROME_PROFILE_PATTERN2";

    /**
     * PowerShell 脚本：找出命令行里带指定用户数据目录的 chrome.exe 进程号。
     *
     * <p>注意：这里<b>不能出现双引号</b>。因为整段脚本是通过 {@code -Command} 传进去的，
     * Java 的 ProcessBuilder 会把它包一层引号并转义内部双引号，
     * PowerShell 就会收到被破坏的 -Filter 参数，报 "Get-CimInstance : 无效查询"。
     * 所以这里改成只用单引号 + Where-Object 过滤进程名。</p>
     */
    private static final String FIND_PROFILE_PROCESS_SCRIPT =
            "Get-CimInstance Win32_Process | "
                    + "Where-Object { $_.Name -eq 'chrome.exe' -and ( $_.CommandLine -like $env:" + PROFILE_PATTERN_ENV
                    + " -or $_.CommandLine -like $env:" + PROFILE_PATTERN_ENV_2 + " ) } | "
                    + "Select-Object -ExpandProperty ProcessId";

    /**
     * 判断某个用户数据目录是否正被 Chrome 占用。
     *
     * <p>Chrome 不允许两个实例同时使用同一个 {@code --user-data-dir}，
     * 第二个实例会直接退出，Selenium 报
     * "session not created: Chrome instance exited"。
     * 最常见的原因就是上一次上传/渲染的浏览器窗口没关干净。</p>
     */
    public static boolean isProfileInUse(String userDataDir) {
        return !findProfileProcessIds(userDataDir).isEmpty();
    }

    /**
     * 查出使用指定用户数据目录的 chrome.exe 进程号
     */
    public static List<Long> findProfileProcessIds(String userDataDir) {
        List<Long> pids = new ArrayList<>();
        if (StrUtil.isBlank(userDataDir)) {
            return pids;
        }
        // 必须规范化路径：Chrome 进程命令行里是规范化后的绝对路径，
        // 如果这里传的是 ".\temp\xxx" 这种带相对段的写法，就会匹配不上
        String normalized = normalizeDir(userDataDir);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    FIND_PROFILE_PROCESS_SCRIPT);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put(PROFILE_PATTERN_ENV, "*" + normalized + "*");
            processBuilder.environment().put(PROFILE_PATTERN_ENV_2, "*" + userDataDir + "*");
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    String trimmed = line.trim();
                    if (trimmed.matches("\\d+")) {
                        pids.add(Long.parseLong(trimmed));
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("查询占用 {} 的 Chrome 失败（退出码 {}）：{}", normalized, exitCode, output.toString().trim());
            }
        } catch (Exception e) {
            log.warn("查询占用 {} 的 Chrome 异常：{}", normalized, e.getMessage());
        }
        return pids;
    }

    /**
     * 规范化路径，去掉 "." 之类相对段，保证能匹配上 Chrome 命令行里的路径
     */
    private static String normalizeDir(String dir) {
        try {
            return new File(dir).getCanonicalPath();
        } catch (Exception e) {
            return new File(dir).getAbsolutePath();
        }
    }

    /**
     * 关闭占用指定用户数据目录的 Chrome。
     *
     * <p>先尝试<b>正常关闭</b>（让 Chrome 自己退出，保证登录 cookie 落盘），
     * 还活着再强杀，避免把刚登录的会话弄丢。</p>
     */
    public static void closeProfileChrome(String userDataDir) {
        List<Long> pids = findProfileProcessIds(userDataDir);
        if (pids.isEmpty()) {
            return;
        }
        log.warn("检测到 {} 个 Chrome 正在使用用户数据目录 {}，先尝试正常关闭：{}", pids.size(), userDataDir, pids);
        for (Long pid : pids) {
            killProcess(pid, false);
        }
        sleepQuietly(3000);

        List<Long> remain = findProfileProcessIds(userDataDir);
        if (remain.isEmpty()) {
            log.info("占用该用户数据目录的 Chrome 已正常退出");
            return;
        }
        log.warn("仍有 Chrome 未退出，强制结束：{}", remain);
        for (Long pid : remain) {
            killProcess(pid, true);
        }
        sleepQuietly(1500);
    }

    private static void killProcess(Long pid, boolean force) {
        try {
            List<String> command = new ArrayList<>();
            command.add("taskkill");
            command.add("/PID");
            command.add(String.valueOf(pid));
            command.add("/T");
            if (force) {
                command.add("/F");
            }
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("结束进程 {} 失败：{}", pid, e.getMessage());
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 读取本机 Chrome 主版本号。
     */
    public static String detectChromeVersion() {
        String version = queryRegistryVersion("HKCU\\Software\\Google\\Chrome\\BLBeacon");
        if (version == null) {
            version = queryRegistryVersion(
                    "HKLM\\SOFTWARE\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Google Chrome");
        }
        if (version == null) {
            version = queryRegistryVersion(
                    "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Google Chrome");
        }
        return version;
    }

    private static String queryRegistryVersion(String key) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("reg", "query", key, "/v", "version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = VERSION_PATTERN.matcher(line);
                    if (matcher.find()) {
                        return matcher.group();
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.debug("读取注册表 {} 失败", key, e);
        }
        return null;
    }

    /**
     * 查找本地已缓存且版本匹配的驱动。
     */
    private static File findCachedDriver(String chromeVersion) {
        File versionDir = new File(DRIVER_DIR, chromeVersion);
        File exe = new File(versionDir, DRIVER_EXE);
        if (exe.exists()) {
            return exe;
        }
        File found = searchFile(versionDir, DRIVER_EXE);
        if (found != null) {
            return found;
        }
        // 兜底：扫描缓存目录下所有驱动，取一个同主版本的（Chrome for Testing 小版本可能对不上）
        File[] dirs = DRIVER_DIR.listFiles(File::isDirectory);
        if (dirs != null) {
            String major = majorOf(chromeVersion);
            for (File dir : dirs) {
                if (major != null && dir.getName().startsWith(major + ".")) {
                    File matched = searchFile(dir, DRIVER_EXE);
                    if (matched != null) {
                        log.info("使用同主版本缓存驱动：{}", matched.getAbsolutePath());
                        return matched;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 下载并解压匹配的驱动。
     */
    private static File downloadAndExtract(String chromeVersion) {
        String json = fetchKnownGoodJson();
        JsonArray versions = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("versions");
        JsonObject picked = pickBestVersion(versions, chromeVersion);
        if (picked == null) {
            throw new IllegalStateException("Chrome for Testing 中未找到可用的 chromedriver 版本：" + chromeVersion);
        }
        String pickedVersion = picked.get("version").getAsString();
        String url = findWin64DownloadUrl(picked);
        if (StrUtil.isBlank(url)) {
            throw new IllegalStateException("未找到 win64 平台驱动下载地址，版本：" + pickedVersion);
        }

        File destDir = new File(DRIVER_DIR, pickedVersion);
        File exe = new File(destDir, DRIVER_EXE);
        if (exe.exists()) {
            return exe;
        }

        FileUtil.mkdir(DRIVER_DIR);
        File zipFile = new File(DRIVER_DIR, "chromedriver-" + pickedVersion + "-win64.zip");
        log.info("开始下载 ChromeDriver {}，地址：{}", pickedVersion, url);
        long size = HttpUtil.downloadFile(url, zipFile);
        log.info("下载完成，共 {} 字节，开始解压", size);
        ZipUtil.unzip(zipFile, destDir);
        FileUtil.del(zipFile);

        File found = searchFile(destDir, DRIVER_EXE);
        if (found == null) {
            throw new IllegalStateException("解压后未找到 chromedriver.exe，目录：" + destDir.getAbsolutePath());
        }
        // 统一放到 driver/<版本>/chromedriver.exe，方便下次命中缓存
        if (!found.getParentFile().equals(destDir)) {
            FileUtil.copy(found, exe, true);
            return exe;
        }
        return found;
    }

    /**
     * 获取已知版本 JSON，优先网络，失败则用本地缓存。
     */
    private static String fetchKnownGoodJson() {
        File cacheFile = new File(DRIVER_DIR, CFT_CACHE_FILE);
        try {
            String json = HttpUtil.get(CFT_KNOWN_GOOD_JSON, 60_000);
            if (StrUtil.isNotBlank(json)) {
                FileUtil.mkdir(DRIVER_DIR);
                FileUtil.writeUtf8String(json, cacheFile);
                return json;
            }
        } catch (Exception e) {
            log.warn("请求 Chrome for Testing 版本接口失败，尝试使用本地缓存：{}", e.getMessage());
        }
        if (cacheFile.exists()) {
            return FileUtil.readUtf8String(cacheFile);
        }
        throw new IllegalStateException("无法获取 Chrome for Testing 版本信息，请检查网络。人工下载地址：" + CFT_STABLE_PAGE);
    }

    private static JsonObject pickBestVersion(JsonArray versions, String targetVersion) {
        JsonObject exact = null;
        JsonObject bestSameMajor = null;
        int[] targetParts = parseParts(targetVersion);
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (StrUtil.isBlank(findWin64DownloadUrl(version))) {
                continue;
            }
            String ver = version.get("version").getAsString();
            if (ver.equals(targetVersion)) {
                exact = version;
                break;
            }
            int[] parts = parseParts(ver);
            if (parts.length > 0 && targetParts.length > 0 && parts[0] == targetParts[0]) {
                if (bestSameMajor == null
                        || compare(parts, parseParts(bestSameMajor.get("version").getAsString())) > 0) {
                    bestSameMajor = version;
                }
            }
        }
        if (exact != null) {
            return exact;
        }
        if (bestSameMajor != null) {
            log.info("未找到与 Chrome {} 完全一致的驱动，使用同主版本 {}",
                    targetVersion, bestSameMajor.get("version").getAsString());
            return bestSameMajor;
        }
        for (int i = versions.size() - 1; i >= 0; i--) {
            JsonObject version = versions.get(i).getAsJsonObject();
            if (StrUtil.isNotBlank(findWin64DownloadUrl(version))) {
                log.warn("未找到同主版本驱动，回退到最新可用版本 {}", version.get("version").getAsString());
                return version;
            }
        }
        return null;
    }

    private static String findWin64DownloadUrl(JsonObject version) {
        if (version == null || !version.has("downloads")) {
            return null;
        }
        JsonObject downloads = version.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("chromedriver")) {
            return null;
        }
        JsonArray drivers = downloads.getAsJsonArray("chromedriver");
        for (JsonElement element : drivers) {
            JsonObject driver = element.getAsJsonObject();
            if ("win64".equals(driver.get("platform").getAsString())) {
                return driver.get("url").getAsString();
            }
        }
        return null;
    }

    private static File searchFile(File root, String fileName) {
        if (root == null || !root.exists()) {
            return null;
        }
        if (root.isFile()) {
            return root.getName().equalsIgnoreCase(fileName) ? root : null;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            File result = searchFile(child, fileName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static int[] parseParts(String version) {
        if (StrUtil.isBlank(version)) {
            return new int[0];
        }
        String[] split = version.split("\\.");
        int[] parts = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                parts[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    private static int compare(int[] left, int[] right) {
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static String majorOf(String version) {
        int[] parts = parseParts(version);
        return parts.length > 0 ? String.valueOf(parts[0]) : null;
    }
}
