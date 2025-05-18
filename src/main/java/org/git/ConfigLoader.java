package org.git;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to load configuration properties from a properties file.
 */
public class ConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
    private static final Properties properties = new Properties();
    private static final String DEFAULT_PROPS_FILE = "git_test.properties";

    // Static initializer block to load properties when the class is loaded
    static {
        loadProperties(DEFAULT_PROPS_FILE);
    }

    /**
     * Loads properties from the specified properties file located in the classpath.
     * @param fileName The name of the properties file.
     */
    private static void loadProperties(String fileName) {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                LOGGER.log(Level.SEVERE, "Sorry, unable to find " + fileName + " in the classpath.");
                // Optionally, throw a runtime exception if the file is critical
                // throw new RuntimeException("Configuration file " + fileName + " not found.");
                return;
            }
            properties.load(input);
            LOGGER.log(Level.INFO, "Successfully loaded properties from " + fileName);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "IOException occurred while loading properties from " + fileName, ex);
            // Optionally, rethrow as a runtime exception
            // throw new RuntimeException("Failed to load properties from " + fileName, ex);
        }
    }

    /**
     * Gets a property value by its key.
     * Replaces {TIMESTAMP} placeholder with the current time in millis.
     * @param key The property key.
     * @return The property value, or null if the key is not found or properties failed to load.
     */
    public static String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value != null) {
            if (value.contains("{TIMESTAMP}")) {
                value = value.replace("{TIMESTAMP}", String.valueOf(System.currentTimeMillis()));
            }
        } else {
            LOGGER.log(Level.WARNING, "Property not found for key: " + key + " in " + DEFAULT_PROPS_FILE);
        }
        return value;
    }

    /**
     * Gets a property value by its key, returning a default value if the key is not found.
     * Replaces {TIMESTAMP} placeholder with the current time in millis.
     * @param key The property key.
     * @param defaultValue The default value to return if the key is not found.
     * @return The property value, or the defaultValue if the key is not found.
     */
    public static String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        if (value != null && value.contains("{TIMESTAMP}")) {
            value = value.replace("{TIMESTAMP}", String.valueOf(System.currentTimeMillis()));
        }
        // If key was not found, properties.getProperty(key, defaultValue) returns defaultValue,
        // so no need for an extra warning here unless defaultValue itself is null.
        if (properties.getProperty(key) == null) {
            LOGGER.log(Level.FINE, "Property not found for key: " + key + ", using default value.");
        }
        return value;
    }
}

