package com.app.util.upload_video;

import cn.hutool.core.io.FileUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
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
            WebElement element = videoInputWait.until(webDriver -> webDriver.findElement(By.className("upload-btn-input-UY_qeY")));
            element.sendKeys(videoFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("产生了异常：",e);
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
        Wait<WebDriver> uploadDataWait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(120))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(NoSuchElementException.class);
        uploadDataWait.until(webDriver -> webDriver.findElement(By.className("long-card-s4BQ2a")));
        logger.info("上传结束");

        //progress--1KEPd 有这个不上传

        WebElement sendButton = driver.findElement(By.xpath("/html/body/div[1]/div/div[2]/div[3]/div/div/div/div[2]/div/div/div/div[2]/div[1]/div[14]/button[1]"));
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
        driver.get("https://channels.weixin.qq.com/platform");
        Thread.sleep(1000);
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
        }
        // 点击发表动态
        driver.get("https://channels.weixin.qq.com/platform/post/create");
        logger.info("开始发表视频...");
        Wait<WebDriver> uploadVideoButtonWait = new FluentWait<>(driver).withTimeout(Duration.ofMinutes(20))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(NoSuchElementException.class);
        WebElement titleTextField = uploadVideoButtonWait.until(webDriver -> webDriver.findElement(By.className("input-editor")));
        titleTextField.sendKeys(title);
        logger.info("填写标题完成...");
        // 上传视频
        Wait<WebDriver> videoInputWait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(120))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(NoSuchElementException.class);
        WebElement element = videoInputWait.until(webDriver -> webDriver.findElement(By.xpath("//*[@id=\"container-wrap\"]/div[2]/div/div/div[1]/div[3]/div/div[2]/div[1]/div/div/div/span/div/span/input")));
        element.sendKeys(mergeVideos.getAbsolutePath());
        logger.info("开始上传视频");

        // 等待上传结束
        Wait<WebDriver> uploadDataWait = new FluentWait<>(driver).withTimeout(Duration.ofSeconds(500))
                .pollingEvery(Duration.ofSeconds(1))
                .ignoring(NoSuchElementException.class);
        uploadDataWait.until(webDriver -> webDriver.findElement(By.xpath("//*[@id=\"container-wrap\"]/div[2]/div/div/div[1]/div[3]/div/div[2]/div[1]/div[2]/div/div[2]/div/span/div/div/div")));
        logger.info("上传结束");
        //                                                   //*[@id="container-wrap"]/div[2]/div/div/div[1]/div[3]/div/div[2]/div[2]/div[10]/div[5]/span/div/button
        //                                                 /html/body/div[1]/div/div[2]/div[2]/div/div/div[1]/div[3]/div/div[2]/div[2]/div[9]/div[5]/span/div/button
        WebElement sendButton = driver.findElement(By.xpath("/html/body/div[1]/div/div[2]/div[2]/div/div/div[1]/div[3]/div/div[2]/div[2]/div[9]/div[5]/span/div/button"));
        sendButton.click();
        logger.info("点击上传");
        Thread.sleep(4000);
        driver.quit();
    }

    /**
     * 登录微信视频号
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
