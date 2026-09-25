package com.app.util;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 多选择器兜底查找工具。
 *
 * <p>抖音、有道、视频号等站点会定期更换前端，尤其是抖音会把 class 名hash化成
 * cover-Jg3T4p / primary-cECiOJ 这种随机后缀。这里把"一个逻辑元素对应一组候选选择器"
 * 统一封装，站点改版时只需要在业务类的选择器常量里补一条候选，
 * 不用再满地改 xpath。</p>
 *
 * @Author OverCode
 */
public final class WebWaitUtil {

    private WebWaitUtil() {
    }

    /**
     * 依次尝试一组选择器，返回第一个命中的元素（不要求可见，适合隐藏的 input[type=file]）。
     */
    public static WebElement findFirst(WebDriver driver, Duration timeout, By... locators) {
        Wait<WebDriver> wait = buildWait(driver, timeout);
        return wait.until(d -> {
            for (By by : locators) {
                for (WebElement element : d.findElements(by)) {
                    return element;
                }
            }
            throw new NoSuchElementException("未找到元素，候选选择器：" + describe(locators));
        });
    }

    /**
     * 依次尝试一组选择器，返回第一个"可见"的元素，适合需要点击的按钮。
     */
    public static WebElement findFirstVisible(WebDriver driver, Duration timeout, By... locators) {
        Wait<WebDriver> wait = buildWait(driver, timeout);
        return wait.until(d -> {
            for (By by : locators) {
                for (WebElement element : d.findElements(by)) {
                    if (element.isDisplayed() && element.isEnabled()) {
                        return element;
                    }
                }
            }
            throw new NoSuchElementException("未找到可见元素，候选选择器：" + describe(locators));
        });
    }

    /**
     * 依次尝试一组选择器，返回第一个"可见且包含指定文案"的元素。
     */
    public static WebElement findByText(WebDriver driver, Duration timeout, String text, By... locators) {
        By textLocator = By.xpath("//*[contains(normalize-space(.),\"" + text + "\")]");
        By[] all = Arrays.copyOf(locators, locators.length + 1);
        all[locators.length] = textLocator;
        return findFirstVisible(driver, timeout, all);
    }

    /**
     * 判断一组选择器中是否存在任意一个元素（不等待）。
     */
    public static boolean exists(WebDriver driver, By... locators) {
        for (By by : locators) {
            if (!driver.findElements(by).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断一组选择器中是否存在任意一个"可见"元素（不等待）。
     */
    public static boolean existsVisible(WebDriver driver, By... locators) {
        for (By by : locators) {
            for (WebElement element : driver.findElements(by)) {
                if (element.isDisplayed()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 生成当前页面的诊断信息（URL、标题、前几个 input 的 type/placeholder/accept）。
     *
     * <p>元素找不到时把它打进日志，一眼就能区分到底是「未登录」「弹窗遮挡」
     * 还是「页面改版」，不用再靠猜。</p>
     */
    public static String describePage(WebDriver driver) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("url=").append(driver.getCurrentUrl());
        } catch (Exception ignored) {
            sb.append("url=未知");
        }
        try {
            sb.append(", title=").append(driver.getTitle());
        } catch (Exception ignored) {
            // 忽略
        }
        try {
            java.util.List<WebElement> inputs = driver.findElements(By.tagName("input"));
            sb.append(", inputCount=").append(inputs.size()).append(", inputs=[");
            for (int i = 0; i < Math.min(inputs.size(), 8); i++) {
                WebElement element = inputs.get(i);
                sb.append("{type=").append(element.getAttribute("type"))
                        .append(", placeholder=").append(element.getAttribute("placeholder"))
                        .append(", accept=").append(element.getAttribute("accept"))
                        .append(", displayed=").append(element.isDisplayed()).append("}");
            }
            sb.append("]");
            sb.append(", fileInput=").append(driver.findElements(By.cssSelector("input[type='file']")).size());
            sb.append(", contentEditable=").append(driver.findElements(By.xpath("//div[@contenteditable='true']")).size());
        } catch (Exception e) {
            sb.append(", 诊断信息获取失败:").append(e.getMessage());
        }
        return sb.toString();
    }

    private static Wait<WebDriver> buildWait(WebDriver driver, Duration timeout) {
        return new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class);
    }

    private static String describe(By... locators) {
        return Arrays.stream(locators).map(By::toString).collect(Collectors.joining(" | "));
    }
}

