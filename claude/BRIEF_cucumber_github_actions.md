# Session Brief: GitHub Actions CI/CD (Cucumber)

## Project coordinates
- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-cucumber-bdd-framework`
- Repo: `github.com/borg-automation/selenium-cucumber-bdd-framework`

## Prerequisite
Sessions 1-3 complete: DriverFactory, ConfigReader, Hooks, feature files with Scenario
Outlines, TestRunner with parallel execution, Log4j2, ExtentReports, screenshot-on-
failure, retry analyzer (whichever wiring approach ended up working per Session 3's
PROGRESS.md).

## Scope

Same shape as the TestNG framework's CI session — headless mode, matrix build across
Chrome/Firefox, artifact upload, README badge. The workflow file itself is nearly
identical; the one difference is the Maven command that actually triggers the run,
since this project executes through the Cucumber-TestNG runner rather than a
`testng.xml` suite file.

**In:** headless mode support, GitHub Actions workflow, artifact upload for both
ExtentReports and Cucumber's own native report output, README badge.

**Out:** Docker/Grid, deploy steps — same boundary as the TestNG framework's CI session.

---

## 1. Add headless support to DriverFactory

Identical to the TestNG framework: `headless` config key, `-Dheadless=true` override,
`--headless=new` for Chrome, `-headless` for Firefox, `--no-sandbox` and
`--disable-dev-shm-usage` for Chrome on Linux runners. Verify locally
(`mvn test -Dheadless=true -Dbrowser=chrome`) before touching CI.

## 2. Confirm the CI-invoking Maven command

Session 1 configured Surefire to run through `TestRunner` (the
`AbstractTestNGCucumberTests` subclass), not a `testng.xml` file. Confirm the exact
command that runs the full suite locally first — likely still just `mvn test` if
Surefire's configured correctly, but verify tag filtering also still works via
`-Dcucumber.filter.tags=`. Whatever the confirmed local command is, that's what goes in
the workflow — don't assume it's identical to the sibling repo's `mvn test`, confirm.

## 3. Workflow file: `.github/workflows/ci.yml`

- Same structure as the TestNG framework: triggers on push/PR to main, `ubuntu-latest`,
  matrix `browser: [chrome, firefox]`, JDK 21 via `setup-java` with `cache: maven`.
- Run command: `mvn test -Dbrowser=${{ matrix.browser }} -Dheadless=true` (adjust if
  step 2 found a different required command).
- Consider a second matrix dimension or a separate job for tag-based runs (e.g. one job
  runs `@smoke` only for fast feedback, full suite runs separately) — optional, only add
  if it doesn't complicate the workflow beyond what's needed for a portfolio demo. If in
  doubt, skip this and keep the workflow as simple as the TestNG framework's.

## 4. Publish reports as artifacts

- `actions/upload-artifact@v4`, `if: always()`, for:
  - `test-output/ExtentReports/` (same as TestNG framework)
  - Cucumber's own native report output if configured in `@CucumberOptions` `plugin =`
    (e.g. if an `html` plugin path was added in Session 3, upload that directory too —
    two report artifacts, not a conflict, gives a client two different views of the same
    run)
  - `logs/automation.log`
- Name artifacts per matrix leg to avoid overwrites, same pattern as before.

## 5. README badge

Same as the TestNG framework, pointed at this repo's workflow:
```
![CI](https://github.com/borg-automation/selenium-cucumber-bdd-framework/actions/workflows/ci.yml/badge.svg)
```

---

## Acceptance criteria

- Push triggers both matrix jobs (chrome, firefox), both run headless, both pass.
- Downloaded ExtentReport artifact opens correctly with screenshots intact.
- If Cucumber's native report is also uploaded, confirm it opens correctly too.
- Deliberately break one scenario, confirm the job goes red and both report artifacts
  still upload (`if: always()` working), badge reflects failure then recovers after fix.
- PROGRESS.md updated: the confirmed CI-invoking Maven command from step 2, any
  headless-specific quirks, confirmation of which retry-wiring approach (from Session 3)
  is working correctly under CI specifically — retry-under-CI is worth double-checking
  since headless timing can differ from local headed runs and might interact with the
  retry count in ways that weren't visible locally.

## Ground rules

- No `Thread.sleep` for headless timing issues — fix explicit waits instead.
- Keep the workflow to test-run + artifact-upload, no deploy step.
- Don't diverge unnecessarily from the TestNG framework's workflow structure — keeping
  both `ci.yml` files structurally similar (same job names, same artifact-naming
  convention) makes the two repos read as a matched pair when a client looks at both,
  which is the point of building two frameworks in the first place.
