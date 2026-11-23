package com.testforge.automation.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class Browser {

    public static final String BASE_URL = "https://www.saucedemo.com/";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private Browser() {
    }

    public static void start() {
        ChromeOptions options = new ChromeOptions();
        if (!"false".equalsIgnoreCase(System.getenv("TESTFORGE_HEADLESS"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080", "--disable-notifications");
        options.setExperimentalOption("prefs", Map.<String, Object>of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false));
        DRIVER.set(new ChromeDriver(options));
    }

    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                DRIVER.remove();
            }
        }
    }

    public static WebDriver driver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("The browser has not been started");
        }
        return driver;
    }

    public static void open(String url) {
        driver().get(url);
    }

    public static WebElement waitVisible(By locator) {
        return new WebDriverWait(driver(), TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public static void type(By locator, String text) {
        WebElement element = waitVisible(locator);
        element.clear();
        element.sendKeys(text);
    }

    public static void click(By locator) {
        new WebDriverWait(driver(), TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    public static String textOf(By locator) {
        return waitVisible(locator).getText();
    }

    public static List<String> textsOf(By locator) {
        waitVisible(locator);
        return driver().findElements(locator).stream().map(WebElement::getText).toList();
    }
}