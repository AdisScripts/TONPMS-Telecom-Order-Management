package com.amdocs.telecom.exception;

public class ProductUnavailableException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public ProductUnavailableException(String message) { super(message); }
    public ProductUnavailableException(String message, Throwable cause) { super(message, cause); }
}
