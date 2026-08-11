package com.amdocs.telecom.service;

import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.OtpPurpose;
import com.amdocs.telecom.security.UserSession;

/** Service interface for authentication, customer registration, OTP, password management, and account locking. */
public interface AuthenticationService {

    UserSession login(String username, String password, String captchaId, String captchaInput);

    UserSession login(String username, String password, String captchaId, String captchaInput, String ipAddress);

    Customer registerCustomer(Customer customer, String username, String rawPassword);

    String generateOtp(Long userId, OtpPurpose purpose);

    void verifyOtp(Long userId, OtpPurpose purpose, String otpInput);

    void changePassword(Long userId, String oldPassword, String newPassword);

    void resetPassword(String username, String otpInput, String newPassword);

    void lockAccount(Long userId, String reason);

    void unlockAccount(Long userId);
}
