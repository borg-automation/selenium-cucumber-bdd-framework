package io.github.borgautomation.stepdefinitions;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.github.borgautomation.utils.ConfigReader;
import io.github.borgautomation.utils.DriverFactory;

public class Hooks {

    @Before
    public void setUp() {
        DriverFactory.setDriver();
        DriverFactory.getDriver().get(ConfigReader.getInstance().getBaseUrl());
    }

    @After
    public void tearDown() {
        DriverFactory.quitDriver();
    }
}
