@regression
Feature: SauceDemo Login - external dataset (demonstration)

  Scenario: Bulk login validation from external dataset
    Given I test all login attempts from file "login-users.json"
