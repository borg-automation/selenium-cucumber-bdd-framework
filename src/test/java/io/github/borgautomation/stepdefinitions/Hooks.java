package io.github.borgautomation.stepdefinitions;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.borgautomation.utils.ConfigReader;
import io.github.borgautomation.utils.DriverFactory;
import io.github.borgautomation.utils.ExtentManager;
import io.github.borgautomation.utils.ScreenshotUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;

public class Hooks {

    private static final Logger log = LogManager.getLogger(Hooks.class);

    @Before
    public void setUp(Scenario scenario) {
        DriverFactory.setDriver();
        DriverFactory.getDriver().get(ConfigReader.getInstance().getBaseUrl());
        // scenario.getName() is just the Scenario Outline's title - every Examples row shares
        // the same name, so without the line number all rows of an Outline would render as
        // identical, indistinguishable ExtentReport nodes. getLine() returns the Examples row's
        // own line for an Outline expansion (verified against cucumber-java 7.20.1 sources).
        String reportName = scenario.getName() + " (line " + scenario.getLine() + ")";
        ExtentManager.setTest(ExtentManager.getInstance().createTest(reportName));
        log.info("Starting scenario: {}", reportName);
    }

    @After
    public void tearDown(Scenario scenario) {
        String reportName = scenario.getName() + " (line " + scenario.getLine() + ")";
        ExtentTest test = ExtentManager.getTest();
        if (scenario.isFailed()) {
            String base64Screenshot = ScreenshotUtil.captureBase64();
            if (base64Screenshot != null) {
                // Attached to both reports from a single capture: Cucumber's own scenario.attach
                // (visible in Cucumber's pretty/html plugin output) and ExtentReports (decoded
                // from the same base64 string rather than re-capturing) - intentional redundancy,
                // not to be collapsed into just one.
                scenario.attach(Base64.getDecoder().decode(base64Screenshot), "image/png", "Failure Screenshot");
                if (test != null) {
                    test.fail("Scenario failed",
                            MediaEntityBuilder.createScreenCaptureFromBase64String(base64Screenshot).build());
                }
            } else if (test != null) {
                test.fail("Scenario failed");
            }
            log.error("Scenario failed: {}", reportName);
        } else {
            if (test != null) {
                test.pass("Scenario passed");
            }
            log.info("Scenario passed: {}", reportName);
        }
        // Deliberately not calling ExtentManager.removeTest() here: Cucumber's @After runs on
        // every attempt, retried or not, but TestNG's RetryAnalyzer.retry() runs afterwards on
        // this same thread and needs this failed attempt's node still live in the ThreadLocal
        // to log the "retrying" message onto it. The next setUp() (whether it's the retry or
        // the next scenario on this thread) overwrites the ThreadLocal via setTest() anyway, so
        // nothing is leaked by leaving the reference in place until then.
        DriverFactory.quitDriver();
    }

    // Runs once after every scenario in the JVM has finished (Cucumber's own whole-run hook,
    // per the brief's note that anything observing the whole run belongs on Cucumber's own
    // lifecycle here, not a TestNG suite-level listener) - flushes the single shared
    // ExtentReports instance to disk.
    @AfterAll
    public static void tearDownSuite() {
        ExtentManager.getInstance().flush();
    }
}
