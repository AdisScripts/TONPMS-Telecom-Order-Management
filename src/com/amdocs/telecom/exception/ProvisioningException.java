package com.amdocs.telecom.exception;

public class ProvisioningException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public ProvisioningException(String message) { super(message); }
    public ProvisioningException(String message, Throwable cause) { super(message, cause); }
}
