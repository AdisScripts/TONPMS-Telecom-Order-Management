package com.amdocs.telecom.exception;

/** Exception thrown when OTP validation fails, expires, or attempt limit is reached. */
public class InvalidOtpException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public InvalidOtpException(String message) {
        super(message);
    }

    public InvalidOtpException(String message, Throwable cause) {
        super(message, cause);
    }
}
