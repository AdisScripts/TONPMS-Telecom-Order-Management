package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.AppRoleDao;
import com.amdocs.telecom.dao.AppUserDao;
import com.amdocs.telecom.dao.AppUserRoleDao;
import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.LoginHistoryDao;
import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.exception.AccountLockedException;
import com.amdocs.telecom.exception.AuthenticationException;
import com.amdocs.telecom.exception.TelecomDomainException;
import com.amdocs.telecom.model.AppRole;
import com.amdocs.telecom.model.AppUser;
import com.amdocs.telecom.model.AppUserRole;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.IdentityStatus;
import com.amdocs.telecom.model.LoginHistory;
import com.amdocs.telecom.model.OtpPurpose;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.UserAccountStatus;
import com.amdocs.telecom.security.CaptchaService;
import com.amdocs.telecom.security.OtpService;
import com.amdocs.telecom.security.PasswordUtils;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuthenticationService;
import com.amdocs.telecom.util.DatabaseConnection;
import com.amdocs.telecom.util.JdbcTransactionManager;
import com.amdocs.telecom.validation.UserValidator;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Implementation of AuthenticationService for user login, registration, lock policy, and security. */
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String DEFAULT_CONSOLE_IP = "CONSOLE";
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final AppUserDao appUserDao;
    private final AppRoleDao appRoleDao;
    private final AppUserRoleDao appUserRoleDao;
    private final CustomerDao customerDao;
    private final ProvisioningEngineerDao provisioningEngineerDao;
    private final LoginHistoryDao loginHistoryDao;
    private final CaptchaService captchaService;
    private final OtpService otpService;

    public AuthenticationServiceImpl(AppUserDao appUserDao,
                                     AppRoleDao appRoleDao,
                                     AppUserRoleDao appUserRoleDao,
                                     CustomerDao customerDao,
                                     ProvisioningEngineerDao provisioningEngineerDao,
                                     LoginHistoryDao loginHistoryDao,
                                     CaptchaService captchaService,
                                     OtpService otpService) {
        this.appUserDao = appUserDao;
        this.appRoleDao = appRoleDao;
        this.appUserRoleDao = appUserRoleDao;
        this.customerDao = customerDao;
        this.provisioningEngineerDao = provisioningEngineerDao;
        this.loginHistoryDao = loginHistoryDao;
        this.captchaService = captchaService;
        this.otpService = otpService;
    }

    @Override
    public UserSession login(String username, String password, String captchaId, String captchaInput) {
        return login(username, password, captchaId, captchaInput, DEFAULT_CONSOLE_IP);
    }

    @Override
    public UserSession login(String username, String password, String captchaId, String captchaInput, String ipAddress) {
        String effectiveIp = (ipAddress == null || ipAddress.trim().isEmpty()) ? DEFAULT_CONSOLE_IP : ipAddress.trim();

        // 1. Verify CAPTCHA
        captchaService.verifyCaptcha(captchaId, captchaInput);

        // 2. Fetch User
        if (username == null || username.trim().isEmpty()) {
            recordLoginAttempt(null, username, false, effectiveIp, "EMPTY_USERNAME");
            throw new AuthenticationException("Username cannot be empty.");
        }

        Optional<AppUser> userOpt = appUserDao.findByUsername(username.trim());
        if (!userOpt.isPresent()) {
            recordLoginAttempt(null, username.trim(), false, effectiveIp, "USER_NOT_FOUND");
            throw new AuthenticationException("Invalid username or password.");
        }

        AppUser user = userOpt.get();
        LocalDateTime now = LocalDateTime.now();

        // 3. Check Lock / Status
        if (user.getAccountStatus() == UserAccountStatus.LOCKED) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                recordLoginAttempt(user.getUserId(), username.trim(), false, effectiveIp, "ACCOUNT_LOCKED");
                throw new AccountLockedException("Account is locked until " + user.getLockedUntil());
            } else {
                // Lock expired -> auto unlock
                user.setAccountStatus(UserAccountStatus.ACTIVE);
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
                appUserDao.update(user);
            }
        } else if (user.getAccountStatus() == UserAccountStatus.DISABLED) {
            recordLoginAttempt(user.getUserId(), username.trim(), false, effectiveIp, "ACCOUNT_DISABLED");
            throw new AccountLockedException("Account is disabled.");
        }

        // 4. Verify Password
        boolean passwordValid = PasswordUtils.verifyPassword(password, user.getPasswordHash(), user.getPasswordSalt());
        if (!passwordValid) {
            int newAttempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(newAttempts);

            boolean newlyLocked = false;
            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setAccountStatus(UserAccountStatus.LOCKED);
                user.setLockedUntil(now.plusMinutes(15));
                newlyLocked = true;
            }
            appUserDao.update(user);

            String reason = newlyLocked ? "MAX_FAILED_ATTEMPTS_EXCEEDED" : "INVALID_CREDENTIALS";
            recordLoginAttempt(user.getUserId(), username.trim(), false, effectiveIp, reason);

            if (newlyLocked) {
                throw new AccountLockedException("Account locked due to " + MAX_FAILED_ATTEMPTS + " consecutive failed login attempts.");
            } else {
                throw new AuthenticationException("Invalid username or password.");
            }
        }

        // 5. Login Successful
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setAccountStatus(UserAccountStatus.ACTIVE);
        appUserDao.update(user);

        // Load roles
        Set<RoleCode> roleCodes = loadUserRoleCodes(user.getUserId());

        // Load profiles
        Customer customer = null;
        if (user.getCustomerId() != null) {
            customer = customerDao.findById(user.getCustomerId()).orElse(null);
        }

        ProvisioningEngineer engineer = null;
        if (provisioningEngineerDao != null) {
            engineer = provisioningEngineerDao.findAll().stream()
                    .filter(e -> Objects.equals(e.getUserId(), user.getUserId()))
                    .findFirst().orElse(null);
        }

        recordLoginAttempt(user.getUserId(), username.trim(), true, effectiveIp, null);
        return new UserSession(user.getUserId(), user.getUsername(), customer, engineer, roleCodes);
    }

    @Override
    public Customer registerCustomer(Customer customer, String username, String rawPassword) {
        UserValidator.validateCustomerRegistration(customer, username, rawPassword);

        // Pre-validation checks
        if (appUserDao.findByUsername(username.trim()).isPresent()) {
            throw new TelecomDomainException("Username '" + username + "' is already taken.");
        }
        if (customerDao.findByEmail(customer.getEmail().trim()).isPresent()) {
            throw new TelecomDomainException("Customer email '" + customer.getEmail() + "' is already registered.");
        }

        if (customer.getCustomerNumber() == null || customer.getCustomerNumber().trim().isEmpty()) {
            customer.setCustomerNumber("CUST-" + System.currentTimeMillis());
        }
        if (customer.getIdentityStatus() == null) {
            customer.setIdentityStatus(IdentityStatus.VERIFIED);
        }
        if (customer.getAccountStatus() == null) {
            customer.setAccountStatus(CustomerAccountStatus.ACTIVE);
        }
        if (customer.getRegistrationDate() == null) {
            customer.setRegistrationDate(LocalDate.now());
        }

        Connection conn = DatabaseConnection.getConnection();
        DatabaseConnection.setThreadConnection(conn);
        try {
            JdbcTransactionManager.begin(conn);

            // 1. Save Customer
            long customerId = customerDao.save(customer);
            customer.setCustomerId(customerId);

            // 2. Hash Password & Save AppUser
            String salt = PasswordUtils.generateSalt();
            String passwordHash = PasswordUtils.hashPassword(rawPassword, salt);

            AppUser appUser = new AppUser(username.trim(), passwordHash, salt);
            appUser.setCustomerId(customerId);
            appUser.setAccountStatus(UserAccountStatus.ACTIVE);
            appUser.setFailedAttempts(0);
            appUser.setLockedUntil(null);

            long userId = appUserDao.save(appUser);
            appUser.setUserId(userId);

            // 3. Assign Role
            Optional<AppRole> customerRoleOpt = appRoleDao.findByRoleCode(RoleCode.CUSTOMER.name());
            if (!customerRoleOpt.isPresent()) {
                throw new TelecomDomainException("Role 'CUSTOMER' not found in database.");
            }

            AppUserRole userRole = new AppUserRole(userId, customerRoleOpt.get().getRoleId());
            userRole.setAssignedAt(LocalDateTime.now());
            appUserRoleDao.save(userRole);

            JdbcTransactionManager.commit(conn);
            return customer;
        } catch (Exception e) {
            if (conn != null) {
                JdbcTransactionManager.rollback(conn);
            }
            throw new TelecomDomainException("Customer registration failed: " + e.getMessage(), e);
        } finally {
            DatabaseConnection.clearThreadConnection();
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public String generateOtp(Long userId, OtpPurpose purpose) {
        return otpService.generateOtp(userId, purpose);
    }

    @Override
    public String generateOtpForUsername(String username, OtpPurpose purpose) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        AppUser user = appUserDao.findByUsername(username.trim())
                .orElseThrow(() -> new TelecomDomainException("User not found for username: " + username));
        return generateOtp(user.getUserId(), purpose);
    }

    @Override
    public void verifyOtp(Long userId, OtpPurpose purpose, String otpInput) {
        otpService.verifyOtp(userId, purpose, otpInput);
    }

    @Override
    public void verifyOtpForUsername(String username, OtpPurpose purpose, String otpInput) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        AppUser user = appUserDao.findByUsername(username.trim())
                .orElseThrow(() -> new TelecomDomainException("User not found for username: " + username));
        verifyOtp(user.getUserId(), purpose, otpInput);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }
        AppUser user = appUserDao.findById(userId)
                .orElseThrow(() -> new TelecomDomainException("User not found for ID: " + userId));

        boolean oldValid = PasswordUtils.verifyPassword(oldPassword, user.getPasswordHash(), user.getPasswordSalt());
        if (!oldValid) {
            throw new AuthenticationException("Current password is incorrect.");
        }

        UserValidator.validatePassword(newPassword);

        String newSalt = PasswordUtils.generateSalt();
        String newHash = PasswordUtils.hashPassword(newPassword, newSalt);

        user.setPasswordSalt(newSalt);
        user.setPasswordHash(newHash);
        appUserDao.update(user);
    }

    @Override
    public void resetPassword(String username, String otpInput, String newPassword) {
        UserValidator.validateUsername(username);
        AppUser user = appUserDao.findByUsername(username.trim())
                .orElseThrow(() -> new AuthenticationException("User not found with username: " + username));

        otpService.verifyOtp(user.getUserId(), OtpPurpose.PASSWORD_RESET, otpInput);
        UserValidator.validatePassword(newPassword);

        String newSalt = PasswordUtils.generateSalt();
        String newHash = PasswordUtils.hashPassword(newPassword, newSalt);

        user.setPasswordSalt(newSalt);
        user.setPasswordHash(newHash);
        user.setAccountStatus(UserAccountStatus.ACTIVE);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        appUserDao.update(user);
    }

    @Override
    public void lockAccount(Long userId, String reason) {
        if (userId == null) throw new IllegalArgumentException("User ID cannot be null.");
        AppUser user = appUserDao.findById(userId)
                .orElseThrow(() -> new TelecomDomainException("User not found for ID: " + userId));
        user.setAccountStatus(UserAccountStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusYears(100)); // manual lock
        appUserDao.update(user);
    }

    @Override
    public void unlockAccount(Long userId) {
        if (userId == null) throw new IllegalArgumentException("User ID cannot be null.");
        AppUser user = appUserDao.findById(userId)
                .orElseThrow(() -> new TelecomDomainException("User not found for ID: " + userId));
        user.setAccountStatus(UserAccountStatus.ACTIVE);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        appUserDao.update(user);
    }

    private void recordLoginAttempt(Long userId, String username, boolean success, String ipAddress, String failureReason) {
        try {
            LoginHistory history = new LoginHistory(username, success);
            history.setUserId(userId);
            history.setAttemptedAt(LocalDateTime.now());
            history.setIpAddress(ipAddress != null ? ipAddress : DEFAULT_CONSOLE_IP);
            history.setFailureReason(failureReason);
            loginHistoryDao.save(history);
        } catch (Exception e) {
            throw new TelecomDomainException("Failed to audit login attempt for user '" + username + "': " + e.getMessage(), e);
        }
    }

    private Set<RoleCode> loadUserRoleCodes(Long userId) {
        Set<RoleCode> roleCodes = new HashSet<>();
        List<AppUserRole> userRoles = appUserRoleDao.findByUserId(userId);
        List<AppRole> allRoles = appRoleDao.findAll();

        for (AppUserRole ur : userRoles) {
            for (AppRole r : allRoles) {
                if (Objects.equals(ur.getRoleId(), r.getRoleId())) {
                    if (r.getRoleCode() != null) {
                        roleCodes.add(r.getRoleCode());
                    }
                }
            }
        }
        return roleCodes;
    }
}
