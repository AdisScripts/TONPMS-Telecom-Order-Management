package com.amdocs.telecom.util;

import com.amdocs.telecom.exception.DatabaseException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Creates short-lived JDBC connections. Callers own and close returned connections. */
public final class DatabaseConnection {
    private static volatile boolean driverLoaded;

    private DatabaseConnection() { }

    public static Connection getConnection() {
        DatabaseConfig config = DatabaseConfig.getInstance();
        loadDriver(config.getDriver());
        try {
            return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
        } catch (SQLException ex) {
            throw new DatabaseException("Unable to create a MySQL database connection.", ex);
        }
    }

    private static synchronized void loadDriver(String driverClass) {
        if (driverLoaded) { return; }
        try {
            Class.forName(driverClass);
            driverLoaded = true;
        } catch (ClassNotFoundException ex) {
            throw new DatabaseException("MySQL JDBC driver not found: " + driverClass
                    + ". Add MySQL Connector/J to the runtime classpath.", ex);
        }
    }
}
