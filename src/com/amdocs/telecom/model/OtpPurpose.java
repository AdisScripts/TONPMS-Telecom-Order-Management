package com.amdocs.telecom.model;

/**
 * Application decision: the schema leaves OTP purpose open as a VARCHAR.
 * These values cover the PDF's login and forgot-password flows.
 */
public enum OtpPurpose {
    LOGIN_VERIFICATION, PASSWORD_RESET
}
