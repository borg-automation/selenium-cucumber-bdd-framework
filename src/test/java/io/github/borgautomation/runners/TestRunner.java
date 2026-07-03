package io.github.borgautomation.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

// AnnotationTransformer is registered via the ServiceLoader mechanism
// (src/test/resources/META-INF/services/org.testng.ITestNGListener), not @Listeners here -
// @Listeners registers too late for IAnnotationTransformer to intercept this very class's own
// @Test annotations (annotation transformation happens during the initial scan, before a
// class-level @Listeners on that same class is processed). See PROGRESS.md.
@CucumberOptions(
        features = "src/test/resources/features",
        glue = "io.github.borgautomation.stepdefinitions",
        tags = "not @wip",
        plugin = {"pretty", "html:target/cucumber-reports/cucumber-report.html"}
)
public class TestRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
