package com.amdocs.telecom.exception;

/** Unchecked wrapper for JDBC/configuration failures in the persistence layer. */
public class DatabaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatabaseException(String message) { super(message); }
    public DatabaseException(String message, Throwable cause) { super(message, cause); }
}
