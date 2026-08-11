package com.amdocs.telecom.exception;

/** Exception thrown when CAPTCHA validation fails or expires. */
public class InvalidCaptchaException extends TelecomDomainException {
    private static final long serialVersionUID = 1L;

    public InvalidCaptchaException(String message) {
        super(message);
    }

    public InvalidCaptchaException(String message, Throwable cause) {
        super(message, cause);
    }
}
