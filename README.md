# selenium-cucumber-bdd-framework

[![CI](https://github.com/borg-automation/selenium-cucumber-bdd-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/borg-automation/selenium-cucumber-bdd-framework/actions/workflows/ci.yml)

A Selenium + Cucumber + TestNG + Maven BDD automation framework: Gherkin feature files with
Scenario Outlines, ThreadLocal-safe parallel execution across local browser instances (no
Grid/Docker), Page Object Model, Log4j2 logging, ExtentReports with screenshot-on-failure, and
a TestNG retry analyzer wired into Cucumber-TestNG's generated data-provider test method.

## Stack

- Java 21, Maven
- Selenium 4.33.0, Cucumber 7.20.1, TestNG 7.11.0 (via `cucumber-testng`)
- WebDriverManager 6.1.0 (no manual driver binaries)
- Log4j2 2.26.0
- ExtentReports 5.1.2 (Spark reporter)
- Jackson Databind 2.19.0 (external JSON test data)

## Project structure

```
src/main/java/io/github/borgautomation/
  pages/           Page Object classes (BasePage, LoginPage, InventoryPage)
  utils/           DriverFactory, ConfigReader, ExtentManager, ScreenshotUtil

src/test/java/io/github/borgautomation/
  runners/         TestRunner (AbstractTestNGCucumberTests)
  stepdefinitions/ Hooks, LoginSteps, InventorySteps, BulkLoginSteps
  listeners/       RetryAnalyzer, AnnotationTransformer
  models/          LoginTestData (JSON row POJO)

src/test/resources/
  features/           login.feature, inventory.feature, login-external-data.feature
  config.properties   browser, baseUrl, retryCount, headless, etc.
  log4j2.xml          console + rolling file logging
  testdata/           login-users.json
  META-INF/services/  ServiceLoader registration for AnnotationTransformer
```

## Running the suite

```
mvn test
```

Runs every Gherkin scenario under `src/test/resources/features` through `TestRunner`
(`AbstractTestNGCucumberTests`), with `@DataProvider(parallel = true)` launching real browser
windows concurrently, not sequentially.

Switch browser without touching any files:

```
mvn test -Dbrowser=firefox
```

Run headless (same as CI):

```
mvn test -Dheadless=true -Dbrowser=chrome
```

Filter by tag:

```
mvn test -Dcucumber.filter.tags=@smoke
```

## Reports & logs

- `test-output/ExtentReports/Report_<timestamp>.html` — one entry per scenario (Scenario
  Outline rows disambiguated by Examples line number), screenshots embedded inline on failure,
  retry attempts logged onto the same node.
- `target/cucumber-reports/cucumber-report.html` — Cucumber's own native HTML report.
- `logs/automation.log` — rolling log file, thread-tagged so concurrent scenario threads can be
  told apart.

None of these are committed (see `.gitignore`); all are regenerated on every run.

## Configuration

`src/test/resources/config.properties`:

| Key | Default | Notes |
|---|---|---|
| `browser` | `chrome` | `chrome` or `firefox`; overridable via `-Dbrowser=...` |
| `baseUrl` | `https://www.saucedemo.com/` | |
| `implicitWaitSeconds` | `0` | Reserved; the framework uses explicit waits only |
| `retryCount` | `2` | Max retries per scenario via `RetryAnalyzer` |
| `headless` | `false` | Overridable via `-Dheadless=true`; CI always sets this |

## CI

`.github/workflows/ci.yml` runs the full suite headless on `ubuntu-latest` across a
`chrome`/`firefox` matrix on every push/PR to `main`/`master`, uploading the ExtentReport,
Cucumber HTML report, and log file as build artifacts (`if: always()`, so they upload even on
failure).

## Status / roadmap

See `PROGRESS.md` for what's been verified so far, known environment caveats, and deviations
from the original session plans.
