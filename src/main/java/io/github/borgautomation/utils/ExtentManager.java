package io.github.borgautomation.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

import java.time.format.DateTimeFormatter;

public class ExtentManager {

    private static final String REPORT_DIR = "test-output/ExtentReports/";
    private static volatile ExtentReports extentReports;
    private static final ThreadLocal<ExtentTest> TEST = new ThreadLocal<>();

    private ExtentManager() {
    }

    // The ExtentReports instance/report file is intentionally one-per-JVM and shared across
    // all parallel scenario threads - only the ExtentTest node each thread writes to needs to
    // be thread-local, otherwise concurrent scenarios race on the same node instance.
    public static ExtentReports getInstance() {
        if (extentReports == null) {
            synchronized (ExtentManager.class) {
                if (extentReports == null) {
                    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                            .format(java.time.LocalDateTime.now());
                    ExtentSparkReporter sparkReporter =
                            new ExtentSparkReporter(REPORT_DIR + "Report_" + timestamp + ".html");
                    extentReports = new ExtentReports();
                    extentReports.attachReporter(sparkReporter);
                }
            }
        }
        return extentReports;
    }

    public static void setTest(ExtentTest test) {
        TEST.set(test);
    }

    public static ExtentTest getTest() {
        return TEST.get();
    }

    public static void removeTest() {
        TEST.remove();
    }
}
