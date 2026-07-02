# Session Brief: Cucumber BDD Framework — Core Scaffold (Session 1 of 2)

## Project coordinates
- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-cucumber-bdd-framework`
- Java package root: `io/github/borgautomation/`
- Java target: 21
- Repo: `github.com/borg-automation/selenium-cucumber-bdd-framework`

## Context
This is a sibling project to `selenium-testng-framework` (already built, working,
reference it if useful for pattern consistency but do not copy files directly — write
fresh code adapted to this project's Cucumber structure). Same target site: SauceDemo.
Same core patterns (ThreadLocal driver, config-driven browser/headless) apply, layered
under Cucumber's Gherkin/step-definition structure instead of plain `@Test` methods.

Goal: a working, demoable BDD framework by end of session — Maven build, Cucumber +
TestNG bridge, Page Object Model, feature files + step definitions for login and
inventory flows, tag-based execution, parallel execution proven. Reporting
(ExtentReports/Log4j2/retry) and CI come in Session 2 — keep this session to core +
Cucumber + parallel proof only.

## Scope

**In:** pom.xml, folder structure, DriverFactory, ConfigReader, BasePage, page objects
(Login, Inventory — same as the TestNG framework), feature files in Gherkin, step
definitions, Cucumber-TestNG runner, tag-based execution (`@smoke`, `@regression`),
parallel execution proof.

**Out:** ExtentReports, Log4j2, screenshot-on-failure, retry analyzer, data providers,
GitHub Actions — Session 2.

---

## 1. pom.xml dependencies

- `selenium-java` (latest 4.x)
- `testng` (latest 7.x)
- `io.cucumber:cucumber-java` (latest 7.x)
- `io.cucumber:cucumber-testng` (latest 7.x — this is the bridge that lets Cucumber
  scenarios run through TestNG's runner, which is what makes parallel execution and
  Session 2's reporting/retry infrastructure work the same way it did in the sibling
  project)
- `io.github.bonigarcia:webdrivermanager` (latest)
- `maven-compiler-plugin` source/target 21
- `maven-surefire-plugin` configured to run the TestNG runner class (not testng.xml
  directly this time — Cucumber-TestNG parallelism is driven by `@CucumberOptions` +
  the runner's TestNG `@Factory`/`@DataProvider` mechanism, not a `parallel=` attribute
  in testng.xml the way Session 1 of the other framework worked; get this right, it's
  the part most likely to differ from assumptions carried over from the other project)

## 2. Folder structure

```
src/main/java/io/github/borgautomation/
  pages/
    BasePage.java
    LoginPage.java
    InventoryPage.java
  utils/
    DriverFactory.java
    ConfigReader.java

src/test/java/io/github/borgautomation/
  stepdefinitions/
    Hooks.java
    LoginSteps.java
    InventorySteps.java
  runners/
    TestRunner.java

src/test/resources/
  features/
    login.feature
    inventory.feature
  config.properties
```

## 3. DriverFactory + ConfigReader

Same design as the TestNG framework: `ThreadLocal<WebDriver>`, browser selection via
config with `-Dbrowser=` override, WebDriverManager for binaries. No headless flag yet
(Session 2, same deferral reasoning as before — CI is where it's needed).

## 4. Hooks — Cucumber's equivalent of BaseTest

- `@Before` (Cucumber annotation, not JUnit/TestNG): `DriverFactory.setDriver(...)`,
  navigate to `baseUrl`.
- `@After`: `DriverFactory.quitDriver()`.
- This replaces `BaseTest`'s `@BeforeMethod`/`@AfterMethod` role — do not also add
  TestNG's `@BeforeMethod` anywhere, Cucumber's own hooks are what run per-scenario here.

## 5. Feature files

`login.feature`:
```gherkin
@smoke
Feature: SauceDemo Login

  Scenario: Successful login with valid credentials
    Given I am on the SauceDemo login page
    When I log in with username "standard_user" and password "secret_sauce"
    Then I should see the inventory page

  @regression
  Scenario: Login fails with locked out user
    Given I am on the SauceDemo login page
    When I log in with username "locked_out_user" and password "secret_sauce"
    Then I should see an error message "Sorry, this user has been locked out."
```

`inventory.feature`: at least one scenario covering add-to-cart and cart badge count
update, tagged `@regression`. Keep scenarios independent (each does its own login via
`Background:` or an explicit login step) — this matters for parallel-safety same as it
did in the TestNG framework.

## 6. Step definitions

- `LoginSteps.java`, `InventorySteps.java` — each step method calls the corresponding
  page object action, no Selenium calls directly in step definition classes (page
  objects stay the only layer that touches `WebDriver`/`By` — keep this boundary clean,
  it's a common thing clients specifically check for in Cucumber frameworks).
- Use TestNG-style `Assert` (from `org.testng.Assert`) in `Then` steps to stay consistent
  with the TestNG bridge, not JUnit assertions.

## 7. TestRunner — Cucumber-TestNG bridge

```java
@CucumberOptions(
  features = "src/test/resources/features",
  glue = "io.github.borgautomation.stepdefinitions",
  tags = "not @wip",
  plugin = {"pretty"}
)
public class TestRunner extends AbstractTestNGCucumberTests {
  @Override
  @DataProvider(parallel = true)
  public Object[][] scenarios() {
    return super.scenarios();
  }
}
```

- The `@DataProvider(parallel = true)` override on `scenarios()` is what actually
  enables parallel execution here — this is the Cucumber-TestNG-specific mechanism,
  different from the plain `testng.xml parallel=` attribute used in the sibling project.
  Confirm this works by observing multiple scenarios executing concurrently, not by
  assuming the annotation alone is sufficient.
- Thread count for this DataProvider-driven parallelism is controlled via
  `surefire-plugin`'s `dataproviderthreadcount` configuration (or system property
  `-Ddataproviderthreadcount=3`) — set a default of 3 in the surefire config, same as
  the sibling project's thread-count choice, for consistency across both repos.

## 8. Proof of parallelism (required before calling this session done)

- Same verification approach as the TestNG framework: run `mvn test`, visually confirm
  multiple browser windows launch concurrently, and temporarily log
  `Thread.currentThread().getId()` in step definitions to confirm distinct thread IDs
  during a parallel run (remove the temporary logging once confirmed).
- Run with `-Dcucumber.filter.tags="@smoke"` and confirm only the tagged scenario runs —
  this proves tag-based execution works before Session 2 builds on top of it.

## Acceptance criteria

- `mvn test` runs all feature files through the TestNG bridge, no manual driver
  management.
- `-Dbrowser=firefox` runs the same suite in Firefox with zero code changes.
- `-Dcucumber.filter.tags="@smoke"` and `-Dcucumber.filter.tags="@regression"` each
  correctly filter which scenarios run.
- Parallel execution demonstrably faster than serial, multiple browsers visibly run at
  once.
- Step definitions contain no direct Selenium calls — everything routes through page
  objects.
- PROGRESS.md created (new file for this repo, separate from the TestNG framework's):
  parallelism mechanism confirmed (DataProvider-based, not testng.xml-based), timing
  numbers, any deviations from this brief.

## Ground rules

- No `Thread.sleep`; explicit waits only.
- Do not add ExtentReports, Log4j2, retry analyzer, or CI in this session — Session 2.
- Keep step definitions thin — one line calling a page object method per step, no
  business logic or assertions-plus-actions mixed in a single step where avoidable.
