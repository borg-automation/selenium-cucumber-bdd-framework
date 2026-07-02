package io.github.borgautomation.stepdefinitions;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.borgautomation.pages.InventoryPage;
import io.github.borgautomation.pages.LoginPage;
import io.github.borgautomation.utils.DriverFactory;
import org.testng.Assert;

public class LoginSteps {

    @Given("I am on the SauceDemo login page")
    public void iAmOnTheSauceDemoLoginPage() {
        Assert.assertTrue(new LoginPage(DriverFactory.getDriver()).isLoaded());
    }

    @When("I log in with username {string} and password {string}")
    public void iLogInWithUsernameAndPassword(String username, String password) {
        new LoginPage(DriverFactory.getDriver()).loginAs(username, password);
    }

    @Then("I should see the inventory page")
    public void iShouldSeeTheInventoryPage() {
        Assert.assertTrue(new InventoryPage(DriverFactory.getDriver()).isLoaded());
    }

    @Then("I should see an error message {string}")
    public void iShouldSeeAnErrorMessage(String expectedMessage) {
        Assert.assertEquals(new LoginPage(DriverFactory.getDriver()).getErrorMessage(), expectedMessage);
    }
}
