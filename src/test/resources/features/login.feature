@smoke
Feature: SauceDemo Login

  Scenario Outline: Login attempts with various users
    Given I am on the SauceDemo login page
    When I log in with username "<username>" and password "<password>"
    Then I should see "<expectedResult>"

    Examples: Valid logins
      | username      | password     | expectedResult     |
      | standard_user | secret_sauce | the inventory page |
      | problem_user  | secret_sauce | the inventory page |

    @regression
    Examples: Invalid logins
      | username        | password     | expectedResult                                                                    |
      | locked_out_user | secret_sauce | error: Epic sadface: Sorry, this user has been locked out.                       |
      | invalid_user    | wrong_pass   | error: Epic sadface: Username and password do not match any user in this service |
