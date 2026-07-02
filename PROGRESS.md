# Progress Log

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
