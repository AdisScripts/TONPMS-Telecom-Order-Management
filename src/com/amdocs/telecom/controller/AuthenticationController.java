package com.amdocs.telecom.controller;

import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.OtpPurpose;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.security.CaptchaService;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuthenticationService;

import java.io.PrintStream;
import java.util.Scanner;

public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final CaptchaService captchaService;

    public AuthenticationController(AuthenticationService authenticationService, CaptchaService captchaService) {
        this.authenticationService = authenticationService;
        this.captchaService = captchaService;
    }

    public UserSession handleCustomerLogin(Scanner scanner, PrintStream out) {
        return handleLoginWithRole(scanner, out, "CUSTOMER LOGIN", RoleCode.CUSTOMER);
    }

    public UserSession handleAdminLogin(Scanner scanner, PrintStream out) {
        return handleLoginWithRole(scanner, out, "ORDER ADMINISTRATOR LOGIN", RoleCode.ORDER_ADMINISTRATOR);
    }

    public UserSession handleProvisioningEngineerLogin(Scanner scanner, PrintStream out) {
        return handleLoginWithRole(scanner, out, "PROVISIONING ENGINEER LOGIN", RoleCode.PROVISIONING_ENGINEER);
    }

    public UserSession handleInventoryAdminLogin(Scanner scanner, PrintStream out) {
        return handleLoginWithRole(scanner, out, "INVENTORY ADMINISTRATOR LOGIN", RoleCode.INVENTORY_ADMINISTRATOR);
    }

    private UserSession handleLoginWithRole(Scanner scanner, PrintStream out, String title, RoleCode requiredRole) {
        out.println("\n--- " + title + " ---");
        out.print("Username: ");
        String username = scanner.nextLine().trim();
        out.print("Password: ");
        String password = scanner.nextLine().trim();

        CaptchaService.CaptchaChallenge challenge = captchaService.generateCaptcha();
        out.println("CAPTCHA Challenge: [" + challenge.getCaptchaText() + "]");
        out.print("Enter CAPTCHA: ");
        String captchaInput = scanner.nextLine().trim();

        try {
            UserSession session = authenticationService.login(username, password, challenge.getChallengeId(), captchaInput);
            if (!session.hasRole(requiredRole)) {
                session.invalidate();
                out.println("ERROR: Account does not have permissions for " + title + ".");
                return null;
            }
            out.println("SUCCESS: Welcome, " + session.getUsername() + "!");
            return session;
        } catch (Exception e) {
            out.println("LOGIN FAILED: " + e.getMessage());
            return null;
        }
    }

    public void handleCustomerRegistration(Scanner scanner, PrintStream out) {
        out.println("\n--- CUSTOMER REGISTRATION ---");
        out.print("Full Name: ");
        String name = scanner.nextLine().trim();
        out.print("Email: ");
        String email = scanner.nextLine().trim();
        out.print("Phone: ");
        String phone = scanner.nextLine().trim();
        out.print("Customer Type (1. INDIVIDUAL, 2. SME, 3. ENTERPRISE): ");
        String typeChoice = scanner.nextLine().trim();
        CustomerType type = CustomerType.INDIVIDUAL;
        if ("2".equals(typeChoice)) type = CustomerType.SME;
        else if ("3".equals(typeChoice)) type = CustomerType.ENTERPRISE;

        out.print("Choose Username: ");
        String username = scanner.nextLine().trim();
        out.print("Choose Password: ");
        String password = scanner.nextLine().trim();

        CaptchaService.CaptchaChallenge challenge = captchaService.generateCaptcha();
        out.println("CAPTCHA Challenge: [" + challenge.getCaptchaText() + "]");
        out.print("Enter CAPTCHA: ");
        String captchaInput = scanner.nextLine().trim();

        try {
            captchaService.verifyCaptcha(challenge.getChallengeId(), captchaInput);
            Customer c = new Customer("CUST-" + (System.currentTimeMillis() % 100000), name, email, phone, type);
            Customer registered = authenticationService.registerCustomer(c, username, password);

            String regOtp = authenticationService.generateOtpForUsername(username, OtpPurpose.LOGIN_VERIFICATION);
            out.println("[SIMULATED REGISTRATION OTP SENT]: " + regOtp);
            out.print("Enter Registration OTP: ");
            String otpInput = scanner.nextLine().trim();

            authenticationService.verifyOtpForUsername(username, OtpPurpose.LOGIN_VERIFICATION, otpInput);

            out.println("SUCCESS: Registration and OTP verification completed for " + registered.getCustomerName() + " (Customer ID: " + registered.getCustomerId() + ").");
        } catch (Exception e) {
            out.println("REGISTRATION FAILED: " + e.getMessage());
        }
    }

    public void handleForgotPassword(Scanner scanner, PrintStream out) {
        out.println("\n--- FORGOT / RESET PASSWORD ---");
        out.print("Enter Username: ");
        String username = scanner.nextLine().trim();

        try {
            String generatedOtp = authenticationService.generateOtpForUsername(username, OtpPurpose.PASSWORD_RESET);
            out.println("[SIMULATED PASSWORD RESET OTP SENT]: " + generatedOtp);
            out.print("Enter OTP: ");
            String otpInput = scanner.nextLine().trim();
            out.print("Enter New Password: ");
            String newPassword = scanner.nextLine().trim();

            authenticationService.resetPassword(username, otpInput, newPassword);
            out.println("SUCCESS: Password reset successfully for " + username + ".");
        } catch (Exception e) {
            out.println("PASSWORD RESET FAILED: " + e.getMessage());
        }
    }
}
