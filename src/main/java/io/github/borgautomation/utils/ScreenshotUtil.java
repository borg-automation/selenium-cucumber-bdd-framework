package io.github.borgautomation.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

public class ScreenshotUtil {

    private ScreenshotUtil() {
    }

    // Base64 (not FILE) so the screenshot is embedded directly in the HTML report - the
    // report stays a single self-contained file with no separate image assets to lose.
    public static String captureBase64() {
        WebDriver driver = DriverFactory.getDriver();
        if (driver == null) {
            return null;
        }
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
    }
}
