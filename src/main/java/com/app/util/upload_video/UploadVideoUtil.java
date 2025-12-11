package com.app.util.upload_video;

import cn.hutool.core.io.FileUtil;
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
import java.util.List;
import java.util.function.Function;

/**
 * 视频上传工具类
 * 包括翻译视频上传字幕下载
 *
 * @Author guofan
 * @Create 2022/9/3
 */
public class UploadVideoUtil {
    public Logger logger = LoggerFactory.getLogger(getClass());
    // 标题
    private String[] strings;
    private String[] fileNames;
    private final boolean isNotHead = false;

    /**
     * 上传翻译视频等待翻译结束下载视频
     *
     * @param file     要翻译的视频文件
     * @param isUpload 是否上传
     * @return 字幕文件
     */
    public File uploadTranslateVideo(File file, boolean isUpload) throws InterruptedException, IOException {
        File newCCFile = null;
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver_win32\\chromedriver.exe");
        //配置自动登录
        ChromeOptions chromeOptions = new ChromeOptions();
        //添加用户cookies
        chromeOptions.addArguments("--user-data-dir=C:\\User Data1");
        // 设置无头模式
        if (isNotHead) {
            chromeOptions.addArguments("--headless");
        }
        WebDriver driver = new ChromeDriver(chromeOptions);
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
            WebElement fileInput = driver.findElement(By.name("file"));
            fileInput.sendKeys(file.getAbsolutePath());
            WebElement submit = driver.findElement(By.className("btn-submit"));
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
        System.setProperty("webdriver.chrome.driver", "C:\\tool\\chromedriver\\chromedriver.exe");
        //配置自动登录
        ChromeOptions chromeOptions = new ChromeOptions();
        // 设置无头模式
        if (isNotHead) {
            chromeOptions.addArguments("--headless");
        }
        //添加用户cookies
        chromeOptions.addArguments("--user-data-dir=C:\\tool\\chromedriver\\UserData");
        WebDriver driver = new ChromeDriver(chromeOptions);
        driver.get("https://creator.douyin.com/creator-micro/content/manage");
        Thread.sleep(1000);
        boolean isLogin = false;
        try {
            driver.findElement(By.className("login"));
        } catch (Exception e) {
            if (e instanceof NoSuchElementException) {
                logger.info("没有找到登录按钮，应该是登录了");
                isLogin = true;
            } else {
                throw e;
            }
        }
        if (!isLogin) {
            Wait<WebDriver> wait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(10))
                    .pollingEvery(Duration.ofSeconds(1))
                    .ignoring(NoSuchElementException.class);
            WebElement loginButton = wait.until(webDriver -> webDriver.findElement(By.className("login")));
            loginButton.click();
            // 点击登录按钮
            WebElement semiLoginButton = driver.findElement(By.className("semi-button-content"));
            semiLoginButton.click();
            Wait<WebDriver> uploadVideoButtonWait = new FluentWait<>(driver).withTimeout(Duration.ofMinutes(20))
                    .pollingEvery(Duration.ofSeconds(1))
                    .ignoring(NoSuchElementException.class);
            uploadVideoButtonWait.until(webDriver -> webDriver.findElement(By.className("container--38fle")));
            logger.info("登录成功！");
        }
        // 切换到登录界面
        driver.get("https://creator.douyin.com/creator-micro/content/upload");
        logger.info("跳转到上传视频界面！");
        try {
            // 上传视频
            Wait<WebDriver> videoInputWait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(120))
                    .pollingEvery(Duration.ofSeconds(1))
                    .ignoring(NoSuchElementException.class);
            WebElement element = videoInputWait.until(webDriver -> webDriver.findElement(By.tagName("input")));
            element.sendKeys(videoFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("产生了异常：", e);
            throw e;
        }
        logger.info("开始上传视频");
        // 填写标题
        Wait<WebDriver> titleWait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(120))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(NoSuchElementException.class);
        WebElement content = titleWait.until(webDriver -> webDriver.findElement(By.className("notranslate")));
        content.sendKeys(title);
        logger.info("填写标题");
        // 等待上传结束
        Wait<WebDriver> uploadDataWait = new FluentWait<>(driver).withTimeout(Duration.ofMinutes(30))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(NoSuchElementException.class);
        uploadDataWait.until(webDriver -> webDriver.findElement(By.className("long-card-s4BQ2a")));
        logger.info("上传结束");

        //progress--1KEPd 有这个不上传
        WebElement selectCover  = driver.findElement(By.className("cover-Jg3T4p"));
        selectCover.click();
        Thread.sleep(3000);

        WebElement okCover  = driver.findElement(By.className("secondary-zU1YLr"));
        okCover.click();
        Thread.sleep(3000);


        WebElement sendButton = driver.findElement(By.className("primary-cECiOJ"));
        sendButton.click();
        logger.info("点击上传");
        // 点击不绑定 不用绑定了去掉
//        Wait<WebDriver> unBindButtonWait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(120))
//                .pollingEvery(Duration.ofSeconds(1))
//                .ignoring(NoSuchElementException.class);
//        WebElement unBindButton = unBindButtonWait.until(webDriver -> webDriver.findElement(By.xpath("//*[@id=\"dialog-0\"]/div/div[2]/div/button[1]")));// 不起作用的感觉
//        unBindButton.click();
//        logger.info("不绑定");

        Thread.sleep(3000);
        driver.quit();
    }

    /**
     * 上传微信视频号
     *
     * @param title       标题
     * @param mergeVideos 合并后的视频
     */
    public void uploadWeChatVideo(String title, File mergeVideos) throws InterruptedException {
        // 1. 基础配置保持不变
        System.setProperty("webdriver.chrome.driver", "C:\\tool\\chromedriver\\chromedriver.exe");
        ChromeOptions chromeOptions = new ChromeOptions();
        if (isNotHead) {
            chromeOptions.addArguments("--headless");
        }
        chromeOptions.addArguments("--user-data-dir=C:\\tool\\chromedriver\\UserData");
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");

        WebDriver driver = new ChromeDriver(chromeOptions);
        WebDriverWait globalWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            // 2. 登录流程 (保持不变)
            driver.get("https://channels.weixin.qq.com/platform");
            Thread.sleep(1000);

            // ... (省略你的登录检查代码，原样保留即可) ...
            boolean isLogin = false;
            try {
                driver.findElement(By.className("login-content"));
            } catch (NoSuchElementException e) {
                isLogin = true;
            }
            if (!isLogin) {
                // ... 扫码登录逻辑 ...
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
        System.setProperty("webdriver.chrome.driver", "C:\\tool\\chromedriver\\chromedriver.exe");
        //配置自动登录
        ChromeOptions chromeOptions = new ChromeOptions();
        //添加用户cookies
        chromeOptions.addArguments("--user-data-dir=C:\\tool\\chromedriver\\UserData");
        WebDriver driver = new ChromeDriver(chromeOptions);

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
}
