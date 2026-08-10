package com.amdocs.telecom.exception;

public class InvalidOrderException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public InvalidOrderException(String message) { super(message); }
    public InvalidOrderException(String message, Throwable cause) { super(message, cause); }
}
