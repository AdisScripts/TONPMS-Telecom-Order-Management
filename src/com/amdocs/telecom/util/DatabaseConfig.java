package com.amdocs.telecom.util;

import com.amdocs.telecom.exception.DatabaseException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loads local database settings without exposing credentials in source code. */
public final class DatabaseConfig {
    private static final String CONFIG_PATH = "config/config.properties";
    private static final String URL_KEY = "db.url";
    private static final String USERNAME_KEY = "db.username";
    private static final String PASSWORD_KEY = "db.password";
    private static final String DRIVER_KEY = "db.driver";
    private final Properties properties = new Properties();

    private DatabaseConfig() {
        try (InputStream input = new FileInputStream(CONFIG_PATH)) {
            properties.load(input);
        } catch (IOException ex) {
            throw new DatabaseException("Unable to load " + CONFIG_PATH
                    + ". Copy config/config.properties.example and configure it locally.", ex);
        }
    }

    /** Loads the current local configuration on demand, preserving clear configuration errors. */
    public static DatabaseConfig getInstance() { return new DatabaseConfig(); }
    public String getUrl() { return required(URL_KEY); }
    public String getUsername() { return required(USERNAME_KEY); }
    public String getPassword() { return required(PASSWORD_KEY); }
    public String getDriver() { return properties.getProperty(DRIVER_KEY, "com.mysql.cj.jdbc.Driver"); }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty() || value.startsWith("YOUR_")) {
            throw new DatabaseException("Missing a usable " + key + " in " + CONFIG_PATH);
        }
        return value.trim();
    }
}
