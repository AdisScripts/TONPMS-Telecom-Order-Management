package com.amdocs.telecom.exception;

public class InventoryUnavailableException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public InventoryUnavailableException(String message) { super(message); }
    public InventoryUnavailableException(String message, Throwable cause) { super(message, cause); }
}
