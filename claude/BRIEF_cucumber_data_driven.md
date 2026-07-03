# Session Brief: Data-Driven Scenarios (Cucumber)

## Project coordinates
- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-cucumber-bdd-framework`

## Prerequisite
Session 1 complete: DriverFactory, ConfigReader, Hooks, LoginPage/InventoryPage,
login.feature/inventory.feature, step definitions, TestRunner with parallel execution
and tag filtering proven working.

## Important context — this is NOT the same pattern as the TestNG framework

The sibling project (`selenium-testng-framework`) uses `@DataProvider` with external
JSON/CSV files, because plain TestNG has no built-in way to express a data table in the
test itself. Cucumber is different: **Gherkin has a native, built-in data-driven
construct — `Scenario Outline` + `Examples`.** This is the idiomatic, expected way to
do data-driven testing in a Cucumber framework, and a client evaluating this repo will
expect to see it. Pulling in JSON parsing to feed Cucumber Examples externally is
possible but non-idiomatic and adds complexity Gherkin already solves — do that only as
the optional second half of this session (step 3 below), not as the primary approach.

## Scope

**In:** convert the login feature to a `Scenario Outline` with an `Examples` table
covering multiple users, add a second `Scenario Outline` for a checkout/cart scenario,
and (optional, secondary) one example of reading external JSON data into a step via a
`DataTable`-consuming step for cases where data genuinely needs to live outside the
feature file.

**Out:** ExtentReports, Log4j2, retry analyzer, CI — still Session 2 of the original plan
(renumber as Session 3 if you're tracking these sequentially; note the renumbering in
this repo's PROGRESS.md so the session history stays clear).

---

## 1. Scenario Outline — login (primary approach)

Rewrite `login.feature`'s login scenario as an Outline:

```gherkin
@smoke
Feature: SauceDemo Login

  Scenario Outline: Login attempts with various users
    Given I am on the SauceDemo login page
    When I log in with username "<username>" and password "<password>"
    Then I should see "<expectedResult>"

    @regression
    Examples:
      | username              | password      | expectedResult                                  |
      | standard_user         | secret_sauce  | the inventory page                              |
      | locked_out_user       | secret_sauce  | error: Sorry, this user has been locked out.    |
      | problem_user          | secret_sauce  | the inventory page                              |
      | invalid_user          | wrong_pass    | error: Username and password do not match       |
```

- Single `Then` step with one parameter (`"<expectedResult>"`) that branches internally
  (starts with `"error:"` → assert error message; otherwise → assert inventory page
  loaded) is cleaner than two different `Then` steps for pass/fail — write the step
  definition to parse this, don't create two near-duplicate step methods.
- Each row runs as its own scenario under the hood — this is what gives you the
  data-driven behavior. Confirm in the Cucumber output that 4 scenarios execute, not 1.

## 2. Scenario Outline — cart / checkout (second scenario)

Add a second `Scenario Outline` to `inventory.feature` (or a new `checkout.feature`)
covering multiple products added to cart, or checkout form field validation with
multiple invalid-field combinations — pick whichever fits what Session 1 already built
for `InventoryPage`, note the choice in PROGRESS.md same as the TestNG framework's
equivalent decision.

## 3. Optional — external JSON data via a DataTable-consuming step

This is a secondary, smaller addition — implement only after steps 1-2 are solid.
Purpose: demonstrate the framework *can* pull from external data files when a table
genuinely needs to live outside the feature file (e.g. a large dataset, or data shared
across multiple feature files), while making clear this is the exception, not the
default pattern.

- One new scenario, e.g. a bulk/negative-testing scenario, structured as:
  ```gherkin
  Scenario: Bulk login validation from external dataset
    Given the following login attempts from "login-users.json" are tested:
      | source |
      | login-users.json |
  ```
  or more simply, a step like `Given I test all login attempts from file "login-users.json"`
  that reads the file directly (Jackson, same as the TestNG framework) and loops through
  assertions inside the step definition.
- Reuse the TestNG framework's `login-users.json` shape/content if convenient — same
  Jackson dependency (`jackson-databind`), same POJO pattern.
- Keep this to ONE demonstration scenario — the point is showing the capability exists,
  not migrating everything back off Gherkin's native Examples tables, which would defeat
  the purpose of using Cucumber in the first place.

## 4. Step definition changes

- Update `LoginSteps.java`: the `Then` step now parses `"<expectedResult>"` (see step 1
  above) instead of two separate hardcoded steps.
- Add step(s) for whichever second scenario is chosen in step 2.
- If step 3 is implemented: new step definition class or method for the file-based
  bulk scenario, calling a shared `LoginPage` action per row from the JSON, same as
  before — no new page object logic needed, only new step-definition/data-reading code.

## Acceptance criteria

- `mvn test` shows the login Scenario Outline expanding into (at minimum) 4 separate
  scenario executions in the Cucumber console output (`pretty` plugin output should show
  each Examples row as its own scenario line).
- Tag filtering still works: `-Dcucumber.filter.tags="@smoke"` still runs (or excludes,
  confirm which is intended) appropriately against the now-restructured feature file —
  Session 1 tagged only the Feature-level `@smoke`; decide whether individual Examples
  rows need their own tags (e.g. tagging just the locked-out-user row `@regression`
  requires placing tags directly above the `Examples:` block, not the `Scenario Outline:`
  line — verify this renders correctly, it's a common Gherkin syntax mistake).
- Parallel execution still functions across the expanded scenario count (more scenarios
  now exist post-expansion — confirm the existing `dataproviderthreadcount` setting from
  Session 1 still gives real concurrency, doesn't need to change unless you want more
  parallelism given the larger scenario count).
- If step 3 implemented: the file-based scenario passes and demonstrates the same
  assertions as the Examples-table version, proving both approaches reach the same
  correctness.
- PROGRESS.md updated: confirm the "Scenario Outline is primary, external-file
  DataTable is secondary/demonstrative" decision is recorded, plus session renumbering
  note if applicable.

## Ground rules

- Do not remove Gherkin's native Examples-table approach in favor of externalizing
  everything to JSON — that would make this framework indistinguishable from a plain
  TestNG data-provider framework wearing a Cucumber label, which defeats the point of
  building two differentiated portfolio pieces.
- Keep the optional step 3 genuinely optional and clearly separated — if time is short,
  ship without it and note the deferral in PROGRESS.md rather than rushing it.
