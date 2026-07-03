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

    @Then("I should see {string}")
    public void iShouldSee(String expectedResult) {
        if (expectedResult.startsWith("error: ")) {
            String expectedMessage = expectedResult.substring("error: ".length());
            Assert.assertEquals(new LoginPage(DriverFactory.getDriver()).getErrorMessage(), expectedMessage);
        } else {
            Assert.assertTrue(new InventoryPage(DriverFactory.getDriver()).isLoaded());
        }
    }
}
