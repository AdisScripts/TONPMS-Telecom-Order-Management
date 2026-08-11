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

/** Shared, persistence-only JDBC mechanics for DAO implementations. */
abstract class AbstractJdbcDao {
    protected static LocalDate localDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    protected static LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
    protected long insert(String sql, StatementBinder binder) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) { return keys.getLong(1); }
            }
            throw new DatabaseException("Insert completed without returning a generated key.");
        } catch (SQLException ex) { throw new DatabaseException("Database insert failed.", ex); }
    }

    protected boolean executeUpdate(String sql, StatementBinder binder) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) { throw new DatabaseException("Database update failed.", ex); }
    }

    protected <T> Optional<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(mapper.map(resultSet)) : Optional.<T>empty();
            }
        } catch (SQLException ex) { throw new DatabaseException("Database query failed.", ex); }
    }

    protected <T> List<T> queryList(String sql, StatementBinder binder, RowMapper<T> mapper) {
        List<T> results = new ArrayList<T>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) { results.add(mapper.map(resultSet)); }
            }
            return results;
        } catch (SQLException ex) { throw new DatabaseException("Database query failed.", ex); }
    }

    @FunctionalInterface
    protected interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
