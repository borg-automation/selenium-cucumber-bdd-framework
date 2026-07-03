package io.github.borgautomation.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import io.cucumber.testng.PickleWrapper;
import io.github.borgautomation.utils.ConfigReader;
import io.github.borgautomation.utils.ExtentManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LogManager.getLogger(RetryAnalyzer.class);

    private int retryCount = 0;
    private final int maxRetryCount = ConfigReader.getInstance().getRetryCount();

    @Override
    public boolean retry(ITestResult result) {
        String scenarioName = describeScenario(result);
        log.info("RetryAnalyzer instance {} evaluating '{}' after attempt {} (max retries {})",
                System.identityHashCode(this), scenarioName, retryCount + 1, maxRetryCount);

        // retry() runs on the same thread that just ran the scenario, so the ThreadLocal
        // ExtentTest node for this scenario is still the active one - this is the only place a
        // failed-but-retried attempt is visible at all, since TestNG suppresses
        // onTestFailure/onTestSuccess for every attempt except the final one.
        ExtentTest test = ExtentManager.getTest();

        if (retryCount < maxRetryCount) {
            retryCount++;
            String message = String.format("Attempt %d failed (%s). Retrying - attempt %d of %d.",
                    retryCount, describeFailure(result), retryCount, maxRetryCount);
            log.warn(message);
            if (test != null) {
                test.log(Status.WARNING, message);
            }
            return true;
        }

        String message = String.format("All %d retries exhausted for '%s'.", maxRetryCount, scenarioName);
        log.warn(message);
        if (test != null) {
            test.log(Status.WARNING, message);
        }
        return false;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    private static String describeFailure(ITestResult result) {
        Throwable throwable = result.getThrowable();
        return throwable != null ? throwable.getClass().getSimpleName() : "unknown reason";
    }

    // Cucumber-TestNG's generated @Test method (runScenario) is shared by every scenario, so
    // the method name alone can't tell scenarios apart. Its first parameter is a PickleWrapper;
    // every row of a Scenario Outline shares the same pickle name, so the line number (the
    // Examples row's own line) is appended to actually distinguish which row this retry
    // decision belongs to - mirrors the same fix applied to the ExtentReport node title in
    // Hooks.java.
    private static String describeScenario(ITestResult result) {
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] instanceof PickleWrapper pickleWrapper) {
            return pickleWrapper.getPickle().getName() + " (line " + pickleWrapper.getPickle().getLine() + ")";
        }
        return result.getMethod().getMethodName();
    }
}
