package com.amdocs.telecom.exception;

/** Base unchecked exception for business-rule violations in TONPMS. */
public class TelecomDomainException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TelecomDomainException(String message) {
        super(message);
    }

    public TelecomDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
