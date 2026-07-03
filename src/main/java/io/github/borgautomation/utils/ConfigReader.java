package io.github.borgautomation.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {

    private static final String CONFIG_FILE = "config.properties";
    private static volatile ConfigReader instance;
    private final Properties properties = new Properties();

    private ConfigReader() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IllegalStateException("Unable to find " + CONFIG_FILE + " on the classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CONFIG_FILE, e);
        }
    }

    public static ConfigReader getInstance() {
        if (instance == null) {
            synchronized (ConfigReader.class) {
                if (instance == null) {
                    instance = new ConfigReader();
                }
            }
        }
        return instance;
    }

    public String getBrowser() {
        return System.getProperty("browser", properties.getProperty("browser", "chrome"));
    }

    public String getBaseUrl() {
        return properties.getProperty("baseUrl");
    }

    public int getImplicitWaitSeconds() {
        return Integer.parseInt(properties.getProperty("implicitWaitSeconds", "0"));
    }

    public int getRetryCount() {
        return Integer.parseInt(properties.getProperty("retryCount", "2"));
    }
}
