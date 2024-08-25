package com.app.util;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @Author guofan
 * @Create 2022/8/29
 */
class CrawlerUtilTest {


//    @Test
    void selectBaidu() {
        CrawlerUtil crawlerUtil = new CrawlerUtil();
        crawlerUtil.selectBaidu();
    }

    private WebDriver driver;
    JavascriptExecutor js;


//    @Test
    void myTest() {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver_win32\\chromedriver.exe");
        driver = new ChromeDriver();
//        driver.get("https://jianwai.youdao.com");
        driver.get("http://67.219.110.6/index.html#/");
//        driver.manage().window().maximize();
        WebElement element = driver.findElement(By.xpath("//*[@id=\"app\"]/div/form/div[1]/div/div/input"));
        element.clear();
        element.sendKeys("admin");
        WebElement element2 = driver.findElement(By.xpath("//*[@id=\"app\"]/div/form/div[2]/div/div/input"));
        element2.clear();
        element2.sendKeys("123");
        WebElement element3 = driver.findElement(By.xpath("//*[@id=\"app\"]/div/form/div[3]/div/div/input"));
        element3.clear();
        element3.sendKeys("gg");
        WebElement element4 = driver.findElement(By.xpath("//*[@id=\"app\"]/div/form/button"));
        element4.click();
//        driver.quit();
    }

//    @Test
    void myTes1t() throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver_win32\\chromedriver.exe");
        driver = new ChromeDriver();
        driver.get("https://jianwai.youdao.com");
//        driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
        driver.switchTo().frame(0);
//        WebElement element = driver.findElement(By.className("j-inputtext"));
        List<WebElement> elements = driver.findElements(By.className("j-inputtext"));
        WebElement element = elements.get(0);
        element.clear();
        element.sendKeys("664130988@qq.com");
        WebElement element2 = elements.get(1);
        element2.clear();
        element2.sendKeys("guofan123456@");
        WebElement element3 = driver.findElement(By.id("dologin"));
        element3.click();
        Thread.sleep(2000);
//        driver.get("https://jianwai.youdao.com/videoTrans");
//        Thread.sleep(2000);
//        WebElement fileInput = driver.findElement(By.name("file"));
//        fileInput.sendKeys("C:\\视频\\项目\\20220807滑板\\$200 MICRO BOOSTED BOARD.mp4");
//        WebElement submit = driver.findElement(By.className("btn-submit"));
//        submit.click();
        // 获取当前页项目
        List<WebElement> cardElement = driver.findElements(By.className("card"));
        WebElement webElement = cardElement.get(0);
        String text = webElement.getText();
        webElement.click();
        // 下载字幕
        driver.findElement(By.className("btn-download")).click();
        // 缺点
        driver.findElement(By.className("confirm")).click();
//        driver.quit();
    }

}
