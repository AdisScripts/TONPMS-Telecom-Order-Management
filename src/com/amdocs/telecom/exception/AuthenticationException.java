package com.amdocs.telecom.exception;

/** Exception thrown when authentication fails due to invalid credentials or user not found. */
public class AuthenticationException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
