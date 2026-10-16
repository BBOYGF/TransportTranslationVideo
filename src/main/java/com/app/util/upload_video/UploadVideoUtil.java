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
     * 上传微信视频号。
     *
     * <p>视频号站点结构多变，而且"视频上传控件"经常藏在 iframe 里，所以这里做了几件事：</p>
     * <ol>
     *   <li>不再硬切 {@code frame(0)}：先在主文档里找编辑器，找不到再遍历 iframe 智能匹配；</li>
     *   <li>标题 / 上传控件 / 发表按钮都用多候选选择器兜底；</li>
     *   <li>全程接入 {@link PageDumpUtil}，失败自动留下 HTML / 截图 / report，方便事后定位；</li>
     *   <li>等视频真正上传、转码完成后再点「发表」，并确认发布成功。</li>
     * </ol>
     *
     * @param title       标题
     * @param mergeVideos 合并后的视频
     */
    public void uploadWeChatVideo(String title, File mergeVideos) throws InterruptedException {
        // 自动判断 Chrome 版本并自动下载匹配的 ChromeDriver
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(CREATOR_USER_DATA_DIR, true);
        WebDriver driver = createDriver(chromeOptions, CREATOR_USER_DATA_DIR);

        PageDumpUtil.startRun("视频号上传_" + (mergeVideos == null ? "无文件" : mergeVideos.getName()));
        try {
            // 1. 打开视频号助手，必要时等待人工扫码登录
            driver.get("https://channels.weixin.qq.com/platform");
            PageDumpUtil.snapshot(driver, "打开视频号助手");
            ensureWeChatLogin(driver);

            // 2. 进入发表页
            driver.get("https://channels.weixin.qq.com/platform/post/create");
            logger.info("进入视频号发表页面...");
            PageDumpUtil.snapshot(driver, "进入发表页");
            // 视频号发表页是 Wujie 微前端，编辑器可能懒加载，先等一会儿再诊断
            sleepQuietly(8000);

            // 3. 归位到主文档上下文（视频号是 Wujie Shadow DOM，无需切 frame），
            //    并打印（含 Shadow DOM 的）页面元素 + 把完整 DOM 落盘，便于失败定位
            resetWeChatContext(driver);
            dumpWeChatDom(driver);
            PageDumpUtil.snapshot(driver, "定位编辑器上下文");

            // 4. 填写标题
            fillWeChatTitle(driver, title);
            PageDumpUtil.snapshot(driver, "填写标题", "title=" + title);

            // 5. 选择视频文件
            selectWeChatVideoFile(driver, mergeVideos);
            PageDumpUtil.snapshot(driver, "已选择视频文件");

            // 6. 等待上传 / 转码完成
            waitWeChatUploadFinished(driver, Duration.ofMinutes(30));
            PageDumpUtil.snapshot(driver, "上传完成");

            // 7. 点发表并确认成功
            publishWeChatVideoAndConfirm(driver);
            PageDumpUtil.snapshot(driver, "发布成功");

            logger.info("视频号发布完成！");
        } catch (Exception e) {
            logger.error("视频号上传失败", e);
            PageDumpUtil.dumpOnFailure(driver, "视频号上传失败", e);
            rethrow(e);
        } finally {
            PageDumpUtil.endRun();
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /**
     * 登录微信视频号。
     *
     * <p>驱动更新地址：https://googlechromelabs.github.io/chrome-for-testing/#stable</p>
     */
    public void loginWeChat() {
        // 自动判断 Chrome 版本并自动下载匹配的 ChromeDriver
        ChromeDriverUtil.prepare();
        ChromeOptions chromeOptions = buildOptions(CREATOR_USER_DATA_DIR, true);
        WebDriver driver = createDriver(chromeOptions, CREATOR_USER_DATA_DIR);

        PageDumpUtil.startRun("视频号登录");
        try {
            driver.get("https://channels.weixin.qq.com/platform");
            PageDumpUtil.snapshot(driver, "打开视频号助手");
            ensureWeChatLogin(driver);
            logger.info("视频号登录成功！");
        } catch (Exception e) {
            logger.error("视频号登录失败", e);
            PageDumpUtil.dumpOnFailure(driver, "视频号登录失败", e);
            throw new RuntimeException("视频号登录失败：" + e.getMessage(), e);
        } finally {
            PageDumpUtil.endRun();
            if (driver != null) {
                driver.quit();
            }
        }
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

    // ==========================================================================
    // 微信视频号（channels.weixin.qq.com）相关：选择器 + 流程辅助
    // 和抖音一样，一个逻辑元素对应一组候选选择器，站点改版时只需在这里补候选。
    // ==========================================================================

    /**
     * 视频号：未登录时会出现登录二维码 / 登录文案（按实测 login.html 的真实结构）。
     *
     * <p><b>重要</b>：判断未登录不能依赖"内容管理 / 发表视频"这类文案 ——
     * 登录页的宣传卡片里同样有"内容管理"文案，实测会把登录页误判成已登录
     * （这就是"2 秒就显示登录成功、实际还在 login.html"的原因）。</p>
     */
    private By[] wechatLoginForm() {
        return new By[]{
                By.id("wx-oauth-container"),
                By.cssSelector("#wx-oauth-container iframe"),
                By.cssSelector(".qrcode-area"),
                By.cssSelector(".qrcode-tip"),
                By.cssSelector(".login-qrcode-wrap"),
                By.xpath("//*[contains(text(),'登录视频号助手')]")
        };
    }

    // ---- 视频号元素选择器：**必须用 CSS**，因为要穿透 Shadow DOM（Wujie）----
    //
    // 视频号助手是「无界 Wujie」微前端，真实表单都在 <wujie-app> 的 Shadow DOM 里。
    // Selenium 的 By.cssSelector / By.xpath 都**穿不透 Shadow DOM**（这就是"输入框/上传控件
    // 一个都找不到"的真正原因，跟风控无关），所以这里统一用 CSS 选择器字符串，
    // 再由下面的 deepFindXxx（JS 递归 shadowRoot）来查找。

    /**
     * 视频号：视频描述（富文本框）
     *
     * <p>实测真实 HTML：{@code <div contenteditable="" data-placeholder="添加描述" class="input-editor">}。
     * 注意它的 contenteditable 值是<b>空字符串</b>（不是 "true"），所以
     * {@code [contenteditable='true']} 匹配不到，必须用 class {@code .input-editor} 或不限值。</p>
     */
    private String[] wechatDescInputCss() {
        return new String[]{
                "div.input-editor",
                "div.input-editor[contenteditable]",
                "[contenteditable]",
                "textarea"
        };
    }

    /**
     * 视频号：短标题输入框
     */
    private String[] wechatShortTitleCss() {
        return new String[]{
                "input[placeholder*='短标题']",
                "input[placeholder*='标题']",
                "input[type='text']"
        };
    }

    /**
     * 视频号：视频上传控件（隐藏的 input[type=file]）
     */
    private String[] wechatFileInputCss() {
        return new String[]{
                "input[type='file'][accept*='video']",
                "input[type='file'][accept*='mp4']",
                "input[type='file']"
        };
    }

    // ==========================================================================
    // 穿透 Shadow DOM 的深度查询工具（Wujie 微前端必须用这套）
    // ==========================================================================

    /**
     * JS：递归穿透 Shadow DOM 查找元素
     */
    private static final String DEEP_QUERY_JS =
            "function __oc_deepAll(sel, root) {" +
                    "  root = root || document;" +
                    "  var out = [];" +
                    "  try { out = out.concat(Array.prototype.slice.call(root.querySelectorAll(sel))); } catch (e) {}" +
                    "  var ns = root.querySelectorAll('*');" +
                    "  for (var i = 0; i < ns.length; i++) {" +
                    "    if (ns[i].shadowRoot) { out = out.concat(__oc_deepAll(sel, ns[i].shadowRoot)); }" +
                    "  }" +
                    "  return out;" +
                    "}";

    /**
     * 递归穿透 Shadow DOM 查找所有匹配元素
     */
    @SuppressWarnings("unchecked")
    private List<WebElement> deepFindAll(WebDriver driver, String css) {
        try {
            Object result = ((JavascriptExecutor) driver)
                    .executeScript(DEEP_QUERY_JS + "return __oc_deepAll(arguments[0]);", css);
            if (result instanceof List) {
                return (List<WebElement>) result;
            }
        } catch (Exception e) {
            logger.debug("deepFindAll 失败({})：{}", css, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 依次尝试多个 CSS 选择器，返回第一个命中的元素（穿透 Shadow DOM，不要求可见）
     */
    private WebElement deepFindFirst(WebDriver driver, String... cssList) {
        for (String css : cssList) {
            for (WebElement element : deepFindAll(driver, css)) {
                return element;
            }
        }
        return null;
    }

    /**
     * 依次尝试多个 CSS 选择器，返回第一个"可见"的元素（穿透 Shadow DOM）。
     *
     * <p><b>注意</b>：这里<b>不能</b>用 {@code WebElement.isDisplayed()} —— 实测它对
     * Wujie Shadow DOM 内的元素会误判为"不可见"（返回 false），从而表现为
     * "元素明明在 DOM 里、日志却报找不到"（视频描述框就是这么被漏掉的）。
     * 改用 JS 的 {@code getBoundingClientRect()} + {@code getComputedStyle()} 判断，
     * 对 Shadow DOM 内元素同样准确。</p>
     */
    private WebElement deepFindFirstVisible(WebDriver driver, String... cssList) {
        for (String css : cssList) {
            for (WebElement element : deepFindAll(driver, css)) {
                if (deepIsVisible(driver, element)) {
                    return element;
                }
            }
        }
        return null;
    }

    /**
     * JS 判断（含 Shadow DOM 内的）元素是否可见。
     *
     * <p>用 {@code getBoundingClientRect()} 是否有面积 + {@code getComputedStyle()}
     * 是否隐藏来判断，避免 {@code isDisplayed()} 在 Shadow DOM 上的误判。</p>
     */
    private boolean deepIsVisible(WebDriver driver, WebElement element) {
        try {
            Object ok = ((JavascriptExecutor) driver).executeScript(
                    "var el=arguments[0];"
                            + "if(!el) return false;"
                            + "var r=el.getBoundingClientRect();"
                            + "if(r.width<=0||r.height<=0) return false;"
                            + "var s=window.getComputedStyle(el);"
                            + "return s.visibility!=='hidden' && s.display!=='none' && s.opacity!=='0';",
                    element);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 等待元素出现（穿透 Shadow DOM），超时返回 null
     *
     * @param visible 是否要求可见
     */
    private WebElement waitDeep(WebDriver driver, Duration timeout, boolean visible, String... cssList) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            WebElement element = visible ? deepFindFirstVisible(driver, cssList) : deepFindFirst(driver, cssList);
            if (element != null) {
                return element;
            }
            sleepQuietly(500);
        }
        return null;
    }

    /**
     * 是否存在任意一个"可见"的匹配元素（穿透 Shadow DOM）
     */
    private boolean deepExistsVisible(WebDriver driver, String... cssList) {
        return deepFindFirstVisible(driver, cssList) != null;
    }

    /**
     * 在（含 Shadow DOM 的）整棵文档树里按可见文本查找元素
     */
    private WebElement deepFindByText(WebDriver driver, String text) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "function __oc_vis(n){var r=n.getBoundingClientRect();return r.width>0&&r.height>0;}" +
                            "function __oc_find(root, t) {" +
                            "  var ns = root.querySelectorAll('button,[role=button],a,div,span');" +
                            "  for (var i = 0; i < ns.length; i++) {" +
                            "    if ((ns[i].innerText || '').trim() === t && __oc_vis(ns[i])) { return ns[i]; }" +
                            "  }" +
                            "  var all = root.querySelectorAll('*');" +
                            "  for (var j = 0; j < all.length; j++) {" +
                            "    if (all[j].shadowRoot) { var r = __oc_find(all[j].shadowRoot, t); if (r) { return r; } }" +
                            "  }" +
                            "  return null;" +
                            "}" +
                            "return __oc_find(document, arguments[0]);", text);
            if (result instanceof WebElement) {
                return (WebElement) result;
            }
        } catch (Exception e) {
            logger.debug("deepFindByText 失败({})：{}", text, e.getMessage());
        }
        return null;
    }

    /**
     * 判断视频号登录状态（以 <b>URL 为准</b>）。
     *
     * <p>实测：未登录时访问 {@code /platform} 会被重定向到 {@code /login.html}，
     * 所以：</p>
     * <ul>
     *   <li>URL 含 {@code login} 或页面上出现登录二维码 → 未登录</li>
     *   <li>URL 在 {@code /platform} 且没有登录二维码 → 已登录</li>
     *   <li>其它（页面加载中）→ {@code null}，继续等</li>
     * </ul>
     *
     * <p><b>不要</b>再用"内容管理 / 发表视频"文案判断登录态：登录页的宣传卡片里
     * 也有"内容管理"，会把登录页误判成已登录。</p>
     *
     * @return {@code true}=已登录，{@code false}=未登录，{@code null}=无法判断
     */
    private Boolean wechatLoginState(WebDriver driver) {
        String url = null;
        try {
            url = driver.getCurrentUrl();
        } catch (Exception ignored) {
            // 忽略
        }
        boolean onLoginUrl = url != null && (url.contains("login.html") || url.contains("/login"));
        boolean hasLoginForm = WebWaitUtil.exists(driver, wechatLoginForm());
        if (onLoginUrl || hasLoginForm) {
            return Boolean.FALSE;
        }
        if (url != null && url.contains("/platform")) {
            return Boolean.TRUE;
        }
        return null;
    }

    /**
     * 连续多次都判定为"已登录"才算数。
     *
     * <p>未登录时访问 {@code /platform} 会先短暂停留在 platform 再被重定向到
     * {@code login.html}，单次判断很容易误判；这里要求连续 3 次（约 3 秒）都成立。</p>
     */
    private boolean isWeChatLoggedInStable(WebDriver driver) {
        for (int i = 0; i < 3; i++) {
            if (!Boolean.TRUE.equals(wechatLoginState(driver))) {
                return false;
            }
            sleepQuietly(1000);
        }
        return true;
    }

    /**
     * 确保视频号已登录（未登录则等待人工扫码，最多 20 分钟）。
     *
     * <p>等待期间<b>不要刷新页面</b>，否则会把扫码弹窗刷掉。</p>
     */
    private void ensureWeChatLogin(WebDriver driver) {
        if (isWeChatLoggedInStable(driver)) {
            logger.info("视频号已登录");
            return;
        }
        logger.info("视频号未登录，请在浏览器中扫码登录（最多等待 20 分钟）...");
        PageDumpUtil.note("视频号未登录，等待人工扫码登录");
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(20).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isWeChatLoggedInStable(driver)) {
                logger.info("视频号登录成功");
                PageDumpUtil.snapshot(driver, "视频号登录成功");
                return;
            }
            sleepQuietly(2000);
        }
        throw new IllegalStateException("视频号登录超时（20 分钟内未检测到登录成功），当前页面："
                + WebWaitUtil.describePage(driver));
    }

    /**
     * 静默睡眠（吞掉中断，仅用于等待轮询）
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 视频号发表页是 Wujie 微前端，真实表单都在 Shadow DOM 里，
     * <b>不能</b>靠 switchTo() 切 frame（那个隐藏 iframe 只是 Wujie 沙箱，
     * 老代码切 frame(0) 切的就是它）。这里只把上下文归位到主文档，
     * 元素查找统一走 deepFindXxx（穿透 shadow）。
     */
    private void resetWeChatContext(WebDriver driver) {
        try {
            driver.switchTo().defaultContent();
        } catch (Exception ignored) {
            // 忽略
        }
    }

    // ==========================================================================
    // 视频号：JS 直连（不经过 WebDriver 元素引用）的操作工具
    //
    // 【为什么必须这样】视频号是「无界 Wujie」微前端，真实表单都在 Shadow DOM 里。
    // 实测（Chrome 155 + ChromeDriver 155）：WebDriver **无法为 shadow 内的元素生成元素引用** ——
    //   * executeScript 返回 shadow 内元素          → stale element reference: stale element not found in the current frame
    //   * 原生 getShadowRoot().findElements(...)     → 同样的 stale element reference
    // 因此不能再"先找 WebElement 再操作"。这里统一改成：
    //   * 查找 / 聚焦 / 写入 / 点击  全部在页面 JS 里完成，Java 侧只接收基本类型（字符串/数字/布尔）；
    //   * 上传文件这种必须投喂本地路径的，走 CDP：Runtime.evaluate 取 objectId → DOM.setFileInputFiles。
    // ==========================================================================

    /**
     * JS 深查命中数量（只返回数字，不涉及元素引用）
     */
    private long jsDeepCount(WebDriver driver, String css) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS + "return __oc_deepAll(arguments[0]).length;", css);
            return (r instanceof Number) ? ((Number) r).longValue() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 把一组 CSS 选择器拼成 JS 数组字面量（内联进脚本，避免把 List 当参数传）
     */
    private String jsArray(String... cssList) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cssList.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('\'').append(cssList[i].replace("\\", "\\\\").replace("'", "\\'")).append('\'');
        }
        return sb.append(']').toString();
    }

    /**
     * 往视频号「视频描述」写内容：全 JS 完成，返回写入后的文本（找不到元素返回 null）。
     *
     * <p>先选中全部内容再 {@code insertText}，保证重复调用是"替换"而不是"追加"，
     * 且会触发 React 的 input 事件（实测页面能接受）。</p>
     */
    private String jsDeepWriteDesc(WebDriver driver, String text) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS
                            + "var sels=" + jsArray(wechatDescInputCss()) + ", t=arguments[0];"
                            + "for (var k=0;k<sels.length;k++){"
                            + "  var a=__oc_deepAll(sels[k]);"
                            + "  for (var i=0;i<a.length;i++){"
                            + "    var el=a[i];"
                            + "    var rect=el.getBoundingClientRect();"
                            + "    if(rect.width<=0||rect.height<=0){continue;}"
                            + "    el.focus();"
                            + "    var range=document.createRange(); range.selectNodeContents(el);"
                            + "    var sel=window.getSelection(); sel.removeAllRanges(); sel.addRange(range);"
                            + "    document.execCommand('insertText', false, t);"
                            + "    return String(el.innerText || el.textContent || '');"
                            + "  }"
                            + "}"
                            + "return null;",
                    text);
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) {
            logger.warn("视频号描述写入(JS)异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 用 JS 在（含 Shadow DOM 的）页面里按可见文本点击（返回是否点到）
     */
    private boolean jsDeepClickByText(WebDriver driver, String text) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS
                            + "function __oc_findText(root, t) {"
                            + "  var prefer=root.querySelectorAll('button,a,[role=button]');"
                            + "  for (var i=0;i<prefer.length;i++){"
                            + "    if(String(prefer[i].innerText||'').trim()===t){var rc=prefer[i].getBoundingClientRect();if(rc.width>0&&rc.height>0){return prefer[i];}}"
                            + "  }"
                            + "  var divs=root.querySelectorAll('div,span');"
                            + "  for (var j=0;j<divs.length;j++){"
                            + "    if(String(divs[j].innerText||'').trim()===t){var r2=divs[j].getBoundingClientRect();if(r2.width>0&&r2.height>0){return divs[j];}}"
                            + "  }"
                            + "  var ns=root.querySelectorAll('*');"
                            + "  for (var m=0;m<ns.length;m++){ if(ns[m].shadowRoot){ var f=__oc_findText(ns[m].shadowRoot, t); if(f){ return f; } } }"
                            + "  return null;"
                            + "}"
                            + "var el=__oc_findText(document, arguments[0]);"
                            + "if(!el){ return false; }"
                            + "el.scrollIntoView({block:'center'}); el.click(); return true;",
                    text);
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            logger.warn("视频号按文本点击(JS)异常({})：{}", text, e.getMessage());
            return false;
        }
    }

    /**
     * 用 JS 依次尝试多个 CSS 选择器，点击第一个可见元素（返回是否点到）
     */
    private boolean jsDeepClickCss(WebDriver driver, String... cssList) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS
                            + "var sels=" + jsArray(cssList) + ";"
                            + "for (var k=0;k<sels.length;k++){"
                            + "  var a=__oc_deepAll(sels[k]);"
                            + "  for (var i=0;i<a.length;i++){"
                            + "    var rect=a[i].getBoundingClientRect();"
                            + "    if(rect.width>0&&rect.height>0){ a[i].scrollIntoView({block:'center'}); a[i].click(); return true; }"
                            + "  }"
                            + "}"
                            + "return false;");
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            logger.warn("视频号点击(JS)异常：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 是否存在可见的命中元素（返回布尔）
     */
    private boolean jsDeepAnyVisible(WebDriver driver, String... cssList) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS
                            + "var sels=" + jsArray(cssList) + ";"
                            + "for (var k=0;k<sels.length;k++){"
                            + "  var a=__oc_deepAll(sels[k]);"
                            + "  for (var i=0;i<a.length;i++){"
                            + "    var rect=a[i].getBoundingClientRect();"
                            + "    if(rect.width>0&&rect.height>0){ return true; }"
                            + "  }"
                            + "}"
                            + "return false;");
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 页面里是否存在某个可见的文本（含 Shadow DOM）
     */
    private boolean jsDeepHasText(WebDriver driver, String... texts) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    "function __oc_hasText(root, list) {"
                            + "  var ns=root.querySelectorAll('button,a,[role=button],div,span');"
                            + "  for (var i=0;i<ns.length;i++){"
                            + "    var t=String(ns[i].innerText||'').trim();"
                            + "    if(!t){continue;}"
                            + "    for (var k=0;k<list.length;k++){ if(t.indexOf(list[k])>=0){ var rc=ns[i].getBoundingClientRect(); if(rc.width>0&&rc.height>0){ return true; } } }"
                            + "  }"
                            + "  var all=root.querySelectorAll('*');"
                            + "  for (var j=0;j<all.length;j++){ if(all[j].shadowRoot){ if(__oc_hasText(all[j].shadowRoot, list)){ return true; } } }"
                            + "  return false;"
                            + "}"
                            + "return __oc_hasText(document, " + jsArray(texts) + ");");
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 视频号：把本地文件投喂到（Shadow DOM 里的）{@code input[type=file]}。
     *
     * <p>WebDriver 拿不到 shadow 内元素，所以走 CDP：
     * {@code Runtime.evaluate} 取 input 的 objectId → {@code DOM.setFileInputFiles} 设置文件，
     * 最后用普通 JS 回读 {@code files.length} 校验。</p>
     */
    private boolean jsDeepSetFileByCdp(WebDriver driver, String css, File video) {
        if (!(driver instanceof ChromeDriver)) {
            return false;
        }
        ChromeDriver chrome = (ChromeDriver) driver;
        try {
            // 【坑】Runtime.evaluate 是当"脚本"执行的，顶层 return 非法（SyntaxError: Illegal return statement），
            // 必须包成 IIFE；否则拿到的是 SyntaxError 对象的 objectId，setFileInputFiles 会静默无效。
            String expr = "(function(){" + DEEP_QUERY_JS
                    + "var a=__oc_deepAll('" + css.replace("\\", "\\\\").replace("'", "\\'") + "');"
                    + "return a.length ? a[0] : null;"
                    + "})()";
            java.util.Map<String, Object> evalParams = new java.util.HashMap<>();
            evalParams.put("expression", expr);
            evalParams.put("returnByValue", false);
            java.util.Map<String, Object> evalRes = chrome.executeCdpCommand("Runtime.evaluate", evalParams);
            if (containsKeyDeep(evalRes, "exceptionDetails")) {
                logger.warn("视频号投喂文件：CDP 表达式出错：{}", evalRes);
                return false;
            }
            String objectId = findStringValue(evalRes, "objectId");
            if (objectId == null) {
                return false;
            }

            java.util.Map<String, Object> setParams = new java.util.HashMap<>();
            setParams.put("files", java.util.Collections.singletonList(video.getAbsolutePath()));
            setParams.put("objectId", objectId);
            chrome.executeCdpCommand("DOM.setFileInputFiles", setParams);
            sleepQuietly(500);

            // 用【同一个节点】回读 files 数量校验。
            // 注意：不能再用深查（jsFileCount）——投喂后页面常会重渲染换掉 input 节点，
            // 深查拿到的是新节点（files=0），会把成功误判成失败。
            java.util.Map<String, Object> callParams = new java.util.HashMap<>();
            callParams.put("objectId", objectId);
            callParams.put("functionDeclaration", "function(){ return this.files ? this.files.length : 0; }");
            callParams.put("returnByValue", true);
            java.util.Map<String, Object> callRes = chrome.executeCdpCommand("Runtime.callFunctionOn", callParams);
            Object value = findValueDeep(callRes, "value");
            long count = (value instanceof Number) ? ((Number) value).longValue() : 0L;
            if (count > 0) {
                logger.info("视频号：（CDP）已把文件投喂到 {}，files={}", css, count);
            }
            return count > 0;
        } catch (Exception e) {
            logger.warn("CDP 投喂文件失败({})：{}", css, e.getMessage());
            return false;
        }
    }

    /**
     * JS 读回（Shadow DOM 里的）{@code input[type=file]} 的 files 数量（-1 表示找不到元素）
     */
    private long jsFileCount(WebDriver driver, String css) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS
                            + "var a=__oc_deepAll(arguments[0]);"
                            + "if(!a.length){return -1;}"
                            + "return a[0].files ? a[0].files.length : 0;",
                    css);
            return (r instanceof Number) ? ((Number) r).longValue() : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * 在（可能嵌套的）Map 里递归找 key 对应的字符串值（适配不同的 CDP 返回结构）
     */
    @SuppressWarnings("unchecked")
    private String findStringValue(java.util.Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v instanceof String) {
            return (String) v;
        }
        for (Object val : map.values()) {
            if (val instanceof java.util.Map) {
                String r = findStringValue((java.util.Map<String, Object>) val, key);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * 在（可能嵌套的）Map 里递归判断是否存在某个 key
     */
    @SuppressWarnings("unchecked")
    private boolean containsKeyDeep(java.util.Map<String, Object> map, String key) {
        if (map == null) {
            return false;
        }
        if (map.containsKey(key)) {
            return true;
        }
        for (Object val : map.values()) {
            if (val instanceof java.util.Map
                    && containsKeyDeep((java.util.Map<String, Object>) val, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在（可能嵌套的）Map 里递归找 key 对应的基本类型值（String / Number / Boolean）
     */
    @SuppressWarnings("unchecked")
    private Object findValueDeep(java.util.Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return v;
        }
        for (Object val : map.values()) {
            if (val instanceof java.util.Map) {
                Object r = findValueDeep((java.util.Map<String, Object>) val, key);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * 填写视频号标题（写入「视频描述」富文本框，带重试）。
     *
     * <p>全程 JS 直连 Shadow DOM（WebDriver 拿不到 shadow 内元素，详见上方说明）。</p>
     */
    private void fillWeChatTitle(WebDriver driver, String title) throws InterruptedException {
        if (title == null || title.isBlank()) {
            return;
        }
        String sample = title.substring(0, Math.min(5, title.length()));
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
        while (System.currentTimeMillis() < deadline) {
            String now = jsDeepWriteDesc(driver, title);
            if (now == null) {
                logger.info("视频号「视频描述」暂时找不到{}，5 秒后重试", describeDescCandidates(driver));
            } else if (now.contains(sample)) {
                logger.info("视频号标题（视频描述）已填写：{}", title);
                return;
            } else {
                logger.info("视频号「视频描述」找到了但写不进去（读回=[{}]），5 秒后重试",
                        now.length() > 40 ? now.substring(0, 40) : now);
            }
            sleepQuietly(5000);
        }
        throw new IllegalStateException("视频号标题写入失败（重试 2 分钟）；页面元素：" + describeWeChatDom(driver));
    }

    /**
     * 诊断用：逐个候选选择器统计命中数量
     */
    private String describeDescCandidates(WebDriver driver) {
        StringBuilder sb = new StringBuilder();
        for (String css : wechatDescInputCss()) {
            sb.append(" | ").append(css).append('=').append(jsDeepCount(driver, css));
        }
        return sb.toString();
    }


    /**
     * 打印元素关键信息（shadow DOM 内也能用）：是否可编辑、当前文本、outerHTML 片段
     */
    private String describeElement(WebDriver driver, WebElement element) {
        try {
            Object info = ((JavascriptExecutor) driver).executeScript(
                    "var el=arguments[0];"
                            + "var t=el.innerText||el.value||el.textContent||'';"
                            + "return 'isContentEditable=' + el.isContentEditable"
                            + " + ' text=' + String(t).slice(0,30)"
                            + " + ' html=' + String(el.outerHTML||'').slice(0,200);", element);
            return String.valueOf(info);
        } catch (Exception e) {
            return "取元素信息失败：" + e.getMessage();
        }
    }

    /**
     * 往（可能是 Shadow DOM 里的）富文本 / 输入框写值。
     *
     * <p>视频号的"视频描述"是 React 管理的 contenteditable（{@code contenteditable=""}），
     * 直接改 innerText 不触发 React 的 input 事件，会被无视/重置。所以顺序为：
     * 聚焦 → {@code execCommand('insertText')} → <b>CDP Input.insertText</b> →
     * 剪贴板粘贴 → sendKeys，每步都读回来校验。</p>
     */
    private boolean setEditorText(WebDriver driver, WebElement element, String text) {
        try {
            clickElement(driver, element);
        } catch (Exception ignored) {
            // 忽略
        }
        focusElement(driver, element);

        if (jsInsertText(driver, element, text)) {
            logger.info("视频号描述已写入（execCommand）");
            return true;
        }
        if (cdpInsertText(driver, element, text)) {
            logger.info("视频号描述已写入（CDP insertText）");
            return true;
        }
        try {
            pasteText(driver, element, text);
            if (verifyTyped(driver, element, text)) {
                logger.info("视频号描述已写入（剪贴板粘贴）");
                return true;
            }
        } catch (Exception ignored) {
            // 忽略
        }
        try {
            element.sendKeys(text);
            if (verifyTyped(driver, element, text)) {
                logger.info("视频号描述已写入（sendKeys）");
                return true;
            }
        } catch (Exception ignored) {
            // 忽略
        }
        return false;
    }

    /**
     * JS 聚焦（shadow DOM 内元素也能用）
     */
    private void focusElement(WebDriver driver, WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].focus();", element);
        } catch (Exception ignored) {
            // 忽略
        }
    }

    /**
     * 用 {@code execCommand('insertText')} 写入（对 React 富文本编辑器最有效，会触发 input 事件）
     */
    private boolean jsInsertText(WebDriver driver, WebElement element, String text) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "var el=arguments[0], t=arguments[1];"
                            + "el.focus();"
                            + "var sel=window.getSelection();"
                            + "var range=document.createRange();"
                            + "range.selectNodeContents(el);"
                            + "range.collapse(false);"
                            + "sel.removeAllRanges();"
                            + "sel.addRange(range);"
                            + "document.execCommand('insertText', false, t);", element, text);
            sleepQuietly(400);
            return verifyTyped(driver, element, text);
        } catch (Exception e) {
            logger.debug("execCommand 写入失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 用 CDP 的 {@code Input.insertText} 注入文本（走浏览器输入管道，最接近真人输入）
     */
    private boolean cdpInsertText(WebDriver driver, WebElement element, String text) {
        try {
            if (!(driver instanceof ChromeDriver)) {
                return false;
            }
            focusElement(driver, element);
            sleepQuietly(200);
            ((ChromeDriver) driver).executeCdpCommand("Input.insertText", java.util.Map.of("text", text));
            sleepQuietly(500);
            return verifyTyped(driver, element, text);
        } catch (Exception e) {
            logger.debug("CDP insertText 失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 选择视频号视频文件。
     *
     * <p>视频号的上传控件藏在 Wujie Shadow DOM 里，<b>WebDriver 无法为 shadow 内元素生成元素引用</b>
     * （会报 {@code stale element reference: stale element not found in the current frame}），
     * 所以 sendKeys 那条路走不通。这里改走 CDP：
     * {@code Runtime.evaluate} 拿到 input 的 objectId → {@code DOM.setFileInputFiles} 直接投喂本地文件路径。</p>
     */
    private void selectWeChatVideoFile(WebDriver driver, File video) {
        if (video == null || !video.exists()) {
            throw new IllegalStateException("视频号上传失败：视频文件不存在 " + video);
        }
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        boolean ok = false;
        while (System.currentTimeMillis() < deadline && !ok) {
            for (String css : wechatFileInputCss()) {
                if (jsDeepSetFileByCdp(driver, css, video)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                sleepQuietly(1000);
            }
        }
        if (!ok) {
            throw new IllegalStateException("视频号上传失败：未能把视频投喂到 input[type=file]（含 Shadow DOM）；页面元素："
                    + describeWeChatDom(driver));
        }
        logger.info("视频号视频路径已提交：{}", video.getAbsolutePath());
    }

    /**
     * 等待视频号上传 / 转码完成（穿透 Shadow DOM 判断"上传中 / 进度条"是否消失）。
     */
    /**
     * 等待视频号上传 / 转码完成。
     *
     * <p>用<b>正面信号</b>判断：出现「重新上传 / 更换封面 / 上传成功」文案，
     * 或「发表」按钮不再带 {@code weui-desktop-btn_disabled}。超时不抛异常，交给后面发表重试兜底。</p>
     *
     * <p>不能再靠"没有 progress/loading 就算完成"：实测空白页上 {@code [class*='loading']} 就有 22 个，
     * 那个判断永远为假，会把每一步都拖满超时。</p>
     */
    private void waitWeChatUploadFinished(WebDriver driver, Duration timeout) {
        logger.info("等待视频号上传 / 转码完成（最多 {} 分钟）...", timeout.toMinutes());
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (jsWeChatUploadFinished(driver)) {
                logger.info("视频号上传 / 转码完成");
                return;
            }
            logger.info("视频号仍在上传 / 转码中...");
            sleepQuietly(5000);
        }
        logger.warn("等待视频号上传超时，交给发表重试兜底");
    }

    /**
     * 视频号上传是否完成（正面信号，全 JS，含 Shadow DOM）
     */
    private boolean jsWeChatUploadFinished(WebDriver driver) {
        try {
            Object r = ((JavascriptExecutor) driver).executeScript(
                    DEEP_QUERY_JS
                            + "function __oc_has(root, list){"
                            + "  var ns=root.querySelectorAll('button,a,[role=button],div,span');"
                            + "  for (var i=0;i<ns.length;i++){ var t=String(ns[i].innerText||'').trim();"
                            + "    for (var k=0;k<list.length;k++){ if(t===list[k]){ var rc=ns[i].getBoundingClientRect(); if(rc.width>0&&rc.height>0){ return true; } } } }"
                            + "  var all=root.querySelectorAll('*');"
                            + "  for (var j=0;j<all.length;j++){ if(all[j].shadowRoot && __oc_has(all[j].shadowRoot, list)){ return true; } }"
                            + "  return false;"
                            + "}"
                            + "if(__oc_has(document, ['重新上传','更换封面','上传成功'])){ return true; }"
                            + "var btns=__oc_deepAll('button');"
                            + "for (var i=0;i<btns.length;i++){ var t=String(btns[i].innerText||'').trim();"
                            + "  if(t==='发表'){"
                            + "    var rc=btns[i].getBoundingClientRect();"
                            + "    if(rc.width>0&&rc.height>0 && String(btns[i].className).indexOf('disabled')<0){ return true; }"
                            + "  }"
                            + "}"
                            + "return false;");
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 点击「发表」并确认发布成功（失败会重试，最多 10 分钟）。
     */
    private void publishWeChatVideoAndConfirm(WebDriver driver) {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis();
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                // 全 JS 点击（Wujie Shadow DOM 里 WebDriver 拿不到元素）：先按文本"发表"，再退回主按钮类名
                boolean clicked = jsDeepClickByText(driver, "发表");
                if (!clicked) {
                    clicked = jsDeepClickCss(driver, "button.weui-desktop-btn_primary", "button[class*='primary']");
                }
                if (!clicked) {
                    logger.info("暂未找到视频号「发表」按钮（第 {} 次），稍后重试", attempt);
                    sleepQuietly(5000);
                    continue;
                }
                logger.info("已点击视频号「发表」按钮（第 {} 次）", attempt);

                if (waitWeChatPublishSuccess(driver, Duration.ofSeconds(90))) {
                    return;
                }
                PageDumpUtil.snapshot(driver, "发表未确认_第" + attempt + "次");
            } catch (Exception e) {
                logger.warn("视频号发表尝试失败：{}", e.getMessage());
            }
            sleepQuietly(3000);
        }
        throw new IllegalStateException("视频号发布失败：多次尝试后仍未确认发布成功");
    }

    /**
     * 判断视频号是否发布成功（URL 离开发表页 / 出现成功提示）。
     */
    private boolean waitWeChatPublishSuccess(WebDriver driver, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                String url = driver.getCurrentUrl();
                if (url != null && !url.contains("/post/create")) {
                    logger.info("视频号发布成功：页面已跳转到 {}", url);
                    return true;
                }
                if (jsDeepHasText(driver, "发表成功", "发布成功", "审核中")) {
                    logger.info("视频号发布成功：出现成功提示");
                    return true;
                }
            } catch (Exception ignored) {
                // 忽略，继续等待
            }
            sleepQuietly(1000);
        }
        return false;
    }

    /**
     * 把（含 Shadow DOM 的）页面元素概况打到日志 + report，并把完整 DOM 落盘，便于失败定位
     */
    private void dumpWeChatDom(WebDriver driver) {
        String info = describeWeChatDom(driver);
        logger.info("视频号页面元素（含 Shadow DOM）：{}", info);
        PageDumpUtil.note("视频号页面元素（含 Shadow DOM）：" + info);
        dumpWeChatShadowHtml(driver);
    }

    /**
     * 采集（含 Shadow DOM 的）页面里 input / textarea / select / contenteditable / button 的概况，
     * 并统计 shadowRoot 数量与 wujie-app 的 shadow 开闭状态。
     */
    private String describeWeChatDom(WebDriver driver) {
        try {
            Object info = ((JavascriptExecutor) driver).executeScript(
                    "function __oc_collect() {"
                            + "  var out = [];"
                            + "  var roots = 0;"
                            + "  var wapps = document.querySelectorAll('wujie-app');"
                            + "  for (var w = 0; w < wapps.length; w++) {"
                            + "    out.push('wujie-app[' + w + '] shadow=' + (wapps[w].shadowRoot ? 'open' : 'closed/null'));"
                            + "  }"
                            + "  function walk(root) {"
                            + "    var ins = root.querySelectorAll('input,textarea,select,[contenteditable],button');"
                            + "    for (var i = 0; i < ins.length; i++) {"
                            + "      var n = ins[i];"
                            + "      out.push(n.tagName + (n.getAttribute('type') ? '[' + n.getAttribute('type') + ']' : '')"
                            + "        + ' ph=' + (n.getAttribute('placeholder') || '')"
                            + "        + ' txt=' + String(n.innerText || n.value || '').trim().slice(0, 20)"
                            + "        + ' cls=' + (n.className || ''));"
                            + "    }"
                            + "    var all = root.querySelectorAll('*');"
                            + "    for (var j = 0; j < all.length; j++) {"
                            + "      if (all[j].shadowRoot) { roots++; walk(all[j].shadowRoot); }"
                            + "    }"
                            + "  }"
                            + "  walk(document);"
                            + "  out.unshift('shadowRootCount=' + roots);"
                            + "  return out;"
                            + "}return __oc_collect();");
            String s = String.valueOf(info);
            return s.length() > 3000 ? s.substring(0, 3000) : s;
        } catch (Exception e) {
            return "诊断失败：" + e.getMessage();
        }
    }

    /**
     * 把（含 Shadow DOM 的）完整 DOM 序列化写到本次快照目录（shadow-dom.html），便于离线排查。
     * Shadow DOM 的内容不会出现在 driver.getPageSource() 里，所以单独存一份。
     */
    private void dumpWeChatShadowHtml(WebDriver driver) {
        try {
            Object html = ((JavascriptExecutor) driver).executeScript(
                    "function __oc_ser(root) {"
                            + "  var sb = [];"
                            + "  function walk(n) {"
                            + "    if (n.nodeType === 1) {"
                            + "      var tag = n.tagName.toLowerCase();"
                            + "      if (tag === 'script' || tag === 'style' || tag === 'noscript' || tag === 'svg' || tag === 'path') { return; }"
                            + "      sb.push('<' + tag);"
                            + "      var a = n.attributes || [];"
                            + "      for (var i = 0; i < a.length; i++) { sb.push(' ' + a[i].name + '=' + String(a[i].value).slice(0, 60)); }"
                            + "      sb.push('>');"
                            + "      if (n.shadowRoot) { sb.push('<#shadow-root>'); var sc = n.shadowRoot.childNodes; for (var k = 0; k < sc.length; k++) { walk(sc[k]); } sb.push('</#shadow-root>'); }"
                            + "      var ch = n.childNodes;"
                            + "      for (var j = 0; j < ch.length; j++) { walk(ch[j]); }"
                            + "      sb.push('</' + tag + '>');"
                            + "    } else if (n.nodeType === 3) {"
                            + "      var t = String(n.textContent || '').trim();"
                            + "      if (t) { sb.push(t); }"
                            + "    }"
                            + "  }"
                            + "  var cs = root.childNodes || [];"
                            + "  for (var i = 0; i < cs.length; i++) { walk(cs[i]); }"
                            + "  return sb.join('');"
                            + "}return __oc_ser(document);");
            File dir = PageDumpUtil.getCurrentRunDir();
            if (dir != null && html != null) {
                File target = new File(dir, "shadow-dom.html");
                FileUtil.writeUtf8String(String.valueOf(html), target);
                logger.info("已把（含 Shadow DOM 的）完整 DOM 写到：{}", target.getAbsolutePath());
                PageDumpUtil.note("完整 DOM（含 Shadow）：" + target.getName());
            }
        } catch (Exception e) {
            logger.warn("dumpWeChatShadowHtml 失败：{}", e.getMessage());
        }
    }

}
