package com.amdocs.telecom.exception;

public class CustomerNotEligibleException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public CustomerNotEligibleException(String message) { super(message); }
    public CustomerNotEligibleException(String message, Throwable cause) { super(message, cause); }
}
