package io.github.borgautomation.utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DriverFactory {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();
    private static final Set<String> PREPARED_BROWSERS = ConcurrentHashMap.newKeySet();

    private DriverFactory() {
    }

    public static void setDriver() {
        String browser = ConfigReader.getInstance().getBrowser().toLowerCase();
        prepareDriverBinary(browser);

        // BiDi is opt-in but each ChromeDriver still advertises a CDP websocket that shares
        // a JVM-wide cleanup executor (see SeleniumHQ/selenium#14269) - one thread's quit()
        // can shut that executor down mid-request for every other parallel session. Setting
        // webSocketUrl=false stops the websocket from being opened at all.
        WebDriver webDriver;
        // DriverService picks a free local port by probing then releasing it, then hands that
        // port to the chromedriver/geckodriver process it launches. Two threads probing at the
        // same instant can be handed the same "free" port (OS TOCTOU race), so two unrelated
        // driver instances end up talking to one physical browser. Serializing only the
        // construction call (not the rest of the scenario) keeps port allocation non-overlapping
        // while still running the actual browser sessions fully in parallel.
        synchronized (DriverFactory.class) {
            webDriver = switch (browser) {
                case "chrome" -> {
                    ChromeOptions options = new ChromeOptions();
                    options.setCapability("webSocketUrl", false);
                    yield new ChromeDriver(options);
                }
                case "firefox" -> {
                    FirefoxOptions options = new FirefoxOptions();
                    options.setCapability("webSocketUrl", false);
                    yield new FirefoxDriver(options);
                }
                default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
            };
        }

        DRIVER.set(webDriver);
    }

    // WebDriverManager.setup() resolves and writes the driver binary to a shared cache;
    // calling it concurrently from every parallel thread races on that cache and can hand
    // back a corrupted binary. Resolve each browser's driver exactly once per JVM instead.
    private static void prepareDriverBinary(String browser) {
        if (PREPARED_BROWSERS.contains(browser)) {
            return;
        }
        synchronized (DriverFactory.class) {
            if (PREPARED_BROWSERS.contains(browser)) {
                return;
            }
            switch (browser) {
                case "chrome" -> WebDriverManager.chromedriver().setup();
                case "firefox" -> WebDriverManager.firefoxdriver().setup();
                default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
            }
            PREPARED_BROWSERS.add(browser);
        }
    }

    public static WebDriver getDriver() {
        return DRIVER.get();
    }

    public static void quitDriver() {
        WebDriver webDriver = DRIVER.get();
        if (webDriver != null) {
            webDriver.quit();
            DRIVER.remove();
        }
    }
}
