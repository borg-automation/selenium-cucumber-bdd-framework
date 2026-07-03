@regression
Feature: SauceDemo Inventory

  Background:
    Given I am on the SauceDemo login page
    And I log in with username "standard_user" and password "secret_sauce"

  Scenario: Adding an item to the cart updates the cart badge count
    When I add the item with button id "add-to-cart-sauce-labs-backpack" to the cart
    Then the cart badge count should be "1"

  Scenario Outline: Adding various products to the cart updates the cart badge count
    When I add the item with button id "<addToCartButtonId>" to the cart
    Then the cart badge count should be "<expectedCount>"

    Examples:
      | addToCartButtonId                    | expectedCount |
      | add-to-cart-sauce-labs-backpack       | 1             |
      | add-to-cart-sauce-labs-bike-light     | 1             |
      | add-to-cart-sauce-labs-bolt-t-shirt   | 1             |
      | add-to-cart-sauce-labs-fleece-jacket  | 1             |
