package com.amdocs.telecom.validation;

import com.amdocs.telecom.exception.TelecomDomainException;
import com.amdocs.telecom.model.Customer;

import java.util.regex.Pattern;

/** Validation utility for user input, credentials, and customer registration. */
public final class UserValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");

    private UserValidator() { }

    public static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new TelecomDomainException("Username cannot be null or empty.");
        }
        if (!USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new TelecomDomainException("Username must be 4-30 alphanumeric characters or underscores.");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new TelecomDomainException("Password must be at least 8 characters long.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new TelecomDomainException("Password must contain at least one letter and one digit.");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new TelecomDomainException("Invalid email address format.");
        }
    }

    public static void validateMobileNumber(String mobileNumber) {
        if (mobileNumber == null || !MOBILE_PATTERN.matcher(mobileNumber.trim()).matches()) {
            throw new TelecomDomainException("Invalid mobile number format.");
        }
    }

    public static void validateCustomerRegistration(Customer customer, String username, String rawPassword) {
        if (customer == null) {
            throw new TelecomDomainException("Customer profile cannot be null.");
        }
        if (customer.getCustomerName() == null || customer.getCustomerName().trim().isEmpty()) {
            throw new TelecomDomainException("Customer name cannot be empty.");
        }
        validateEmail(customer.getEmail());
        validateMobileNumber(customer.getMobileNumber());
        if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {
            throw new TelecomDomainException("Customer address cannot be empty.");
        }
        if (customer.getCity() == null || customer.getCity().trim().isEmpty()) {
            throw new TelecomDomainException("Customer city cannot be empty.");
        }
        if (customer.getCustomerType() == null) {
            throw new TelecomDomainException("Customer type must be specified.");
        }
        validateUsername(username);
        validatePassword(rawPassword);
    }
}
