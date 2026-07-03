package io.github.borgautomation.models;

public class LoginTestData {

    public enum Outcome {
        SUCCESS, ERROR
    }

    public String username;
    public String password;
    public Outcome expectedOutcome;
    public String expectedMessage;

    @Override
    public String toString() {
        return username;
    }
}
