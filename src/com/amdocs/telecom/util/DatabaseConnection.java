package com.amdocs.telecom.util;

import com.amdocs.telecom.exception.DatabaseException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Creates short-lived JDBC connections or retrieves active thread-bound transaction connection. */
public final class DatabaseConnection {
    private static volatile boolean driverLoaded;
    private static final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();

    private DatabaseConnection() { }

    public static Connection getConnection() {
        Connection current = threadConnection.get();
        if (current != null) {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(current, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause() != null ? ex.getCause() : ex;
                    }
                }
            );
        }
        DatabaseConfig config = DatabaseConfig.getInstance();
        loadDriver(config.getDriver());
        try {
            return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
        } catch (SQLException ex) {
            throw new DatabaseException("Unable to create a MySQL database connection.", ex);
        }
    }

    public static void setThreadConnection(Connection connection) {
        threadConnection.set(connection);
    }

    public static void clearThreadConnection() {
        threadConnection.remove();
    }

    public static boolean isThreadConnectionActive() {
        return threadConnection.get() != null;
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
