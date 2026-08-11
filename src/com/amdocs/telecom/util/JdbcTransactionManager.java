package com.amdocs.telecom.util;

import com.amdocs.telecom.exception.DatabaseException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/** Reusable JDBC transaction primitives; business workflows are intentionally deferred. */
public final class JdbcTransactionManager {
    private JdbcTransactionManager() { }

    public static void begin(Connection connection) {
        try { connection.setAutoCommit(false); }
        catch (SQLException ex) { throw new DatabaseException("Unable to begin transaction.", ex); }
    }
    public static void commit(Connection connection) {
        try { connection.commit(); }
        catch (SQLException ex) { throw new DatabaseException("Unable to commit transaction.", ex); }
    }
    public static void rollback(Connection connection) {
        try { connection.rollback(); }
        catch (SQLException ex) { throw new DatabaseException("Unable to roll back transaction.", ex); }
    }
    public static Savepoint createSavepoint(Connection connection, String name) {
        try { return connection.setSavepoint(name); }
        catch (SQLException ex) { throw new DatabaseException("Unable to create savepoint.", ex); }
    }
    public static void rollback(Connection connection, Savepoint savepoint) {
        try { connection.rollback(savepoint); }
        catch (SQLException ex) { throw new DatabaseException("Unable to roll back to savepoint.", ex); }
    }
}
