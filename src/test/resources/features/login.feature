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
    Then I should see an error message "Epic sadface: Sorry, this user has been locked out."
