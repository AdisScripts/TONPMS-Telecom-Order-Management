package com.amdocs.telecom.exception;

public class DuplicateOrderException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public DuplicateOrderException(String message) { super(message); }
    public DuplicateOrderException(String message, Throwable cause) { super(message, cause); }
}
