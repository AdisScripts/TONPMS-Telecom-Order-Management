package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.RowMapper;
import com.amdocs.telecom.exception.DatabaseException;
import com.amdocs.telecom.util.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared, persistence-only JDBC mechanics for DAO implementations supporting thread-bound transactions. */
abstract class AbstractJdbcDao {
    protected static LocalDate localDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    protected static LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Connection obtainConnection(boolean[] isShared) {
        if (DatabaseConnection.isThreadConnectionActive()) {
            isShared[0] = true;
            return DatabaseConnection.getConnection();
        } else {
            isShared[0] = false;
            return DatabaseConnection.getConnection();
        }
    }

    private void releaseConnection(Connection connection, boolean isShared) {
        if (!isShared && connection != null) {
            try { connection.close(); } catch (SQLException ignored) { }
        }
    }

    protected long insert(String sql, StatementBinder binder) {
        boolean[] isShared = new boolean[1];
        Connection connection = obtainConnection(isShared);
        try {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                binder.bind(statement);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) { return keys.getLong(1); }
                }
                throw new DatabaseException("Insert completed without returning a generated key.");
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Database insert failed.", ex);
        } finally {
            releaseConnection(connection, isShared[0]);
        }
    }

    protected boolean executeUpdate(String sql, StatementBinder binder) {
        boolean[] isShared = new boolean[1];
        Connection connection = obtainConnection(isShared);
        try {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Database update failed.", ex);
        } finally {
            releaseConnection(connection, isShared[0]);
        }
    }

    protected <T> Optional<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
        boolean[] isShared = new boolean[1];
        Connection connection = obtainConnection(isShared);
        try {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.ofNullable(mapper.map(resultSet)) : Optional.<T>empty();
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Database query failed.", ex);
        } finally {
            releaseConnection(connection, isShared[0]);
        }
    }

    protected <T> List<T> queryList(String sql, StatementBinder binder, RowMapper<T> mapper) {
        boolean[] isShared = new boolean[1];
        Connection connection = obtainConnection(isShared);
        List<T> results = new ArrayList<T>();
        try {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) { results.add(mapper.map(resultSet)); }
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Database query failed.", ex);
        } finally {
            releaseConnection(connection, isShared[0]);
        }
    }

    @FunctionalInterface
    protected interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
