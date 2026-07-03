# Progress Log

## Session 3 — Logging, ExtentReports, screenshot-on-failure, retry analyzer

Implemented per `claude/BRIEF_cucumber_reporting_retry.md`.

### Reused from the sibling TestNG framework, unchanged

`ExtentManager` (single `ExtentReports` instance, `ThreadLocal<ExtentTest>`), `ScreenshotUtil`
(`TakesScreenshot` → `OutputType.BASE64`), and `log4j2.xml` (console + rolling file appender to
`logs/automation.log`, `%t` thread name in the pattern, 10 MB rollover) were copied verbatim
from `selenium-testng-framework` — same thread-safety reasoning applies since Cucumber
scenarios still run on TestNG's thread pool under the hood. `logs/` and `test-output/` added
to `.gitignore`. `ConfigReader.getRetryCount()` (default 2, from `config.properties`'
`retryCount`) also copied as-is.

### Hook point is Cucumber's own `Hooks.java`, not a TestNG `ITestListener`

Per the brief, `ExtentTest` node creation/closure and Log4j2 logging live in Cucumber's own
`@Before`/`@After` (`Hooks.java`), keyed off the injected `Scenario` object — not TestNG's
`ITestListener`, which is the sibling project's (different) attachment point. On failure, the
screenshot is captured **once** as a base64 string and attached to both reports: decoded to
bytes for Cucumber's native `scenario.attach(...)` (visible in Cucumber's own `pretty`/html
output) and passed to `MediaEntityBuilder.createScreenCaptureFromBase64String(...)` for
ExtentReports. This is intentionally two attachments, not one — confirmed both appear
(`grep -c "data:image/png;base64"` on the report, and "Embedding Failure Screenshot" in
Cucumber's console output).

### Cucumber-Scenario-object quirk: `scenario.getName()` does not disambiguate Examples rows

`scenario.getName()` returns the *Scenario Outline's title*, not the resolved row — every row
of an Outline reports the identical name (confirmed empirically: all rows of "Adding various
products..." showed as identical ExtentReport node names on the first attempt). This is
different from the TestNG framework's data providers, where each `@Test` invocation's first
parameter's `toString()` naturally differs per row. Fixed by appending the Examples row's own
line number: `scenario.getName() + " (line " + scenario.getLine() + ")"` — `Scenario.getLine()`
is documented (cucumber-java 7.20.1 sources) to return the Examples row's line, not the
`Scenario Outline:` line, for an Outline expansion. Applied the same fix to `RetryAnalyzer`'s
logging, using `PickleWrapper.getPickle().getLine()` (Cucumber-TestNG's own equivalent) since
its `params[0].toString()` similarly collapses to the same quoted name for every row of an
Outline.

### Retry wiring: `IAnnotationTransformer` via `@Listeners` did not work; ServiceLoader did

This was the flagged open question. Two things had to be resolved:

1. **`@Listeners(AnnotationTransformer.class)` on `TestRunner` silently did nothing** — ran a
   full Chrome-parallel suite (which reliably reproduces the known cart-badge race from
   Sessions 1–2) with `retryCount=2` and zero `RetryAnalyzer` log lines appeared. Root cause:
   `IAnnotationTransformer` needs to be known before TestNG reads the class's own `@Test`
   annotations, and a `@Listeners` declared on that same class is discovered too late in that
   pass — a chicken-and-egg problem specific to annotation transformers (ordinary listeners
   like `ITestListener` don't have this issue since they only fire at runtime). This project
   has no `testng.xml`, so the usual "declare it in the suite file" fix wasn't available.
2. **Fix: register `AnnotationTransformer` via the `ServiceLoader` mechanism** —
   `src/test/resources/META-INF/services/org.testng.ITestNGListener` containing
   `io.github.borgautomation.listeners.AnnotationTransformer`. TestNG picks this up at JVM
   startup, before any annotation scanning, so it's early enough. Rerunning the same
   Chrome-parallel suite after this fix showed `RetryAnalyzer` firing correctly, with a
   **distinct instance per Examples row** (verified via `System.identityHashCode(this)` in the
   log — four different cart-badge rows produced four different instance IDs), i.e. TestNG
   clones the underlying `ITestNGMethod` per `@DataProvider(parallel = true)` row, so retry
   state does not bleed between rows. The documented fallback (`@Test(retryAnalyzer = ...)`
   override on `TestRunner.runScenario`) was not needed in the end.

A second bug surfaced once retries were actually firing: `Hooks.tearDown()` unconditionally
called `ExtentManager.removeTest()`, but that hook runs on **every** attempt (Cucumber has no
concept of "this attempt will be retried"), so by the time TestNG's `RetryAnalyzer.retry()` ran
afterwards on the same thread, the ThreadLocal node was already gone and the "retrying" log
message was silently dropped from the report (confirmed: `grep -c "Retrying"` on the report was
0 before the fix). Fix: don't call `removeTest()` in `tearDown()` at all — the next `setUp()`
(whether it's the retry itself or the next scenario on that thread) overwrites the ThreadLocal
via `setTest()` regardless, so nothing leaks. After the fix, `grep -c "Retrying"` on the report
was non-zero and matched the number of retried attempts in the log.

### Verification

- Serial (Firefox, `dataproviderthreadcount=1`): all 10 scenarios pass; `logs/automation.log`
  and one `ExtentReport_*.html` node per scenario, correctly named with line numbers.
- Parallel (Chrome, default threadcount 3), run three times: reliably reproduced the
  pre-existing, load-sensitive cart-badge race documented in Sessions 1–2 — this served as the
  "genuine timing/wait mismatch" required by the ground rules (no `Thread.sleep` needed; the
  framework already has an authentic flaky scenario). Observed: most retried rows failed once
  then passed on retry (confirmed in both `logs/automation.log` and the ExtentReport, each
  showing the failed attempt's node with the "Retrying" warning followed by a separate passing
  node for the same line number); one row twice exhausted both retries and surfaced as a final
  `[ERROR]` — its ExtentReport node shows `Status.WARNING` retry log entries followed by a
  final `fail()` with an embedded screenshot, and the same screenshot bytes were also attached
  to Cucumber's own output. No cross-thread bleed observed at threadcount 3 (each
  `TestNG-PoolService-N` thread's log lines and report nodes stayed on their own scenario).
- `logs/automation.log` confirmed thread-tagged (`[TestNG-PoolService-1/2/3]` interleaved
  correctly across concurrent scenarios).

### Deviations from the brief

- Brief's example `Hooks.tearDown()` code captures the screenshot as raw bytes then
  re-encodes to base64 for ExtentReports; implemented instead as a single
  `ScreenshotUtil.captureBase64()` call, decoding to bytes only for Cucumber's `attach()` —
  same two-attachment outcome, one fewer `TakesScreenshot` round trip.
- Added `Hooks.tearDownSuite()` (`@AfterAll`, Cucumber's own whole-run hook) to flush the
  shared `ExtentReports` instance once after all scenarios finish in the JVM — not explicitly
  in the brief's Hooks.java snippet, but required for the report to actually get written to
  disk, and consistent with the brief's own suggestion to use "Cucumber's
  `ConcurrentEventListener`/plugin system for anything that needs to observe the whole run"
  (an `@AfterAll` static hook is the simpler, equivalent tool for this specific need).

## Session 2 — Data-driven scenarios

Implemented per `claude/BRIEF_cucumber_data_driven.md`. Note on numbering: the brief calls
this "Session 3" if tracked sequentially against the sibling `selenium-testng-framework`'s
own session count, but within this repo's own history it's Session 2 (this repo's Session 1
was the core scaffold below). This log keeps this repo's own numbering.

### Scenario Outline is primary; external-file DataTable is secondary/demonstrative

Per the brief's explicit guidance: Gherkin's native `Scenario Outline` + `Examples` is the
idiomatic, expected data-driven mechanism for a Cucumber framework, and is used for both
new data-driven cases below. The external-JSON approach (step 3, optional) is implemented
too, but only as a single demonstration scenario proving the capability exists for the rare
case where a dataset genuinely needs to live outside the feature file — it is deliberately
not the default pattern and does not replace the Examples tables.

- **`login.feature`** — the single login scenario became a `Scenario Outline` with two
  `Examples` blocks: "Valid logins" (`standard_user`, `problem_user` — no extra tag, so
  only feature-level `@smoke` applies) and "Invalid logins" (`locked_out_user`,
  `invalid_user` — tagged `@regression` directly above that `Examples:` line, not above
  `Scenario Outline:`). This demonstrates row-group-level tagging correctly: `@regression`
  applies only to the two invalid-login rows, not all four. Confirmed via
  `-Dcucumber.filter.tags="@smoke"` (4 scenarios: the Outline expands per Examples row) vs
  `-Dcucumber.filter.tags="@regression"` (includes only the 2 invalid-login rows from this
  feature, alongside inventory.feature and the bulk-login feature).
- The old two-step `Then` design (`I should see the inventory page` /
  `I should see an error message {string}`) was collapsed into a single
  `Then I should see "<expectedResult>"` step (`LoginSteps.iShouldSee`) that branches on an
  `"error: "` prefix — asserts the error message if present, otherwise asserts the inventory
  page loaded. This matches the brief's explicit guidance to avoid two near-duplicate steps.
- **`inventory.feature`** — added a second scenario, a `Scenario Outline` varying which
  product is added to the cart (`add-to-cart-sauce-labs-backpack` /
  `-bike-light` / `-bolt-t-shirt` / `-fleece-jacket`, each asserting cart badge count `"1"`
  since each row's `Background` is a fresh login). Chose "multiple products added to cart"
  over checkout-form-validation because Session 1 only built `InventoryPage`, not a
  checkout page object — reusing the existing page object avoids adding new page-object
  logic for this session, per the brief's step 2 guidance. No new step definitions were
  needed; the Outline reuses `InventorySteps`' existing two steps.
- Total scenario count after both Outlines: 10 (was 3 in Session 1).

### Optional step 3 implemented: external JSON dataset

Added one demonstration scenario, `login-external-data.feature`'s
"Bulk login validation from external dataset", tagged `@regression` (not `@smoke`, to keep
the smoke suite fast). Reused the sibling `selenium-testng-framework`'s
`login-users.json` shape/content and Jackson (`jackson-databind` 2.19.0, test scope) rather
than inventing a new schema. `LoginTestData` (test-scope POJO, plain public fields, no
annotations needed — Jackson binds by field name) lives under
`src/test/java/.../models/`, matching the sibling's own placement. `BulkLoginSteps` reads
`testdata/login-users.json` from the classpath and loops through each row doing a full
login + assertion — this is a deliberate, noted exception to the "one page-object call per
step" thinness rule from Session 1's ground rules: a bulk/dataset-driven step is inherently
a loop over several page-object calls, and the brief explicitly frames this step as the
exception, not the pattern to generalize.

**Bug found and fixed:** the first version of this step reused one browser session across
all four JSON rows (`driver.get(baseUrl)` between rows, no fresh driver). This reliably
broke on the second row: SauceDemo's SPA left stale state after a first successful login
and subsequent same-tab renavigation to `/`, silently failing to mount its error banner on
the next login attempt — confirmed with temporary diagnostics (URL/page-source checks
added then removed) showing `data-test="error"` never appeared in the DOM at all after the
second attempt's click, even after a 3s settle. This is a different failure mode from
Session 1's documented Chrome cart-badge parallel race (that one is timing/contention
under load; this one was 100%-reproducible in complete isolation, on a single thread, with
no other scenarios running). The fix: give each JSON row its own fresh driver instance
(`DriverFactory.quitDriver()` + `DriverFactory.setDriver()` before each row) instead of
reusing one session across repeated logins — verified reliable across multiple isolated
runs after the fix. This is a heavier per-row cost (a full browser relaunch per JSON row)
but correctness took priority over speed for a demonstration scenario, and it mirrors how
every other scenario in this suite already gets its own fresh driver per Cucumber scenario
via `Hooks`.

### Verification performed

- `mvn test -Ddataproviderthreadcount=1`: all 10 scenarios pass serially (confirmed clean
  runs after the `BulkLoginSteps` fix above).
- `-Dcucumber.filter.tags="@smoke"`: 4 scenarios (the login Outline's full expansion),
  0 failures.
- `-Dcucumber.filter.tags="@regression"`: 8 scenarios (inventory.feature's 5, the bulk-login
  feature's 1, login.feature's 2 invalid-login rows), confirming Examples-block-level
  tagging works as intended.
- `mvn test -Dbrowser=firefox` (parallel, default `dataproviderthreadcount=3`): all 10
  scenarios pass, 0 failures, exit code 0.
- `mvn test` (parallel, Chrome, default thread count 3): intermittent cart-badge
  `TimeoutException` failures recur (2-3 out of 10 scenarios per run, inconsistent between
  runs) — this is the same Chrome-parallel JS-render-under-load race documented in Session
  1's PROGRESS.md, now simply more visible because there are more cart-badge-asserting
  scenarios post-expansion (5 vs. Session 1's 1). One serial (`dataproviderthreadcount=1`)
  Chrome run also intermittently hit the same failure once, on a fleece-jacket row -
  consistent with Session 1's finding that this is a load-sensitive JS re-render race, not a
  logic bug, and reinforces that more total browser launches in one JVM run (even serial)
  sustain enough machine load to occasionally trigger it. Not worked around here (retry
  logic remains out of scope); still recommend Session 2/3's retry analyzer, or defaulting
  to Firefox for parallel/CI runs, as previously noted.

### Deviations from the brief

- Session renumbering: brief refers to this work as "Session 3" in the two-repo program's
  overall sequence; this repo's own `PROGRESS.md` keeps calling it "Session 2" since it's
  the second session logged in this specific repo (see note above).
- `BulkLoginSteps`'s per-row fresh-driver approach is heavier than the brief's suggested
  "reuse session, loop through assertions" framing implied — necessary due to the stale-SPA
  bug found above; noted here rather than silently deviating.

## Session 1 — Cucumber core scaffold

Implemented per `claude/BRIEF_cucumber_core_scaffold.md`: Maven build (Selenium 4.33.0,
TestNG 7.11.0, Cucumber 7.20.1 `cucumber-java` + `cucumber-testng`, WebDriverManager
6.1.0), `DriverFactory` (ThreadLocal `WebDriver`, no headless flag yet — deferred to
Session 2 same as the sibling project), `ConfigReader` (singleton, `-Dbrowser` override),
`BasePage`, `LoginPage`/`InventoryPage` (plain `By` locators, adapted from
`selenium-testng-framework`'s page objects but written fresh for this repo), Cucumber
`Hooks` (`@Before`/`@After` replacing TestNG's `BaseTest` role), `login.feature` /
`inventory.feature`, `LoginSteps`/`InventorySteps` (thin, one page-object call per step,
no direct Selenium calls), and `TestRunner` bridging Cucumber to TestNG via
`AbstractTestNGCucumberTests`.

`mvn` is not on this machine's shell `PATH` — verification below was run using IntelliJ's
bundled Maven (`...\plugins\maven\lib\maven3\bin\mvn`) with `JAVA_HOME` pointed at
`C:\Program Files\Java\jdk-21.0.11`.

### Parallelism mechanism: DataProvider-based, not testng.xml-based

This is the key structural difference from the TestNG framework's Session 1. There is no
`testng.xml` in this repo — `TestRunner` overrides `scenarios()` with
`@DataProvider(parallel = true)`, and `surefire-plugin` is pointed directly at
`TestRunner.class` (via `<includes>`) rather than at a suite XML file. Thread count is
controlled by `dataproviderthreadcount`, set to `3` in `pom.xml`'s surefire configuration
for consistency with the sibling project's `thread-count="3"` choice, overridable via
`-Ddataproviderthreadcount=N`.

### Feature-file / step-definition structure

- `login.feature`: `@smoke` feature tag, two scenarios (valid login, locked-out user),
  the second additionally tagged `@regression`.
- `inventory.feature`: `@regression` feature tag, one scenario (add item to cart, assert
  cart badge count) with its own `Background:` login — kept independent for
  parallel-safety, no shared login state across features/scenarios.
- `LoginPage` gained an `isLoaded()` method (waiting on the username field) beyond what
  the sibling `LoginPage` has, so `Given I am on the SauceDemo login page` has a real
  assertion to make — `Hooks.setUp()` already navigates to `baseUrl` before any step
  runs, so this step confirms that navigation landed correctly rather than navigating
  itself.
- Step definitions construct a fresh `LoginPage`/`InventoryPage` per step
  (`new LoginPage(DriverFactory.getDriver())`) rather than sharing page-object instances
  across step classes. Page objects are stateless wrappers over the ThreadLocal driver, so
  this avoids needing a Cucumber dependency-injection module (PicoContainer/Spring/etc.)
  just to pass page-object references between `LoginSteps` and `InventorySteps`.
- `Then` steps use `org.testng.Assert`, not JUnit assertions, per the brief.

### Bug found and fixed during verification: locked-out error message text

The brief's `login.feature` copy asserted the error message as
`"Sorry, this user has been locked out."`. The real SauceDemo error element renders
`"Epic sadface: Sorry, this user has been locked out."` — confirmed against the live site,
not assumed. Updated the scenario's expected string to match; this is a one-line feature
file fix, no step-definition or page-object change needed.

### Verification performed

- **Tag filtering** — confirmed both directions:
  - `-Dcucumber.filter.tags="@smoke"`: 2 scenarios ran (both `login.feature` scenarios,
    since the feature-level `@smoke` tag applies to all scenarios in the file), 0
    failures.
  - `-Dcucumber.filter.tags="@regression"`: 2 scenarios ran (locked-out login +
    inventory add-to-cart), 0 failures.
- **`-Dbrowser=firefox`** — Firefox was not installed on this dev machine during initial
  verification (see the deviation this caused, below); it has since been installed
  (`C:\Program Files\Mozilla Firefox`) and the full suite re-verified end-to-end on
  Firefox: serial 3/3 pass (28.65s), and **parallel 3/3 pass across three consecutive
  runs** (26.85s / 27.73s / 27.93s) — see the parallelism findings below for why Firefox
  is consistently reliable here where Chrome is not.
- **Parallel execution / thread dispatch** — confirmed genuinely concurrent, not just
  "distinct thread IDs assigned by a pool that happens to run tasks one at a time." Two
  rounds of temporary diagnostic logging were used, then removed:
  1. `System.out.println("[parallel-proof] thread=" + Thread.currentThread().getId())` in
     `LoginSteps.iAmOnTheSauceDemoLoginPage()` — showed 3 distinct thread IDs (e.g.
     `31`/`32`/`33`) across every parallel run, each with its own Selenium `Session ID`.
  2. A follow-up round added a timestamp print at the very start of `Hooks.setUp()`
     (before any driver work) for all 3 threads. Result: **all 3 threads' `before-hook-start`
     timestamps were identical to the millisecond** (e.g. `1783018512565` for all three in
     one Firefox run, `1783018573287`/`88` in one Chrome run) — TestNG's
     `@DataProvider(parallel = true)` dispatch is genuinely simultaneous, not staggered.
- **Step definitions contain no direct Selenium calls** — every step method's body is a
  single call into `LoginPage`/`InventoryPage`; `WebDriver`/`By` never appear outside
  `src/main/java/.../pages/`.

### Root cause found: Chrome fails reliably under parallel execution, Firefox does not — and why

Serial run (`dataproviderthreadcount=1`), all 3 scenarios, either browser: **3/3 pass**
every time (Chrome 9.43s, Firefox 28.65s — Firefox is simply slower to launch).

Parallel run (`dataproviderthreadcount=3`, the default):
- **Chrome: failed on 6 consecutive attempts**, always the same way and always the same
  scenario — `Adding an item to the cart updates the cart badge count` throws
  `TimeoutException: ... waiting for visibility of element located by
  By.className: shopping_cart_badge`, after the add-to-cart click itself had already
  succeeded. Total elapsed each time was ~18–22s (slower than serial), because of this
  repeated failure eating its full 10s wait.
- **Firefox: passed 3/3 on 3 consecutive attempts**, ~27–31s total.

The `before-hook-start` timestamps (see above) plus per-scenario `Given`-step timestamps
explain the difference. In the diagnosed Firefox run, the 3 scenarios' `Given` steps
(i.e., "browser finished launching + navigated") landed ~7 seconds apart from each other,
because `DriverFactory.setDriver()` serializes the actual
`new ChromeDriver(...)`/`new FirefoxDriver(...)` call inside
`synchronized (DriverFactory.class)` (this exists to avoid the `DriverService`
port-allocation TOCTOU race the sibling project documented in its own Session 1), and
Firefox simply takes ~7s per instance to launch — so the three launches are almost fully
serialized by that lock, leaving little time where multiple *interactive* (JS-executing)
browser sessions actually overlap. In the diagnosed Chrome run, the same three `Given`
steps landed only ~2–4 seconds apart — Chrome launches much faster, clears the lock
sooner, and so all three sessions spend meaningfully more overlapping wall-clock time
actually running JS-heavy steps concurrently. That overlap is what starves the CPU during
exactly the moment SauceDemo's add-to-cart handler needs to re-render the cart badge,
consistently pushing it past the 10-second explicit wait. Two additional checks ruled out
a locator/logic bug specifically: the inventory scenario passes cleanly and quickly (4.7s)
when run alone in serial, and the failure is 100% reproducible on Chrome/parallel and 0%
reproducible on Chrome/serial or Firefox/parallel across every attempt made.

**Conclusion:** proof-of-parallelism requirement (genuinely concurrent thread dispatch,
concurrent browser sessions) is satisfied and precisely explained, not just asserted.
"Parallel demonstrably faster than serial" is **not** met by either browser on this
specific dev machine, because `DriverFactory`'s construction-serializing lock (itself
necessary to avoid a real race condition) puts a floor under total runtime close to
serial's — this is an inherent tradeoff of that fix, not a bug. Separately, **Chrome
specifically cannot run this suite's inventory scenario reliably in parallel on this
machine**; Firefox can. This mirrors the sibling TestNG framework's own documented
Chrome-parallel flakiness on this exact machine (also only fully resolved there by its
Session 2 retry analyzer), but this session pinned down the actual mechanism (launch-speed
asymmetry interacting with the construction lock) rather than attributing it to unspecified
"resource contention." Retry logic remains out of scope per the brief's "Out" list; not
worked around here. Recommend Session 2's retry analyzer (or a documented default-browser
change to Firefox for parallel CI runs) as the fix, now with a concrete root cause to
validate against.

### Deviations from the brief

- `login.feature`'s locked-out error message text corrected to match the live site (see
  above) — the only feature-file content that differed from the brief's exact copy.
- `ConfigReader` omits `isHeadless()`/`retryCount` (headless and retry are both Session 2
  concerns per the brief's own deferral).
- No logging framework wired into `DriverFactory`/page objects yet (Log4j2 is explicitly
  Session 2 scope) — no `log.info(...)` calls like the sibling project has.
- `LoginPage` gained an `isLoaded()` method not present in the sibling's `LoginPage`, added
  so the `Given` login-page step has a real assertion (see above).
- Firefox was not installed on this dev machine during the first verification pass
  (`-Dbrowser=firefox` failed with `SessionNotCreatedException: ... unable to find binary
  in default location`, an environment gap, not a code defect — the driver-selection code
  path itself was already confirmed correct: right `FirefoxDriver` class, right
  capabilities, `geckodriver` resolved cleanly by WebDriverManager). Firefox was installed
  afterward and the full suite re-verified on it; see above.
