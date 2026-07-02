package io.github.borgautomation.stepdefinitions;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.borgautomation.pages.InventoryPage;
import io.github.borgautomation.utils.DriverFactory;
import org.testng.Assert;

public class InventorySteps {

    @When("I add the item with button id {string} to the cart")
    public void iAddTheItemWithButtonIdToTheCart(String addToCartButtonId) {
        new InventoryPage(DriverFactory.getDriver()).addItemToCart(addToCartButtonId);
    }

    @Then("the cart badge count should be {string}")
    public void theCartBadgeCountShouldBe(String expectedCount) {
        Assert.assertEquals(new InventoryPage(DriverFactory.getDriver()).getCartBadgeCount(), Integer.parseInt(expectedCount));
    }
}
