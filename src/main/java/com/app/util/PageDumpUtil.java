package com.app.util;

import cn.hutool.core.io.FileUtil;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 网页快照工具：把自动化过程中的页面存下来，方便事后排查故障。
 *
 * <p>每次上传会新建一个目录，里面按步骤保存 HTML + 截图，并追加一行关键状态：</p>
 * <pre>
 * ./logs/page-dump/20260926_004530_抖音上传_xxx.mp4/
 *     report.txt                       ← 时间 + 步骤 + URL/可见元素等关键状态（排查先看这个）
 *     01_已进入上传页.html  /  .png
 *     02_已选择视频文件.html /  .png
 *     03_上传完成.html     /  .png
 *     ERROR_第1次点击发布后未确认.html / .png
 *     ...
 * </pre>
 *
 * <p>设计原则：<b>这里出任何问题都不能影响上传本身</b>，
 * 所以所有方法内部都吞异常，只打日志。</p>
 *
 * @Author OverCode
 */
public final class PageDumpUtil {

    private static final Logger log = LoggerFactory.getLogger(PageDumpUtil.class);

    /**
     * 快照根目录
     */
    private static final File ROOT_DIR = new File("./logs/page-dump");

    /**
     * 只保留最近多少次运行的快照，避免越攒越多
     */
    private static final int MAX_KEEP_RUNS = 10;

    /**
     * 单次运行最多存多少个快照（发布重试会点很多次）
     */
    private static final int MAX_SNAPSHOTS_PER_RUN = 30;

    /**
     * 总开关
     */
    private static volatile boolean enabled = true;

    private static File currentRunDir;

    private static String currentRunName;

    private static int sequence = 0;

    private static boolean capped = false;

    private PageDumpUtil() {
    }

    /**
     * 关闭/开启页面快照
     */
    public static void setEnabled(boolean value) {
        enabled = value;
        log.info("页面快照已{}", value ? "开启" : "关闭");
    }

    /**
     * 取本次运行的快照目录（可能为 null，表示未开启或创建失败）
     */
    public static synchronized File getCurrentRunDir() {
        return currentRunDir;
    }

    /**
     * 开始一次运行，返回本次快照目录
     *
     * @param tag 标签，例如 "抖音上传_xxx.mp4"
     */
    public static synchronized File startRun(String tag) {
        if (!enabled) {
            return null;
        }
        try {
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            currentRunName = time + "_" + sanitize(tag);
            currentRunDir = new File(ROOT_DIR, currentRunName);
            FileUtil.mkdir(currentRunDir);
            sequence = 0;
            capped = false;
            note("=========== 开始记录：" + currentRunName + " ===========");
            log.info("页面快照目录：{}", currentRunDir.getAbsolutePath());
            cleanupOldRuns();
            return currentRunDir;
        } catch (Exception e) {
            log.warn("创建页面快照目录失败：{}", e.getMessage());
            currentRunDir = null;
            return null;
        }
    }

    /**
     * 结束本次运行
     */
    public static synchronized void endRun() {
        if (currentRunDir == null) {
            return;
        }
        note("=========== 记录结束：" + currentRunName + " ===========");
        currentRunDir = null;
        currentRunName = null;
        sequence = 0;
    }

    /**
     * 保存一次页面快照（HTML + 截图 + 一行关键状态）
     *
     * @param stepName 步骤名，例如 "03_上传完成"
     */
    public static synchronized void snapshot(WebDriver driver, String stepName) {
        snapshot(driver, stepName, null);
    }

    /**
     * 保存一次页面快照，并额外记一条备注
     */
    public static synchronized void snapshot(WebDriver driver, String stepName, String remark) {
        if (!enabled || currentRunDir == null || driver == null) {
            return;
        }
        if (sequence >= MAX_SNAPSHOTS_PER_RUN) {
            if (!capped) {
                capped = true;
                note("已达到单次快照上限（" + MAX_SNAPSHOTS_PER_RUN + " 个），后续步骤不再保存文件");
            }
            return;
        }
        try {
            sequence++;
            String name = String.format("%02d_%s", sequence, sanitize(stepName));
            writeHtml(driver, new File(currentRunDir, name + ".html"));
            writeScreenshot(driver, new File(currentRunDir, name + ".png"));
            note("步骤[" + stepName + "] " + describe(driver) + (remark == null ? "" : " | " + remark));
        } catch (Exception e) {
            log.debug("保存页面快照失败：{}", e.getMessage());
        }
    }

    /**
     * 失败时的完整落盘：页面 + 截图 + 异常栈
     */
    public static synchronized void dumpOnFailure(WebDriver driver, String stepName, Throwable error) {
        if (!enabled) {
            return;
        }
        if (currentRunDir == null) {
            // 还没 startRun 就失败了，补一个目录
            startRun("失败兜底");
        }
        try {
            String name = "ERROR_" + sanitize(stepName);
            if (driver != null) {
                writeHtml(driver, new File(currentRunDir, name + ".html"));
                writeScreenshot(driver, new File(currentRunDir, name + ".png"));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("\n==================== 异常 ====================\n");
            sb.append("步骤：").append(stepName).append('\n');
            if (driver != null) {
                sb.append("页面：").append(describe(driver)).append('\n');
            }
            if (error != null) {
                sb.append("异常类型：").append(error.getClass().getName()).append('\n');
                sb.append("异常信息：").append(error.getMessage()).append('\n');
                StringWriter writer = new StringWriter();
                error.printStackTrace(new PrintWriter(writer));
                sb.append(writer);
            }
            note(sb.toString());
            log.error("已把失败页面保存到：{}", new File(currentRunDir, name + ".html").getAbsolutePath());
        } catch (Exception e) {
            log.debug("保存失败页面时出错：{}", e.getMessage());
        }
    }

    /**
     * 追加一条记录到 report.txt
     */
    public static synchronized void note(String content) {
        if (currentRunDir == null || content == null) {
            return;
        }
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            FileUtil.appendUtf8String("[" + time + "] " + content + System.lineSeparator(),
                    new File(currentRunDir, "report.txt"));
        } catch (Exception e) {
            log.debug("写 report.txt 失败：{}", e.getMessage());
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 一行关键状态：URL + 标题 + 可见元素概况（和 WebWaitUtil.describePage 一致）
     */
    private static String describe(WebDriver driver) {
        try {
            return WebWaitUtil.describePage(driver);
        } catch (Exception e) {
            return "页面信息获取失败：" + e.getMessage();
        }
    }

    private static void writeHtml(WebDriver driver, File target) {
        try {
            String source = driver.getPageSource();
            if (source != null) {
                FileUtil.writeUtf8String(source, target);
            }
        } catch (Exception e) {
            log.debug("保存页面 HTML 失败：{}", e.getMessage());
        }
    }

    private static void writeScreenshot(WebDriver driver, File target) {
        try {
            if (!(driver instanceof TakesScreenshot)) {
                return;
            }
            File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtil.copy(source, target, true);
            // Selenium 生成的临时文件用完删掉
            FileUtil.del(source);
        } catch (Exception e) {
            log.debug("保存页面截图失败：{}", e.getMessage());
        }
    }

    /**
     * 只保留最近 MAX_KEEP_RUNS 次运行的目录
     */
    private static void cleanupOldRuns() {
        try {
            File[] dirs = ROOT_DIR.listFiles(File::isDirectory);
            if (dirs == null || dirs.length <= MAX_KEEP_RUNS) {
                return;
            }
            List<File> sorted = Arrays.asList(dirs);
            // 目录名以时间开头，按名字倒序即"新的在前"
            sorted.sort(Comparator.comparing(File::getName).reversed());
            for (int i = MAX_KEEP_RUNS; i < sorted.size(); i++) {
                FileUtil.del(sorted.get(i));
                log.info("清理旧的页面快照：{}", sorted.get(i).getName());
            }
        } catch (Exception e) {
            log.debug("清理旧快照失败：{}", e.getMessage());
        }
    }

    /**
     * 文件名里不能有的字符
     */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_");
        return safe.length() > 60 ? safe.substring(0, 60) : safe;
    }
}
