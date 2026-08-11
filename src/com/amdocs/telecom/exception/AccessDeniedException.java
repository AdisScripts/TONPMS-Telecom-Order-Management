package com.amdocs.telecom.exception;

/** Exception thrown when role-based access authorization fails. */
public class AccessDeniedException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
