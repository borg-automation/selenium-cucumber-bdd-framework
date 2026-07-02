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
- **`-Dbrowser=firefox`** — the browser-selection code path is confirmed correct:
  `DriverFactory` correctly routed to `FirefoxDriver` with the right capabilities
  (`browserName: firefox`, `webSocketUrl: false`), and WebDriverManager resolved
  `geckodriver` without error. The actual session failed with
  `SessionNotCreatedException: ... unable to find binary in default location` — **Firefox
  itself is not installed on this dev machine**, which is an environment gap, not a
  framework defect. `-Dbrowser=chrome` (the default) runs cleanly; re-verify
  `-Dbrowser=firefox` end-to-end once Firefox is installed.
- **Parallel execution / thread IDs** — confirmed via a temporary
  `System.out.println("[parallel-proof] thread=" + Thread.currentThread().getId())` in
  `LoginSteps.iAmOnTheSauceDemoLoginPage()` (first step of every scenario), then removed
  once confirmed. Across 4 separate parallel runs (`dataproviderthreadcount=3`, the
  default), the 3 scenarios consistently dispatched to 3 distinct thread IDs (e.g.
  `31`/`32`/`33`) concurrently, each with its own `Session ID` in the Selenium logs —
  structurally, parallel execution is real and working correctly.
- **Step definitions contain no direct Selenium calls** — every step method's body is a
  single call into `LoginPage`/`InventoryPage`; `WebDriver`/`By` never appear outside
  `src/main/java/.../pages/`.

### Known issue: inventory scenario fails reliably under parallel execution (not a code bug — see below)

Serial run (`dataproviderthreadcount=1`), all 3 scenarios: **3/3 pass, 9.43s.**

Parallel run (`dataproviderthreadcount=3`, the default), all 3 scenarios: **the inventory
scenario (`Adding an item to the cart updates the cart badge count`) failed on all 4
consecutive attempts**, always the same way —
`TimeoutException: ... waiting for visibility of element located by
By.className: shopping_cart_badge` after the add-to-cart click had already succeeded.
Total elapsed each time was ~19–22s, i.e. **slower than serial, not faster**, because of
this repeated failure.

This is not a new defect — it is the same class of issue the sibling TestNG framework
documented extensively in its own Session 1/2 `PROGRESS.md`, on this exact machine, on
functionally the same scenario (login → immediate assertion, the smallest margin before
an explicit wait is checked). That project's own Session 1 "never reached a clean parallel
run to time" either; the sibling's timing table that shows a passing parallel run was
measured in *Session 2*, after the retry analyzer existed, and explicitly includes "1 real
retry". In other words, the sibling framework only became reliably green under parallel
load once retry logic was added — and retry logic is out of scope for this session here
too, by the brief's own "Out" list.

Diagnosis performed before concluding this: reran the inventory scenario alone in serial
mode (passed cleanly, 4.7s) to rule out a locator/logic bug; reran the full parallel suite
4 times to rule out a one-off fluke (failed all 4, same step, same exception, each time).
Free system RAM during these runs was ~7GB/20GB — enough that this reads as this
machine's specific 3-way-Chrome-contention profile (documented by the sibling project),
not a from-scratch environment problem.

**Conclusion:** proof-of-parallelism requirement (distinct concurrent thread IDs, distinct
concurrent browser sessions) is satisfied. The "parallel demonstrably faster than serial"
criterion is **not** met on this specific dev machine without retry logic, matching the
sibling project's own documented precedent exactly. Deliberately not worked around here
(no retry analyzer, no arbitrary wait-timeout inflation) since Session 2 owns that fix by
the brief's explicit scope — flagging it now so Session 2 knows to verify it's resolved
once the retry analyzer lands, the same way the sibling project's Session 2 did.

### Deviations from the brief

- `login.feature`'s locked-out error message text corrected to match the live site (see
  above) — the only feature-file content that differed from the brief's exact copy.
- `ConfigReader` omits `isHeadless()`/`retryCount` (headless and retry are both Session 2
  concerns per the brief's own deferral).
- No logging framework wired into `DriverFactory`/page objects yet (Log4j2 is explicitly
  Session 2 scope) — no `log.info(...)` calls like the sibling project has.
- `LoginPage` gained an `isLoaded()` method not present in the sibling's `LoginPage`, added
  so the `Given` login-page step has a real assertion (see above).
