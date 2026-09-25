package com.app.util.upload_video;

import cn.hutool.core.io.FileUtil;
import com.app.util.ChromeDriverUtil;
import com.app.util.PageDumpUtil;
import com.app.util.WebWaitUtil;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 视频上传工具类
 * 包括翻译视频上传字幕下载
 *
 * @Author guofan
 * @Create 2022/9/3
 */
public class UploadVideoUtil {
    public Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 有道的用户数据目录（保存登录态）
     */
    private static final String YOUDAO_USER_DATA_DIR = "C:\\User Data1";

    /**
     * 抖音 / 视频号的用户数据目录（保存登录态）
     */
    private static final String CREATOR_USER_DATA_DIR = "C:\\tool\\chromedriver\\UserData";

    /**
     * 抖音页面状态：已登录（页面上已出现文件上传控件）
     */
    private static final int DOUYIN_STATE_LOGGED_IN = 1;

    /**
     * 抖音页面状态：需要登录（页面上出现登录表单）
     */
    private static final int DOUYIN_STATE_NEED_LOGIN = 0;

    /**
     * 抖音页面状态：未知
     */
    private static final int DOUYIN_STATE_UNKNOWN = -1;

    /**
     * 抖音「作品描述」的字数上限（页面上显示 0/30），超了会被截断或校验失败
     */
    private static final int TITLE_MAX_LENGTH = 30;

    // 标题
    private String[] strings;
    private String[] fileNames;
    private final boolean isNotHead = false;

    // ==========================================================================
    // 选择器集中维护区
    //
    // 站点（尤其是抖音）会把 class 名 hash 化成 cover-Jg3T4p / primary-cECiOJ
    // 这类随机后缀，或者干脆换结构。这里统一采用「一个逻辑元素 = 一组候选选择器」
    // 的写法：优先用文案、语义属性（如 contenteditable、type=file）等稳定特征，
    // 哈希类名只作最后的兜底。改版时只需要在这里补一条候选，不用再满项目找 xpath。
    // ==========================================================================

    /**
     * 抖音：上传页出现「登录表单」的特征。
     *
     * <p>未登录时访问 content/upload 会直接渲染登录表单（手机号 + 验证码），
     * 此时页面上根本没有 input[type=file]。用登录表单来判断登录态，
     * 比判断「登录 / 发布作品 / 作品管理」这些文案可靠得多 ——
     * 实测未登录时「发布作品 / 作品管理」这些文案在页面上同样存在。</p>
     */
    private static By[] douyinLoginForm() {
        return new By[]{
                By.xpath("//input[contains(@placeholder,'手机号')]"),
                By.xpath("//input[contains(@placeholder,'验证码')]"),
                By.cssSelector("input[placeholder*='手机号']"),
                By.cssSelector("input[placeholder*='验证码']"),
                By.xpath("//*[contains(text(),'扫码登录')]"),
                By.xpath("//*[contains(text(),'验证码登录')]"),
                By.className("login")
        };
    }

    /**
     * 抖音：真正的视频上传控件（只认真实的文件输入框）
     */
    private static By[] douyinFileInput() {
        return new By[]{
                By.cssSelector("input[type='file'][accept*='video']"),
                By.cssSelector("input[type='file']")
        };
    }

    /**
     * 抖音：作品标题输入框（页面上的「作品描述」，最多 30 个字）。
     *
     * <p>用 placeholder 定位最稳。注意**绝对不能用 {@code className("notranslate")}**：
     * 「添加作品简介」那个富文本编辑器也带 notranslate，会串到简介框里去。</p>
     */
    private static By[] douyinTitleInput() {
        return new By[]{
                By.xpath("//*[contains(@data-placeholder,'作品标题')]"),
                By.xpath("//*[contains(@placeholder,'作品标题')]"),
                By.xpath("//*[contains(@data-placeholder,'填写作品标题')]"),
                By.xpath("//textarea[contains(@placeholder,'标题')]"),
                By.xpath("//input[contains(@placeholder,'标题')]"),
                By.cssSelector("textarea")
        };
    }

    /**
     * 抖音：作品简介输入框（富文本编辑器，data-placeholder="添加作品简介"）
     */
    private static By[] douyinDescriptionInput() {
        return new By[]{
                By.xpath("//*[contains(@data-placeholder,'作品简介')]"),
                By.xpath("//*[contains(@placeholder,'作品简介')]"),
                By.xpath("//*[contains(@data-placeholder,'添加作品简介')]")
        };
    }

    /**
     * 抖音：**上传完成的正面信号** —— 「重新上传」出现（或等价文案）。
     *
     * <p>只有它出现了才说明视频真的传完了，这时点发布才有意义。
     * 而「上传中/处理中」这种负面判断不可靠：页面上可能压根不显示这类文案，
     * 结果视频还在传就去点发布，页面只会提示「等待视频上传中」，什么都不会发生。</p>
     */
    private static By[] douyinUploadFinishedMark() {
        return new By[]{
                By.xpath("//*[contains(text(),'重新上传')]"),
                By.xpath("//*[contains(text(),'上传成功')]"),
                By.xpath("//*[contains(text(),'上传完成')]"),
                By.xpath("//*[contains(text(),'上传已完成')]")
        };
    }

    /**
     * 抖音：仍在上传 / 处理中的标志（负面信号）。
     *
     * <p>其中「取消上传」是上传中的强特征 —— 上传时视频卡片上会有一个取消上传按钮，
     * 传完后它就消失了（实测截图确认）。</p>
     */
    private static By[] douyinUploadingMark() {
        return new By[]{
                By.xpath("//*[contains(text(),'取消上传')]"),
                By.xpath("//*[contains(text(),'上传中')]"),
                By.xpath("//*[contains(text(),'处理中')]"),
                By.xpath("//*[contains(text(),'转码中')]"),
                By.xpath("//*[contains(text(),'视频上传中')]"),
                By.xpath("//*[contains(text(),'等待视频上传')]"),
                By.xpath("//*[contains(text(),'正在上传')]")
        };
    }


    /**
     * 抖音：选择封面
     */
    private static By[] douyinCoverButton() {
        return new By[]{
                By.xpath("//*[contains(text(),'选择封面')]"),
                By.xpath("//*[contains(@class,'cover-')]")
        };
    }

    /**
     * 抖音：封面弹窗确定按钮
     */
    private static By[] douyinCoverConfirm() {
        return new By[]{
                By.xpath("//button[contains(.,'确定')]"),
                By.xpath("//*[contains(@class,'secondary-')]")
        };
    }

    /**
     * 抖音：发布按钮。
     *
     * <p>抖音用 CSS Modules，类名是「稳定前缀 + 变化的 hash 后缀」，
     * 例如发布按钮的真实 HTML：
     * {@code <button class="button-dhlUZE primary-cECiOJ fixed-J9O8Yw">发布</button>}
     * 其中 {@code primary-} 前缀是稳定的（只有 hash 后缀会变），所以它是个可靠锚点。</p>
     *
     * <p>按"精确 → 宽松 → 只靠类名"排序：精确匹配优先，避免点到侧边栏的
     * 「发布作品 / 发布视频」等导航项，那会让页面跳走而不是发布。</p>
     */
    private static By[] douyinPublishButton() {
        return new By[]{
                // 主按钮 + 精确文案（最精确）
                By.xpath("//button[contains(@class,'primary-') and normalize-space(.)='发布']"),
                // 只靠精确文案（类名改了也能命中）
                By.xpath("//button[normalize-space(.)='发布']"),
                By.xpath("//div[@role='button'][normalize-space(.)='发布']"),
                By.xpath("//*[contains(text(),'发布')]/ancestor-or-self::button[not(contains(.,'作品'))]"),
                By.xpath("//button[contains(.,'发布') and not(contains(.,'作品'))]"),
                // 兜底：只要有稳定前缀 primary- 的主按钮
                By.xpath("//*[contains(@class,'primary-')]")
        };
    }

    /**
     * 有道：提交按钮
     */
    private static By[] youdaoSubmitButton() {
        return new By[]{
                By.className("btn-submit"),
                By.xpath("//button[contains(.,'提交')]")
        };
    }

    /**
     * 上传翻译视频等待翻译结束下载视频
     *
     * @param file     要翻译的视频文件
     * @param isUpload 是否上传
     * @return 字幕文件
     */
    public File uploadTranslateVideo(File file, boolean isUpload) throws InterruptedException, IOException {
        File newCCFile = null;
        // 自动判断 Chrome 版本并自动下载匹配的 ChromeDriver
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(YOUDAO_USER_DATA_DIR, false);
        WebDriver driver = createDriver(chromeOptions, YOUDAO_USER_DATA_DIR);
        driver.get("https://jianwai.youdao.com");
        driver.switchTo().frame(0);
        boolean login = false;
        try {
            driver.findElement(By.className("j-inputtext"));
        } catch (Exception e) {
            if (e instanceof NoSuchElementException) {
                logger.info("没有找到登录按钮，应该是登录了");
                login = true;
            } else {
                throw e;
            }
        }
        Thread.sleep(2000);
        if (!login) {
            List<WebElement> elements = driver.findElements(By.className("j-inputtext"));
            WebElement element = elements.get(0);
            element.clear();
            element.sendKeys("664130988@qq.com");
            Thread.sleep(2000);
            WebElement element2 = elements.get(1);
            element2.clear();
            element2.sendKeys("guofan123456@");
            WebElement element3 = driver.findElement(By.id("dologin"));
            element3.click();
        }

        Thread.sleep(5000);
        driver.get("https://jianwai.youdao.com");
        Wait<WebDriver> waitLogin = new FluentWait<>(driver)
                .withTimeout(Duration.ofMinutes(30))
                .pollingEvery(Duration.ofSeconds(20))
                .ignoring(NoSuchElementException.class);
        waitLogin.until(webDriver -> {
            // 新建项目按钮 //*[@id="app"]/div[1]/div/div[1]
            WebElement cardElement = driver.findElement(By.xpath("//*[@id=\"app\"]/div[1]/div/div[1]"));
            if (cardElement.isDisplayed()) {
                return cardElement;
            }
            throw new NoSuchElementException("按钮不能点击");
        });
        if (isUpload) {
            driver.get("https://jianwai.youdao.com/videoTrans");
            Thread.sleep(2000);
            WebElement fileInput = WebWaitUtil.findFirst(driver, Duration.ofSeconds(30), By.name("file"));
            fileInput.sendKeys(file.getAbsolutePath());
            WebElement submit = WebWaitUtil.findFirstVisible(driver, Duration.ofSeconds(30), youdaoSubmitButton());
            submit.click();
            // 定义一个等待类等待
            Wait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofMinutes(30))
                    .pollingEvery(Duration.ofSeconds(20))
                    .ignoring(NoSuchElementException.class);

            //设一等待 按钮出现并且能被点击
            WebElement webElement = wait.until(webDriver -> {
                List<WebElement> cardElements = driver.findElements(By.className("card"));
                for (WebElement webElement1 : cardElements) {
                    strings = webElement1.getText().split("[\\r|\\n]");
                    fileNames = file.getName().split("\\.");
                    String titleName = strings[0].replace(" ", "");
                    String fileName = fileNames[0].replace(" ", "");
                    if (webElement1.isEnabled() && fileName.contains(titleName) && strings.length >= 3 && !strings[2].contains("处理中")) {
                        return webElement1;
                    } else if (fileName.contains(titleName)) {
                        logger.info("等待翻译 刷新界面");
                        return cardElements.get(0);
                    }
                }
                logger.info("等待上传");
                throw new NoSuchElementException("按钮不能点击");
            });
            //关闭浏览器退出
            driver.quit();
        } else {
            // 定义一个等待类等待
            Wait<WebDriver> wait = new FluentWait<>(driver)
                    .withTimeout(Duration.ofMinutes(30))
                    .pollingEvery(Duration.ofSeconds(20))
                    .ignoring(NoSuchElementException.class);

            //设一等待 按钮出现并且能被点击
            WebElement webElement = wait.until(webDriver -> {
                List<WebElement> cardElements = driver.findElements(By.className("card"));
                for (WebElement webElement1 : cardElements) {
                    strings = webElement1.getText().split("[\\r|\\n]");
                    fileNames = file.getName().split("\\.");
                    String titleName = strings[0].replace(" ", "");
                    String fileName = fileNames[0].replace(" ", "");
                    if (webElement1.isEnabled() && fileName.contains(titleName) && strings.length >= 3 && !strings[2].contains("处理中")) {
                        return webElement1;
                    } else if (fileName.contains(titleName)) {
                        logger.info("等待翻译 刷新界面");
                        driver.navigate().refresh();
                        throw new NoSuchElementException("按钮不能点击");
                    }
                }
                logger.info("等待上传");
                throw new NoSuchElementException("按钮不能点击");
            });
            webElement.click();
            Thread.sleep(2000);
            // 获取字幕标题
            WebElement titleElement = driver.findElement(By.className("title"));
            String title = "CHSEN_" + titleElement.getText();
            // 下载字幕
            driver.findElements(By.className("btn")).get(3).click();
            // 确定
            driver.findElement(By.className("confirm")).click();
            Thread.sleep(3000);
            // 创建下载字幕文件
            File fileParent = new File("C:\\Users\\fan\\Downloads");
            File ccFile = new File(fileParent, title + ".srt");
            if (!ccFile.exists()) {
                logger.error("{}文件不存在", ccFile.getAbsolutePath());
                throw new IOException("文件不存在");
            }
            newCCFile = new File(file.getParent(), fileNames[0] + ".srt");
            FileUtil.copy(ccFile, newCCFile, true);
        }
        driver.quit();
        return newCCFile;
    }

    /**
     * 上传抖音视频
     */
    public void uploadDouYinVideo(String title, File videoFile, File imgFile) throws InterruptedException {
        // 自动判断 Chrome 版本并自动下载匹配的 ChromeDriver
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(CREATOR_USER_DATA_DIR, true);
        WebDriver driver = createDriver(chromeOptions, CREATOR_USER_DATA_DIR);
        try {
            // 开始记录本次上传的页面快照（存到 ./logs/page-dump/ 下），方便事后排查
            PageDumpUtil.startRun("抖音上传_" + (videoFile == null ? "unknown" : videoFile.getName()));

            // 进入上传页并确保已登录（未登录时会在浏览器里等待人工登录）
            ensureDouyinUploadPageReady(driver);
            logger.info("已进入抖音上传视频界面");
            PageDumpUtil.snapshot(driver, "已进入上传页");

            // 上传视频：只认真实的 input[type=file]，避免把文件路径打到手机号框里
            WebElement element = findDouyinElement(driver, Duration.ofSeconds(120),
                    "视频上传控件", douyinFileInput());
            element.sendKeys(videoFile.getAbsolutePath());
            logger.info("开始上传视频");
            PageDumpUtil.snapshot(driver, "已选择视频文件");

            // 【关键】先等视频真的上传完（以「重新上传」出现为准）再动其它东西。
            // 之前是"看不到上传中文案就认为传完了"，结果视频还在传就去点发布，
            // 页面只提示「等待视频上传中」，什么都不会发生。
            waitDouyinUploadFinished(driver, Duration.ofMinutes(30));
            PageDumpUtil.snapshot(driver, "上传完成");

            // 上传完成后再填标题和简介（避免上传过程中页面重渲染把内容冲掉）
            fillDouyinTitleAndDescription(driver, title);
            PageDumpUtil.snapshot(driver, "已填写标题和简介");

            // 设置封面：尽力而为，失败也不影响发布
            setDouyinCoverBestEffort(driver);
            PageDumpUtil.snapshot(driver, "封面处理完成");

            // 点击发布，并且必须确认真的发布成功才返回
            publishDouyinVideoAndConfirm(driver);
            PageDumpUtil.snapshot(driver, "发布成功");
        } catch (Exception e) {
            PageDumpUtil.dumpOnFailure(driver, "上传抖音失败", e);
            rethrow(e);
        } finally {
            PageDumpUtil.endRun();
            driver.quit();
        }
    }

    /**
     * 原样抛出异常，保持 uploadDouYinVideo 的方法签名不变
     */
    private void rethrow(Exception e) throws InterruptedException {
        if (e instanceof InterruptedException) {
            throw (InterruptedException) e;
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw new IllegalStateException(e.getMessage(), e);
    }


    /**
     * 等待视频上传完成。
     *
     * <p>判定依据是**正面信号**：页面上出现「重新上传 / 上传成功」这类文案，
     * 说明视频已经传完、可以去点发布了。等待期间会打印「上传中」提示。</p>
     *
     * <p>超时不会抛异常：交给后面的发布重试去兜底（那边会一直等到能发布为止）。</p>
     */
    private void waitDouyinUploadFinished(WebDriver driver, Duration timeout) throws InterruptedException {
        logger.info("等待视频上传完成（「重新上传」可见 且 没有「取消上传」）……");
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        long nextLogAt = 0;
        while (System.currentTimeMillis() < deadline) {
            // 必须"正面信号出现 + 负面信号消失"同时成立。
            // 只看「重新上传」不够：它可能一开始就存在于 DOM 里（之前就是这样提前放行的，
            // 结果视频才传到 71% 就去填标题，输入框是写不进去的）。
            boolean finished = WebWaitUtil.existsVisible(driver, douyinUploadFinishedMark());
            boolean uploading = WebWaitUtil.existsVisible(driver, douyinUploadingMark());
            if (finished && !uploading) {
                logger.info("视频上传完成");
                return;
            }
            if (System.currentTimeMillis() >= nextLogAt) {
                logger.info("视频仍在上传/处理中（完成标志可见={}，上传中标志可见={}），继续等待……", finished, uploading);
                nextLogAt = System.currentTimeMillis() + 30_000;
            }
            Thread.sleep(2000);
        }
        logger.warn("等待上传完成超时（{} 分钟），继续下一步（后续写入会带重试）", timeout.toMinutes());
    }

    /**
     * 填写标题和简介。
     *
     * <p>规则（用户要求）：<b>第一个 # 号及其后面的所有文字放到简介里</b>，
     * 前面的部分作为标题；标题最多 30 个字（抖音现在限制 30 字，超了会被截断/校验失败）。</p>
     */
    private void fillDouyinTitleAndDescription(WebDriver driver, String fullTitle) throws InterruptedException {
        String[] parts = splitDouyinTitle(fullTitle);
        String titlePart = parts[0];
        String descPart = parts[1];

        if (!titlePart.isEmpty()) {
            typeIntoEditorWithRetry(driver, "标题输入框", douyinTitleInput(), titlePart, Duration.ofMinutes(5));
            logger.info("已填写标题（{} 个字）：{}", titlePart.length(), titlePart);
        }

        if (!descPart.isEmpty()) {
            try {
                typeIntoEditorWithRetry(driver, "作品简介输入框", douyinDescriptionInput(), descPart, Duration.ofMinutes(2));
                logger.info("已把 # 之后的内容填入简介：{}", descPart);
            } catch (Exception e) {
                logger.warn("填写简介失败（不影响发布）：{}", e.getMessage());
            }
        }
    }

    /**
     * 反复尝试把内容写进编辑器。
     *
     * <p>视频还没上传完时，标题/简介输入框虽然"可见"但是**写不进去**
     * （实测：上传到 71% 时输入无效、字段仍是空的），所以这里不能写一次就放弃，
     * 要一边重试一边等上传结束。</p>
     *
     * @throws IllegalStateException 超时仍写不进去
     */
    private void typeIntoEditorWithRetry(WebDriver driver, String name, By[] locators,
                                         String text, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                WebElement element = WebWaitUtil.findFirstVisible(driver, Duration.ofSeconds(30), locators);
                if (typeIntoEditor(driver, element, text)) {
                    return;
                }
                lastError = "尝试写入后读回来是空的";
            } catch (Exception e) {
                lastError = e.getMessage();
            }
            // 还可能在上传中，等一会儿再试
            logger.info("{} 暂时写不进去（{}），5 秒后重试", name, lastError);
            Thread.sleep(5000);
        }
        throw new IllegalStateException(name + " 写入失败（重试 " + timeout.toMinutes() + " 分钟）：" + lastError
                + "；页面诊断：" + WebWaitUtil.describePage(driver));
    }


    /**
     * 拆分抖音的标题和简介。
     *
     * <p>规则：<b>第一个 # 号及其后面的所有文字都放到简介里</b>，前面的部分作为标题；
     * 标题最多 {@value #TITLE_MAX_LENGTH} 个字（抖音「作品描述」现在的限制）。</p>
     *
     * @param fullTitle 完整标题（可能带一堆 #话题）
     * @return {@code [0]=标题, [1]=简介}
     */
    static String[] splitDouyinTitle(String fullTitle) {
        String text = fullTitle == null ? "" : fullTitle.trim();
        int hashIndex = text.indexOf('#');
        String titlePart = (hashIndex >= 0 ? text.substring(0, hashIndex) : text).trim();
        String descPart = hashIndex >= 0 ? text.substring(hashIndex).trim() : "";

        // 整段以 # 开头时兜底：取前 30 个字当标题，简介留空
        if (titlePart.isEmpty() && !text.isEmpty()) {
            titlePart = text.length() > TITLE_MAX_LENGTH ? text.substring(0, TITLE_MAX_LENGTH) : text;
            descPart = "";
        }
        if (titlePart.length() > TITLE_MAX_LENGTH) {
            titlePart = titlePart.substring(0, TITLE_MAX_LENGTH);
        }
        return new String[]{titlePart, descPart};
    }

    /**
     * 往富文本编辑器里输内容。
     *
     * <p>抖音的标题/简介是 Slate 类富文本编辑器（{@code data-slate-editor="true"}），
     * 直接改 innerHTML 不会被它的内部模型接受，所以：
     * 先点聚焦 → 清空 → <b>优先用剪贴板粘贴</b>（编辑器对粘贴事件处理最好）→
     * 不行再退回 sendKeys → 再不行用 execCommand('insertText')。每一步都会读回来校验。</p>
     */
    private boolean typeIntoEditor(WebDriver driver, WebElement element, String text) throws InterruptedException {
        if (text == null || text.isEmpty()) {
            return true;
        }
        clickElement(driver, element);
        // 清空原有内容
        try {
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            element.sendKeys(Keys.DELETE);
        } catch (Exception ignored) {
            // 清空失败不影响后续输入
        }

        if (pasteText(driver, element, text)) {
            return true;
        }
        logger.warn("剪贴板粘贴未生效，改用 sendKeys");
        try {
            element.sendKeys(text);
            if (verifyTyped(driver, element, text)) {
                return true;
            }
        } catch (Exception e) {
            logger.warn("sendKeys 输入失败：{}", e.getMessage());
        }

        logger.warn("sendKeys 也未生效，改用 execCommand 写入");
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "var el = arguments[0]; el.focus();"
                            + "document.execCommand('selectAll', false, null);"
                            + "document.execCommand('insertText', false, arguments[1]);",
                    element, text);
            Thread.sleep(500);
        } catch (Exception e) {
            logger.warn("execCommand 写入失败：{}", e.getMessage());
        }
        return verifyTyped(driver, element, text);
    }

    /**
     * 用系统剪贴板粘贴（富文本编辑器最稳的输入方式）
     */
    private boolean pasteText(WebDriver driver, WebElement element, String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            element.sendKeys(Keys.chord(Keys.CONTROL, "v"));
            Thread.sleep(600);
            return verifyTyped(driver, element, text);
        } catch (Exception e) {
            logger.debug("剪贴板粘贴失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 校验内容是否真的写进去了。
     *
     * <p><b>注意</b>：标题其实是个 {@code <input type="text">}，对这类元素
     * {@code WebElement.getText()} 永远返回空串，必须读 {@code value} 属性，
     * 否则会把"输入成功"误判成"失败"（这个坑真的踩过）。</p>
     */
    boolean verifyTyped(WebDriver driver, WebElement element, String expected) {
        try {
            String actual = String.valueOf(((JavascriptExecutor) driver).executeScript(
                    "var el = arguments[0];"
                            + "if (el.value !== undefined && el.value !== null) return el.value;"
                            + "return el.innerText || el.textContent || '';",
                    element));
            actual = actual.replace("\n", "").replace("\r", "").trim();
            String sample = expected.substring(0, Math.min(5, expected.length()));
            boolean ok = actual.contains(sample);
            if (!ok) {
                logger.debug("内容校验未通过：期望前缀[{}] 实际[{}]", sample,
                        actual.length() > 60 ? actual.substring(0, 60) : actual);
            }
            return ok;
        } catch (Exception e) {
            logger.debug("内容校验异常：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 校验内容是否真的写进去了（取前 5 个字比对即可，编辑器可能会插入换行）
     */
    private boolean verifyTyped(WebElement element, String expected) {
        try {
            String actual = element.getText();
            if (actual == null) {
                actual = element.getAttribute("value");
            }
            if (actual == null) {
                return false;
            }
            actual = actual.replace("\n", "").replace("\r", "").trim();
            String sample = expected.substring(0, Math.min(5, expected.length()));
            return actual.contains(sample);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 设置封面（尽力而为）。
     *
     * <p>抖音改版后「选择封面」不一定还在，一旦这里抛异常，
     * 原来会导致整个流程在**没点发布**的情况下直接退出，
     * 表现为「视频都传完了但没发布成功」。所以这里改成失败即跳过。</p>
     */
    private void setDouyinCoverBestEffort(WebDriver driver) {
        try {
            WebElement selectCover = WebWaitUtil.findFirstVisible(driver, Duration.ofSeconds(15), douyinCoverButton());
            logger.info("点击选择封面");
            selectCover.click();
            Thread.sleep(3000);

            WebElement okCover = WebWaitUtil.findFirstVisible(driver, Duration.ofSeconds(30), douyinCoverConfirm());
            okCover.click();
            Thread.sleep(2000);
            logger.info("已确认封面");
        } catch (Exception e) {
            logger.warn("设置封面被跳过（不影响发布）：{}", e.getMessage());
            // 关掉可能已经弹出来的封面弹窗，避免遮挡发布按钮
            dismissDouyinDialogIfPresent(driver);
        }
    }

    /**
     * 点击发布，并且必须确认发布成功才返回。
     *
     * <p>原来的实现点完只 sleep 3 秒就 driver.quit()，有两个问题：
     * 1. 上传/转码没完成时按钮是灰的，点了没反应；
     * 2. 点完立刻关浏览器会把发布请求掐断。
     * 现在改为：还在上传就先等 → 点一次 → 等确认（URL 跳转 / 发布成功提示）→ 没确认就重试。</p>
     *
     * @throws IllegalStateException 超时仍未确认发布成功
     */
    private void publishDouyinVideoAndConfirm(WebDriver driver) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(30).toMillis();
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            // 上传还没结束就别点：点了只会弹「等待视频上传中」，什么都不会发生。
            // 这里只看"上传中"这个负面信号（不再要求"完成标志也可见"，
            // 否则完成标志提前出现时会误放行）
            if (WebWaitUtil.existsVisible(driver, douyinUploadingMark())) {
                logger.info("视频仍在上传/处理中，暂不点击发布……");
                Thread.sleep(5000);
                continue;
            }
            // 万一还是点了，页面会提示「等待视频上传中」，这时继续等上传完成
            if (WebWaitUtil.existsVisible(driver, douyinWaitingUploadMark())) {
                logger.info("页面提示「等待视频上传中」，等上传完成后再点发布");
                Thread.sleep(5000);
                continue;
            }

            attempt++;
            WebElement sendButton;
            try {
                sendButton = WebWaitUtil.findFirstVisible(driver, Duration.ofSeconds(30), douyinPublishButton());
            } catch (Exception e) {
                // 按钮可能还在渲染，继续等，不要直接失败
                logger.warn("暂未找到可点击的发布按钮，10 秒后重试：{}", e.getMessage());
                Thread.sleep(10000);
                continue;
            }
            logger.info("第 {} 次点击发布按钮…… 按钮文案=[{}] class=[{}]",
                    attempt, safeText(sendButton), sendButton.getAttribute("class"));
            clickElement(driver, sendButton);

            // 每次点击后最多确认 90 秒
            if (waitDouyinPublishSuccess(driver, Duration.ofSeconds(90))) {
                logger.info("🎉 抖音发布成功（第 {} 次点击后确认）", attempt);
                return;
            }
            logger.warn("第 {} 次点击后未确认到发布成功，处理弹窗后重试", attempt);
            // 每次未确认都存一份页面，方便排查"点了没反应"到底是为什么
            PageDumpUtil.snapshot(driver, "发布未确认_第" + attempt + "次");
            // 可能弹了「确认发布 / 绑定手机号」之类的弹窗
            if (!dismissDouyinDialogIfPresent(driver)) {
                Thread.sleep(5000);
            }
        }
        throw new IllegalStateException("抖音发布未确认成功（30 分钟内未出现页面跳转或发布成功提示）。页面诊断："
                + WebWaitUtil.describePage(driver));
    }

    /**
     * 判断抖音是否发布成功。
     *
     * <p><b>只认两个信号</b>：页面跳到<b>作品管理页</b>、或出现「发布成功」文案。</p>
     *
     * <p>两个必须避开的坑：</p>
     * <ol>
     *   <li>绝不能用「上传控件消失」当成功信号！抖音在上传过程中就会把
     *       {@code input[type=file]} 移除，用它判断会误判成功。</li>
     *   <li>也不能用「URL 不含 /content/upload」判断！抖音的<b>编辑页 URL 是
     *       {@code /content/post/video}</b>（实测），同样不含 upload，
     *       所以必须认准「跳到 /content/manage」。</li>
     * </ol>
     */
    private boolean waitDouyinPublishSuccess(WebDriver driver, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                String url = driver.getCurrentUrl();
                if (url != null && (url.contains("/content/manage") || url.contains("/content/post/manage"))) {
                    logger.info("已跳转到作品管理页，判定发布成功：{}", url);
                    return true;
                }
                if (WebWaitUtil.existsVisible(driver,
                        By.xpath("//*[contains(text(),'发布成功')]"),
                        By.xpath("//*[contains(text(),'发布完成')]"),
                        By.xpath("//*[contains(text(),'作品发布成功')]"))) {
                    logger.info("检测到发布成功提示");
                    return true;
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        logger.info("等待发布成功超时，当前 URL={}", safeCurrentUrl(driver));
        return false;
    }

    /**
     * 安全读取当前 URL（只用于打日志）
     */
    private String safeCurrentUrl(WebDriver driver) {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 抖音：「等待视频上传中」这类提示（说明发布点了太早，得等视频传完）
     */
    private static By[] douyinWaitingUploadMark() {
        return new By[]{
                By.xpath("//*[contains(text(),'等待视频上传')]"),
                By.xpath("//*[contains(text(),'视频上传中')]"),
                By.xpath("//*[contains(text(),'请等待视频上传')]")
        };
    }


    /**
     * 关闭抖音可能弹出的确认/绑定类弹窗。
     *
     * <p>只点白名单里的文案，刻意**不点「取消」**，避免把发布流程取消掉。</p>
     *
     * @return 是否点了某个按钮
     */
    private boolean dismissDouyinDialogIfPresent(WebDriver driver) {
        String[] whitelist = {"我知道了", "暂不绑定", "以后再说", "跳过", "确认", "确定"};
        for (String text : whitelist) {
            try {
                List<WebElement> buttons = driver.findElements(By.xpath(
                        "//button[contains(.,'" + text + "')] | //div[@role='button'][contains(.,'" + text + "')]"));
                for (WebElement button : buttons) {
                    if (button.isDisplayed() && button.isEnabled()) {
                        logger.info("处理弹窗：点击「{}」", text);
                        clickElement(driver, button);
                        Thread.sleep(2000);
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.debug("处理弹窗失败：{}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * 安全获取元素文案，元素失效时返回空串（仅用于打日志）
     */
    private String safeText(WebElement element) {
        try {
            return element.getText();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 点击元素，普通点击失败时用 JS 兜底（被遮挡/未滚入视口时很常见）
     */
    private void clickElement(WebDriver driver, WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        } catch (Exception ignored) {
            // 滚动失败不影响后续点击
        }
        try {
            element.click();
        } catch (Exception e) {
            logger.debug("普通点击失败，改用 JS 点击：{}", e.getMessage());
            js.executeScript("arguments[0].click();", element);
        }
    }


    /**
     * 打开抖音并等待人工登录（登录态会写入固定用户目录，之后无需重复登录）。
     * 供界面上的「登录抖音」按钮调用。
     */
    public void loginDouYin() {
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(CREATOR_USER_DATA_DIR, true);
        WebDriver driver = createDriver(chromeOptions, CREATOR_USER_DATA_DIR);
        try {
            ensureDouyinUploadPageReady(driver);
            logger.info("抖音已登录，登录态已保存在：{}", CREATOR_USER_DATA_DIR);
        } finally {
            driver.quit();
        }
    }

    /**
     * 确保抖音上传页可用且已登录。
     *
     * <p>关键点：未登录时访问 content/upload 会直接渲染登录表单，
     * 页面上没有 input[type=file]；这里以「文件上传控件是否出现」为准来判断，
     * 而不是靠「登录 / 发布作品」这类文案（实测未登录时这些文案同样存在）。</p>
     *
     * @throws IllegalStateException 长时间未完成登录或页面结构异常
     */
    private void ensureDouyinUploadPageReady(WebDriver driver) {
        final String uploadUrl = "https://creator.douyin.com/creator-micro/content/upload";
        driver.get(uploadUrl);

        if (waitDouyinUploadPageState(driver, Duration.ofSeconds(45)) == DOUYIN_STATE_LOGGED_IN) {
            return;
        }

        // 未登录：停留在当前页面等待人工登录（不要刷新，否则会把扫码/验证码弹窗刷掉）
        logger.info("检测到抖音未登录，请在打开的浏览器里完成登录（扫码或手机号验证码），登录成功后程序会自动继续");
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(20).toMillis();
        boolean loginFormGone = false;
        while (System.currentTimeMillis() < deadline) {
            if (WebWaitUtil.exists(driver, douyinFileInput())) {
                loginFormGone = true;
                break;
            }
            if (!WebWaitUtil.existsVisible(driver, douyinLoginForm())) {
                loginFormGone = true;
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待抖音登录被中断", e);
            }
        }
        if (!loginFormGone) {
            throw new IllegalStateException("等待抖音登录超时（20 分钟）。页面诊断："
                    + WebWaitUtil.describePage(driver));
        }
        logger.info("抖音登录完成，登录态已保存");

        // 登录后回到上传页确认真的可用
        driver.get(uploadUrl);
        if (waitDouyinUploadPageState(driver, Duration.ofSeconds(60)) == DOUYIN_STATE_LOGGED_IN) {
            return;
        }
        throw new IllegalStateException("已登录但仍未进入抖音上传页（可能被弹窗遮挡或页面改版）。页面诊断："
                + WebWaitUtil.describePage(driver));
    }

    /**
     * 判断抖音当前页面状态
     *
     * @return {@link #DOUYIN_STATE_LOGGED_IN} / {@link #DOUYIN_STATE_NEED_LOGIN} / {@link #DOUYIN_STATE_UNKNOWN}
     */
    private int waitDouyinUploadPageState(WebDriver driver, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (WebWaitUtil.exists(driver, douyinFileInput())) {
                return DOUYIN_STATE_LOGGED_IN;
            }
            if (WebWaitUtil.existsVisible(driver, douyinLoginForm())) {
                return DOUYIN_STATE_NEED_LOGIN;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return DOUYIN_STATE_UNKNOWN;
            }
        }
        return DOUYIN_STATE_UNKNOWN;
    }

    /**
     * 查找抖音页面元素，找不到时把页面诊断信息一起抛出来，避免出现"看不懂的超时"
     */
    private WebElement findDouyinElement(WebDriver driver, Duration timeout, String name, By... locators) {
        try {
            return WebWaitUtil.findFirst(driver, timeout, locators);
        } catch (Exception e) {
            String diagnostics = WebWaitUtil.describePage(driver);
            logger.error("未找到{}（等待 {} 秒）。页面诊断：{}", name, timeout.getSeconds(), diagnostics);
            throw new IllegalStateException("未找到" + name + "，抖音可能未登录 / 有弹窗遮挡 / 页面已改版。页面诊断："
                    + diagnostics, e);
        }
    }


    /**
     * 上传微信视频号
     *
     * @param title       标题
     * @param mergeVideos 合并后的视频
     */
    public void uploadWeChatVideo(String title, File mergeVideos) throws InterruptedException {
        // 自动判断 Chrome 版本并自动下载匹配的 ChromeDriver
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(CREATOR_USER_DATA_DIR, true);

        WebDriver driver = createDriver(chromeOptions, CREATOR_USER_DATA_DIR);
        WebDriverWait globalWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            // 2. 登录流程 (保持不变)
            driver.get("https://channels.weixin.qq.com/platform");
            Thread.sleep(1000);

            boolean isLogin = false;
            try {
                driver.findElement(By.className("login-content"));
            } catch (NoSuchElementException e) {
                isLogin = true;
            }
            if (!isLogin) {
                // 扫码登录逻辑
                Wait<WebDriver> loginWait = new FluentWait<>(driver).withTimeout(Duration.ofMinutes(20)).pollingEvery(Duration.ofSeconds(1)).ignoring(NoSuchElementException.class);
                loginWait.until(webDriver -> webDriver.findElement(By.className("finder-nickname")));
            }

            // 3. 进入发表页
            driver.get("https://channels.weixin.qq.com/platform/post/create");
            logger.info("进入发表页面...");

            // ==========================================
            // 步骤 1: 锁定 Frame (标题成功的关键)
            // ==========================================
            try {
                driver.switchTo().defaultContent();
                // 既然之前 frame(0) 能输标题，说明就是它，不用改
                globalWait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(0));
                logger.info("✅ 已切入编辑器 Frame");
                Thread.sleep(2000);
            } catch (Exception e) {
                throw new RuntimeException("Frame 切换失败");
            }


            boolean isSuccess = false;
            int attempts = 0;
            // 2. 循环重试：在一个 JS 命令里同时完成“查找”和“赋值”
            // 只有这样才能避开 StaleElementReferenceException
            while (attempts < 20) { // 最多试 20 次 (约 20秒)
                try {
                    // 这段 JS 脚本做了三件事：
                    // 1. 现找元素
                    // 2. 如果找到，直接赋值并触发事件
                    // 3. 返回 true 或 false
                    String script =
                            "var target = document.querySelector('div[contenteditable=\"true\"]') || document.querySelector('.input-editor');" +
                                    "if (target) {" +
                                    " target.click();" + // 尝试聚焦
                                    " target.innerText = arguments[0];" + // 赋值
                                    " target.dispatchEvent(new Event('input', { bubbles: true }));" + // 通知 Vue/React 发生了改变
                                    " target.dispatchEvent(new Event('change', { bubbles: true }));" +
                                    " return true;" + // 告诉 Java 成功了
                                    "} else {" +
                                    " return false;" + // 没找到，告诉 Java 失败
                                    "}";
                    // 注意：这里只传 title 字符串，不传 WebElement 对象！
                    // 避开了 WebElement 过期的问题
                    Boolean result = (Boolean) js.executeScript(script, title);
                    if (result) {
                        isSuccess = true;
                        logger.info(">>> 标题输入成功！(第 " + (attempts + 1) + " 次尝试)");
                        break;
                    }
                } catch (Exception e) {
                    // 忽略 JS 执行中的任何错误，坚持重试
                    // logger.warn("JS执行微小异常，忽略并重试...");
                }
                Thread.sleep(1000); // 等待 1 秒
                attempts++;
                logger.info("编辑器未就绪或页面正在刷新，正在重试 (" + attempts + "/20)...");
            }

            if (!isSuccess) {
                logger.error("!!! 最终失败：20秒内无法通过 JS 注入标题 !!!");
                String src = driver.getPageSource();
                logger.error("当前 Iframe 源码前 1000 字符: " + (src.length() > 1000 ? src.substring(0, 1000) : src));
                throw new RuntimeException("标题输入失败，页面元素不稳定。");
            }
            // ==========================================
            // 步骤 3: 仿照标题逻辑的【JS 查找 + 显形 + 上传】
            // ==========================================
            logger.info("准备进行【JS 查找模式】视频上传...");

            boolean uploadSuccess = false;
            int uploadAttempts = 0;

            while (uploadAttempts < 20) {
                try {
                    // 这段 JS 的逻辑和你标题的逻辑几乎一模一样：
                    // 1. 在当前 DOM 里找 input[type=file]
                    // 2. 找到了就强制把它的 display 改为 block (解决不能操作的问题)
                    // 3. 把这个元素返回给 Java
                    String findFileScript =
                            "var target = document.querySelector('input[type=\"file\"]');" +
                                    "if (target) {" +
                                    "    target.style.display = 'block';" +      // 关键：强制显形
                                    "    target.style.visibility = 'visible';" + // 关键：强制可见
                                    "    target.style.width = '1px';" +          // 防止它是 0x0 大小
                                    "    target.style.height = '1px';" +
                                    "    target.style.opacity = '1';" +
                                    "    return target;" +                       // 找到了，返回给 Java
                                    "} else {" +
                                    "    return null;" +                         // 没找到
                                    "}";

                    // 执行 JS，尝试拿到 WebElement
                    // 这里不用 expectedConditions，直接问浏览器要元素，和你标题逻辑一样直接
                    WebElement fileInput = (WebElement) js.executeScript(findFileScript);

                    if (fileInput != null) {
                        logger.info(">>> JS 成功捕获并处理了上传控件！(第 " + (uploadAttempts + 1) + " 次尝试)");

                        // 此时元素已经被 JS 强制显形了，Selenium 可以直接操作
                        fileInput.sendKeys(mergeVideos.getAbsolutePath());

                        logger.info("✅ 视频路径发送成功！");
                        uploadSuccess = true;
                        break; // 成功退出循环
                    }

                } catch (Exception e) {
                    // 忽略中间的报错，坚持重试
                    logger.warn("上传控件查找微小异常，重试中...");
                }

                Thread.sleep(1000); // 找不到就睡 1 秒再找
                uploadAttempts++;
                logger.info("正在搜寻上传入口 (" + uploadAttempts + "/20)...");
            }

            if (!uploadSuccess) {
                throw new RuntimeException("上传失败：20秒内 JS 未能定位到 input[type='file']");
            }

            // ==========================================
            // 步骤 4: 等待【发表】按钮
            // ==========================================
            logger.info("等待上传进度及发表按钮...");

            // 视频上传需要时间，这里必须给足耐心
            WebDriverWait uploadWait = new WebDriverWait(driver, Duration.ofMinutes(10));

            // 定位“发表”按钮
            // 微信的按钮通常是 button 标签或者 div role='button'
            WebElement sendBtn = uploadWait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'发表')] | //div[contains(text(),'发表') and @role='button']"))
            );

            logger.info("发表按钮已激活，准备点击...");
            Thread.sleep(1000);
            js.executeScript("arguments[0].click();", sendBtn);

            // 等待成功跳转或提示
            Thread.sleep(5000);
            logger.info("🎉 任务完成！");

        } catch (Exception e) {
            logger.error("❌ 任务失败", e);
            throw e;
        }
        if (driver != null) driver.quit();

    }

    /**
     * 登录微信视频号
     * 驱动更新地址：https://googlechromelabs.github.io/chrome-for-testing/#stable
     */
    public void loginWeChat() {
        // 自动判断 Chrome 版本并自动下载匹配的 ChromeDriver
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(CREATOR_USER_DATA_DIR, true);
        WebDriver driver = createDriver(chromeOptions, CREATOR_USER_DATA_DIR);

        driver.get("https://channels.weixin.qq.com/platform");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        boolean isLogin = false;
        try {
            driver.findElement(By.className("login-content"));
        } catch (Exception e) {
            if (e instanceof NoSuchElementException) {
                logger.info("没有找到登录按钮，应该是登录了");
                isLogin = true;
            } else {
                throw e;
            }
        }
        if (!isLogin) {
            Wait<WebDriver> uploadVideoButtonWait = new FluentWait<>(driver).withTimeout(Duration.ofMinutes(20))
                    .pollingEvery(Duration.ofSeconds(1))
                    .ignoring(NoSuchElementException.class);
            uploadVideoButtonWait.until(webDriver -> webDriver.findElement(By.className("finder-nickname")));
            logger.info("登录成功！");
            driver.quit();
            return;
        }
        driver.quit();
    }

    /**
     * 创建浏览器，并在启动前处理"专用浏览器还开着"的情况。
     *
     * <p>Chrome 不允许两个实例共用同一个 {@code --user-data-dir}，
     * 第二个实例会直接退出，Selenium 只会报一句很难懂的
     * "session not created: Chrome instance exited"。
     * 这里会先检测并关掉残留的专用浏览器（优先正常关闭，保证登录 cookie 落盘），
     * 实在关不掉再抛出可读的提示。</p>
     *
     * @param chromeOptions 浏览器参数
     * @param userDataDir   该浏览器使用的用户数据目录（登录态就存在这里）
     */
    private WebDriver createDriver(ChromeOptions chromeOptions, String userDataDir) {
        if (ChromeDriverUtil.isProfileInUse(userDataDir)) {
            logger.warn("检测到专用浏览器还开着（用户数据目录：{}），先把它关闭再继续", userDataDir);
            ChromeDriverUtil.closeProfileChrome(userDataDir);
            if (ChromeDriverUtil.isProfileInUse(userDataDir)) {
                throw new IllegalStateException("浏览器启动失败：用户数据目录 " + userDataDir + " 仍被占用。\n"
                        + "请手动关闭所有 Chrome 窗口（或任务管理器结束 chrome.exe）后重试。\n"
                        + "若确认没有 Chrome 在运行，可删除该目录下的 SingletonLock 文件再试。");
            }
        }
        try {
            return new ChromeDriver(chromeOptions);
        } catch (SessionNotCreatedException e) {
            throw new IllegalStateException("浏览器启动失败（Chrome 实例已退出）。\n"
                    + "最常见原因：用户数据目录 " + userDataDir + " 已被另一个 Chrome 占用（上次的浏览器没关干净）。\n"
                    + "请关闭所有 Chrome 窗口后重试。", e);
        }
    }

    /**
     * 统一构建 ChromeOptions，并顺带做一些反自动化检测的处理，
     * 减少被站点判定为爬虫的概率（降低改 xpath 的频率）。
     *
     * @param userDataDir 用户数据目录，用于复用登录态
     * @param antiDetect  是否开启反自动化检测
     */
    private ChromeOptions buildOptions(String userDataDir, boolean antiDetect) {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--user-data-dir=" + userDataDir);
        if (isNotHead) {
            chromeOptions.addArguments("--headless");
        }
        if (antiDetect) {
            chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
            chromeOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
            chromeOptions.setExperimentalOption("useAutomationExtension", false);
        }
        return chromeOptions;
    }

}
