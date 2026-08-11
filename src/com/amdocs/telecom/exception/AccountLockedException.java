package com.amdocs.telecom.exception;

/** Exception thrown when an account is locked or disabled. */
public class AccountLockedException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public AccountLockedException(String message) {
        super(message);
    }

    public AccountLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
