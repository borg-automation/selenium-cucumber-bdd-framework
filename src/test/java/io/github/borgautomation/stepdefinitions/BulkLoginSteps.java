package io.github.borgautomation.stepdefinitions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.github.borgautomation.models.LoginTestData;
import io.github.borgautomation.pages.InventoryPage;
import io.github.borgautomation.pages.LoginPage;
import io.github.borgautomation.utils.ConfigReader;
import io.github.borgautomation.utils.DriverFactory;
import org.testng.Assert;

import java.io.IOException;
import java.io.InputStream;

public class BulkLoginSteps {

    @Given("I test all login attempts from file {string}")
    public void iTestAllLoginAttemptsFromFile(String fileName) throws IOException {
        for (LoginTestData row : loadLoginTestData(fileName)) {
            // A fresh driver per row instead of reusing the session: reusing one browser session
            // for repeated logins left SauceDemo's SPA in a state where its error banner silently
            // failed to mount on the next attempt.
            DriverFactory.quitDriver();
            DriverFactory.setDriver();
            DriverFactory.getDriver().get(ConfigReader.getInstance().getBaseUrl());
            LoginPage loginPage = new LoginPage(DriverFactory.getDriver());
            Assert.assertTrue(loginPage.isLoaded());
            InventoryPage inventoryPage = loginPage.loginAs(row.username, row.password);

            if (row.expectedOutcome == LoginTestData.Outcome.SUCCESS) {
                Assert.assertTrue(inventoryPage.isLoaded());
            } else {
                Assert.assertEquals(loginPage.getErrorMessage(), row.expectedMessage);
            }
        }
    }

    private LoginTestData[] loadLoginTestData(String fileName) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("testdata/" + fileName)) {
            if (input == null) {
                throw new IllegalStateException("Unable to find testdata/" + fileName + " on the classpath");
            }
            return new ObjectMapper().readValue(input, LoginTestData[].class);
        }
    }
}
