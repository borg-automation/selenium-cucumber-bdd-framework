# Session Brief: Logging + ExtentReports + Screenshot-on-Failure + Retry Analyzer (Cucumber)

## Project coordinates
- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-cucumber-bdd-framework`

## Prerequisite
Sessions 1-2 complete: DriverFactory, ConfigReader, Hooks, page objects, feature files
with Scenario Outlines, TestRunner with parallel execution and tag filtering proven.

## Important context — hook points differ from the TestNG framework

The sibling project (`selenium-testng-framework`) wires reporting/retry through TestNG's
`ITestListener` and `IRetryAnalyzer`, applied via `testng.xml` or `@Listeners`. This
project runs through `AbstractTestNGCucumberTests`, so the natural hook points are
**Cucumber's own `@Before`/`@After` hooks in `Hooks.java`** for reporting (since Cucumber
owns the scenario lifecycle here, not raw TestNG test methods), plus Cucumber's
`ConcurrentEventListener`/plugin system for anything that needs to observe the whole run.
Retry is the one place TestNG's mechanism still applies directly, since
`AbstractTestNGCucumberTests` still runs scenarios as TestNG-visible units. Don't assume
the TestNG framework's listener classes can be copied over unchanged — the attachment
points are different even though the goal is identical.

## Scope

**In:** Log4j2, ExtentReports with thread-safe parallel logging tied to Cucumber's
scenario lifecycle, screenshot capture on failure embedded in the report, retry for
failed scenarios, retry-aware reporting.

**Out:** GitHub Actions, README polish — next session.

---

## 1. pom.xml additions

- `org.apache.logging.log4j:log4j-core` + `log4j-api` (latest 2.x)
- `com.aventstack:extentreports` (latest 5.x)

## 2. Folder structure additions

```
src/main/java/io/github/borgautomation/
  utils/
    ExtentManager.java
    ScreenshotUtil.java

src/test/java/io/github/borgautomation/
  stepdefinitions/
    Hooks.java   (already exists from Session 1 — extend, don't replace)
  runners/
    TestRunner.java   (already exists — add retry config)
  listeners/
    RetryAnalyzer.java

src/test/resources/
  log4j2.xml
```

## 3. Log4j2

Same setup as the TestNG framework: console + rolling file appender to
`logs/automation.log`, thread name in the pattern (still relevant here — Cucumber
scenarios run on TestNG's thread pool under the hood, so concurrent scenario logs need
the same thread-tagging to stay readable). `.gitignore` the `logs/` folder.

## 4. ExtentManager — same thread-safety requirement, different hook point

- Single `ExtentReports` instance, `ExtentTest` in `ThreadLocal<ExtentTest>` — identical
  reasoning to the TestNG framework (each concurrently-running scenario needs its own
  report node without cross-writing).
- Where it plugs in is different: create/close the `ExtentTest` node inside
  `Hooks.java`'s `@Before`/`@After` methods, using the `Scenario` object Cucumber injects
  (its `getName()` gives you the scenario name, including which Examples row if this is
  a Scenario Outline expansion — use this for the Extent node's title so parallel/
  data-driven runs are distinguishable in the report, same concern Session 2's
  acceptance criteria raised for Cucumber's own console output).

## 5. ScreenshotUtil

Identical approach to the TestNG framework: `TakesScreenshot` → `OutputType.BASE64` →
embed directly in the report, portable/self-contained.

## 6. Hooks.java — extend, don't replace

```java
@Before
public void setUp(Scenario scenario) {
  DriverFactory.setDriver(...);
  ExtentManager.createTest(scenario.getName());
  log.info("Starting scenario: {}", scenario.getName());
}

@After
public void tearDown(Scenario scenario) {
  if (scenario.isFailed()) {
    byte[] screenshot = ScreenshotUtil.captureBase64();
    scenario.attach(screenshot, "image/png", "Failure Screenshot");  // Cucumber's own
                                                                        // native attachment,
                                                                        // shows in Cucumber's
                                                                        // pretty/HTML output too
    ExtentManager.getTest().fail("Scenario failed", 
        MediaEntityBuilder.createScreenCaptureFromBase64String(
            Base64.getEncoder().encodeToString(screenshot)).build());
  } else {
    ExtentManager.getTest().pass("Scenario passed");
  }
  DriverFactory.quitDriver();
}
```

Note the screenshot gets attached to BOTH Cucumber's native scenario (via
`scenario.attach`, which shows up in Cucumber's own `pretty`/html plugin output) AND
ExtentReports — this dual-attachment is intentional, not redundant: it means the
screenshot is visible whichever report a client happens to open.

## 7. Retry — TestNG's IRetryAnalyzer still applies

- `RetryAnalyzer implements IRetryAnalyzer`, same as the TestNG framework: max retry
  count from `config.properties` (`retryCount`, default 2).
- Because scenarios run through `AbstractTestNGCucumberTests`'s `@DataProvider`
  mechanism rather than plain `@Test` methods, the retry analyzer needs to be wired via
  an `IAnnotationTransformer` applied to the `scenarios()` data-provider-backed test
  method — this is less standard than the TestNG framework's per-`@Test` application.
  If `IAnnotationTransformer` doesn't cleanly intercept Cucumber-TestNG's generated test
  methods (verify this — it's the biggest uncertainty in this session), the documented
  fallback is a `TestNG` `<listener>` + explicit `retryAnalyzer` set at the
  `AbstractTestNGCucumberTests` subclass level via `@Test(retryAnalyzer = ...)` override
  on `TestRunner`'s inherited test method, whichever proves to actually work — try the
  transformer first, fall back if needed, and document which one ended up being used and
  why in PROGRESS.md. This is a genuine open question for this integration, not a
  solved pattern — budget extra time for it.
- Log retry attempts the same way as the TestNG framework: visible in both Log4j2 output
  and the ExtentReport node, not silent.

---

## Acceptance criteria

- Run the full parallel suite. Confirm:
  - `logs/automation.log` shows correctly thread-tagged concurrent scenario logs.
  - ExtentReport shows one node per scenario (including each Examples-table row as its
    own distinctly named entry), no cross-thread bleed at thread-count ≥ 3.
  - Deliberately fail one scenario, confirm screenshot appears in BOTH Cucumber's native
    output (if using the `html`/`pretty` plugin with attachments) and the ExtentReport.
  - Deliberately make one scenario flaky (fails once, passes on retry), confirm the
    retry mechanism actually re-runs it and the report reflects the retry, not a silent
    pass or duplicate-looking entries.
- PROGRESS.md updated: which retry-wiring approach actually worked (transformer vs.
  explicit override), any Cucumber-Scenario-object quirks hit, screenshot of the
  ExtentReport for later README use.

## Ground rules

- Do not remove or bypass Cucumber's own `scenario.attach()` reporting in favor of only
  ExtentReports — both should coexist, per step 6.
- No `Thread.sleep` for demonstrating retry — use a genuine timing/wait mismatch.
- Keep GitHub Actions and README out of this session.
