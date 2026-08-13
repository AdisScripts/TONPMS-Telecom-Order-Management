package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.*;
import com.amdocs.telecom.exception.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.service.impl.AuthenticationServiceImpl;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class AuthenticationServiceTest {

    public static void main(String[] args) {
        System.out.println("Running AuthenticationServiceTest...");

        TestEnvironment env = createTestEnvironment();
        AuthenticationService authService = env.authService;
        AuthorizationService authzService = new AuthorizationServiceImpl();

        testCustomerRegistration(authService, env);
        testLoginSuccess(authService, env);
        testLoginCaptchaFailure(authService, env);
        testLoginAccountLocking(authService, env);
        testPasswordChange(authService, env);
        testPasswordResetViaOtp(authService, env);
        testRoleAuthorization(authService, authzService, env);

        System.out.println("PASS: AuthenticationServiceTest completed successfully.");
    }

    private static void testCustomerRegistration(AuthenticationService authService, TestEnvironment env) {
        Customer customer = new Customer();
        customer.setCustomerName("Alice Smith");
        customer.setEmail("alice@example.com");
        customer.setMobileNumber("+1234567890");
        customer.setAddress("123 Main St");
        customer.setCity("Metropolis");
        customer.setCustomerType(CustomerType.INDIVIDUAL);

        Customer registered = authService.registerCustomer(customer, "alice_smith", "Password123!");
        assertNotNull(registered.getCustomerId(), "Registered customer ID must not be null.");

        Optional<AppUser> userOpt = env.userDao.findByUsername("alice_smith");
        assertTrue(userOpt.isPresent(), "AppUser 'alice_smith' must exist.");
        assertEquals(registered.getCustomerId(), userOpt.get().getCustomerId(), "AppUser must link to Customer.");

        List<AppUserRole> userRoles = env.userRoleDao.findByUserId(userOpt.get().getUserId());
        assertTrue(!userRoles.isEmpty(), "User roles must not be empty.");
        assertNotNull(userRoles.get(0).getAssignedAt(), "AppUserRole assignedAt timestamp must not be null.");
    }

    private static void testLoginSuccess(AuthenticationService authService, TestEnvironment env) {
        CaptchaService.CaptchaChallenge challenge = env.captchaService.generateCaptcha();
        UserSession session = authService.login("alice_smith", "Password123!", challenge.getChallengeId(), challenge.getCaptchaText());

        assertNotNull(session, "UserSession must not be null.");
        assertEquals("alice_smith", session.getUsername(), "Username must match.");
        assertTrue(session.hasRole(RoleCode.CUSTOMER), "User must have CUSTOMER role.");

        // Check LoginHistory recorded CONSOLE IP
        List<LoginHistory> histories = env.loginHistoryDao.findByUsernameAttempted("alice_smith");
        assertTrue(!histories.isEmpty(), "Login history must be recorded.");
        assertEquals("CONSOLE", histories.get(0).getIpAddress(), "IP Address must be CONSOLE.");
        assertTrue(histories.get(0).getSuccess(), "Login history success must be true.");
    }

    private static void testLoginCaptchaFailure(AuthenticationService authService, TestEnvironment env) {
        try {
            authService.login("alice_smith", "Password123!", "invalid_captcha_id", "WRONG1");
            throw new AssertionError("Login with invalid CAPTCHA must fail.");
        } catch (InvalidCaptchaException expected) {
            // Expected
        }
    }

    private static void testLoginAccountLocking(AuthenticationService authService, TestEnvironment env) {
        // Register Bob
        Customer customer = new Customer();
        customer.setCustomerName("Bob Builder");
        customer.setEmail("bob@example.com");
        customer.setMobileNumber("+1987654321");
        customer.setAddress("456 Oak Ave");
        customer.setCity("Springfield");
        customer.setCustomerType(CustomerType.SME);

        authService.registerCustomer(customer, "bob_builder", "SecretPass1");

        // Attempt 1 with wrong password
        CaptchaService.CaptchaChallenge c1 = env.captchaService.generateCaptcha();
        try {
            authService.login("bob_builder", "WrongPass1", c1.getChallengeId(), c1.getCaptchaText());
        } catch (AuthenticationException e) { /* Expected */ }

        // Attempt 2 with wrong password
        CaptchaService.CaptchaChallenge c2 = env.captchaService.generateCaptcha();
        try {
            authService.login("bob_builder", "WrongPass2", c2.getChallengeId(), c2.getCaptchaText());
        } catch (AuthenticationException e) { /* Expected */ }

        // Attempt 3 with wrong password -> triggers account lock!
        CaptchaService.CaptchaChallenge c3 = env.captchaService.generateCaptcha();
        try {
            authService.login("bob_builder", "WrongPass3", c3.getChallengeId(), c3.getCaptchaText());
            throw new AssertionError("3rd failed attempt must lock account.");
        } catch (AccountLockedException expected) {
            // Expected lock
        }

        // Attempt 4 even with correct password -> still locked!
        CaptchaService.CaptchaChallenge c4 = env.captchaService.generateCaptcha();
        try {
            authService.login("bob_builder", "SecretPass1", c4.getChallengeId(), c4.getCaptchaText());
            throw new AssertionError("Login on locked account must fail even with correct password.");
        } catch (AccountLockedException expected) {
            // Expected lock
        }

        // Admin unlocks Bob
        AppUser bobUser = env.userDao.findByUsername("bob_builder").get();
        authService.unlockAccount(bobUser.getUserId());

        // Now login should succeed!
        CaptchaService.CaptchaChallenge c5 = env.captchaService.generateCaptcha();
        UserSession session = authService.login("bob_builder", "SecretPass1", c5.getChallengeId(), c5.getCaptchaText());
        assertNotNull(session, "Bob should log in successfully after admin unlock.");
    }

    private static void testPasswordChange(AuthenticationService authService, TestEnvironment env) {
        AppUser user = env.userDao.findByUsername("alice_smith").get();

        authService.changePassword(user.getUserId(), "Password123!", "NewStrongPass1!");

        // Login with old password should fail
        CaptchaService.CaptchaChallenge c1 = env.captchaService.generateCaptcha();
        try {
            authService.login("alice_smith", "Password123!", c1.getChallengeId(), c1.getCaptchaText());
        } catch (AuthenticationException expected) { /* Expected */ }

        // Login with new password should succeed
        CaptchaService.CaptchaChallenge c2 = env.captchaService.generateCaptcha();
        UserSession session = authService.login("alice_smith", "NewStrongPass1!", c2.getChallengeId(), c2.getCaptchaText());
        assertNotNull(session, "Login with new password must succeed.");
    }

    private static void testPasswordResetViaOtp(AuthenticationService authService, TestEnvironment env) {
        AppUser user = env.userDao.findByUsername("alice_smith").get();

        // Generate OTP
        String otpCode = authService.generateOtp(user.getUserId(), OtpPurpose.PASSWORD_RESET);

        // Reset password
        authService.resetPassword("alice_smith", otpCode, "ResetPass999!");

        // Login with reset password
        CaptchaService.CaptchaChallenge c = env.captchaService.generateCaptcha();
        UserSession session = authService.login("alice_smith", "ResetPass999!", c.getChallengeId(), c.getCaptchaText());
        assertNotNull(session, "Login after OTP password reset must succeed.");
    }

    private static void testRoleAuthorization(AuthenticationService authService, AuthorizationService authzService, TestEnvironment env) {
        CaptchaService.CaptchaChallenge c = env.captchaService.generateCaptcha();
        UserSession session = authService.login("alice_smith", "ResetPass999!", c.getChallengeId(), c.getCaptchaText());

        // Alice is a CUSTOMER
        authzService.checkAccess(session, RoleCode.CUSTOMER);

        // Alice is NOT ORDER_ADMINISTRATOR
        try {
            authzService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
            throw new AssertionError("Customer should not have ORDER_ADMINISTRATOR access.");
        } catch (AccessDeniedException expected) {
            // Expected
        }
    }

    private static TestEnvironment createTestEnvironment() {
        InMemoryAppUserDao userDao = new InMemoryAppUserDao();
        InMemoryAppRoleDao roleDao = new InMemoryAppRoleDao();
        InMemoryAppUserRoleDao userRoleDao = new InMemoryAppUserRoleDao();
        InMemoryCustomerDao customerDao = new InMemoryCustomerDao();
        InMemoryProvisioningEngineerDao engineerDao = new InMemoryProvisioningEngineerDao();
        InMemoryLoginHistoryDao loginHistoryDao = new InMemoryLoginHistoryDao();
        InMemoryOtpChallengeDao otpDao = new InMemoryOtpChallengeDao();

        // Seed default roles using AppRole(RoleCode, String)
        roleDao.save(new AppRole(RoleCode.CUSTOMER, "Customer"));
        roleDao.save(new AppRole(RoleCode.ORDER_ADMINISTRATOR, "Order Administrator"));
        roleDao.save(new AppRole(RoleCode.PROVISIONING_ENGINEER, "Provisioning Engineer"));
        roleDao.save(new AppRole(RoleCode.INVENTORY_ADMINISTRATOR, "Inventory Administrator"));

        CaptchaService captchaService = new CaptchaService();
        OtpService otpService = new OtpService(otpDao);

        AuthenticationService authService = new AuthenticationServiceImpl(
                userDao, roleDao, userRoleDao, customerDao, engineerDao, loginHistoryDao, captchaService, otpService
        );

        TestEnvironment env = new TestEnvironment();
        env.userDao = userDao;
        env.roleDao = roleDao;
        env.userRoleDao = userRoleDao;
        env.customerDao = customerDao;
        env.loginHistoryDao = loginHistoryDao;
        env.captchaService = captchaService;
        env.authService = authService;
        return env;
    }

    private static class TestEnvironment {
        InMemoryAppUserDao userDao;
        InMemoryAppRoleDao roleDao;
        InMemoryAppUserRoleDao userRoleDao;
        InMemoryCustomerDao customerDao;
        InMemoryLoginHistoryDao loginHistoryDao;
        CaptchaService captchaService;
        AuthenticationService authService;
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) throw new AssertionError(msg);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    // In-memory DAOs for unit testing
    private static class InMemoryAppUserDao implements AppUserDao {
        Map<Long, AppUser> users = new HashMap<>();
        AtomicLong idGen = new AtomicLong(1);

        @Override
        public long save(AppUser entity) {
            entity.setUserId(idGen.getAndIncrement());
            users.put(entity.getUserId(), entity);
            return entity.getUserId();
        }

        @Override
        public Optional<AppUser> findById(Long id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<AppUser> findByUsername(String username) {
            return users.values().stream().filter(u -> u.getUsername().equals(username)).findFirst();
        }

        @Override
        public List<AppUser> findAll() { return new ArrayList<>(users.values()); }

        @Override
        public boolean update(AppUser entity) {
            users.put(entity.getUserId(), entity);
            return true;
        }

        @Override
        public boolean delete(Long id) { return users.remove(id) != null; }
    }

    private static class InMemoryAppRoleDao implements AppRoleDao {
        Map<Short, AppRole> roles = new HashMap<>();
        short idGen = 1;

        @Override
        public long save(AppRole entity) {
            entity.setRoleId(idGen++);
            roles.put(entity.getRoleId(), entity);
            return entity.getRoleId();
        }

        @Override
        public Optional<AppRole> findById(Long id) {
            return Optional.ofNullable(roles.get(id.shortValue()));
        }

        @Override
        public Optional<AppRole> findByRoleCode(String roleCode) {
            return roles.values().stream().filter(r -> r.getRoleCode() != null && r.getRoleCode().name().equals(roleCode)).findFirst();
        }

        @Override
        public List<AppRole> findAll() { return new ArrayList<>(roles.values()); }

        @Override
        public boolean update(AppRole entity) { roles.put(entity.getRoleId(), entity); return true; }

        @Override
        public boolean delete(Long id) { return roles.remove(id.shortValue()) != null; }
    }

    private static class InMemoryAppUserRoleDao implements AppUserRoleDao {
        List<AppUserRole> userRoles = new ArrayList<>();

        @Override
        public boolean save(AppUserRole entity) {
            if (entity.getAssignedAt() == null) {
                throw new IllegalArgumentException("AppUserRole assignedAt must not be null.");
            }
            userRoles.add(entity);
            return true;
        }

        @Override
        public Optional<AppUserRole> findByUserIdAndRoleId(Long userId, Short roleId) {
            return userRoles.stream().filter(ur -> ur.getUserId().equals(userId) && ur.getRoleId().equals(roleId)).findFirst();
        }

        @Override
        public List<AppUserRole> findByUserId(Long userId) {
            return userRoles.stream().filter(ur -> ur.getUserId().equals(userId)).collect(Collectors.toList());
        }

        @Override
        public boolean delete(Long userId, Short roleId) {
            return userRoles.removeIf(ur -> ur.getUserId().equals(userId) && ur.getRoleId().equals(roleId));
        }
    }

    private static class InMemoryCustomerDao implements CustomerDao {
        Map<Long, Customer> customers = new HashMap<>();
        AtomicLong idGen = new AtomicLong(1);

        @Override
        public long save(Customer entity) {
            entity.setCustomerId(idGen.getAndIncrement());
            customers.put(entity.getCustomerId(), entity);
            return entity.getCustomerId();
        }

        @Override
        public Optional<Customer> findById(Long id) { return Optional.ofNullable(customers.get(id)); }

        @Override
        public Optional<Customer> findByCustomerNumber(String customerNumber) {
            return customers.values().stream().filter(c -> customerNumber.equals(c.getCustomerNumber())).findFirst();
        }

        @Override
        public Optional<Customer> findByEmail(String email) {
            return customers.values().stream().filter(c -> email.equalsIgnoreCase(c.getEmail())).findFirst();
        }

        @Override
        public List<Customer> findAll() { return new ArrayList<>(customers.values()); }

        @Override
        public boolean update(Customer entity) { customers.put(entity.getCustomerId(), entity); return true; }

        @Override
        public boolean delete(Long id) { return customers.remove(id) != null; }
    }

    private static class InMemoryProvisioningEngineerDao implements ProvisioningEngineerDao {
        Map<Long, ProvisioningEngineer> engineers = new HashMap<>();
        AtomicLong idGen = new AtomicLong(1);

        @Override
        public long save(ProvisioningEngineer entity) {
            entity.setEngineerId(idGen.getAndIncrement());
            engineers.put(entity.getEngineerId(), entity);
            return entity.getEngineerId();
        }

        @Override
        public Optional<ProvisioningEngineer> findById(Long id) { return Optional.ofNullable(engineers.get(id)); }

        @Override
        public Optional<ProvisioningEngineer> findByEmployeeCode(String employeeCode) {
            return engineers.values().stream().filter(e -> employeeCode.equals(e.getEmployeeCode())).findFirst();
        }

        @Override
        public List<ProvisioningEngineer> findAll() { return new ArrayList<>(engineers.values()); }

        @Override
        public boolean update(ProvisioningEngineer entity) { engineers.put(entity.getEngineerId(), entity); return true; }

        @Override
        public boolean delete(Long id) { return engineers.remove(id) != null; }
    }

    private static class InMemoryLoginHistoryDao implements LoginHistoryDao {
        List<LoginHistory> histories = new ArrayList<>();
        AtomicLong idGen = new AtomicLong(1);

        @Override
        public long save(LoginHistory entity) {
            entity.setLoginHistoryId(idGen.getAndIncrement());
            histories.add(entity);
            return entity.getLoginHistoryId();
        }

        @Override
        public Optional<LoginHistory> findById(Long id) {
            return histories.stream().filter(h -> h.getLoginHistoryId().equals(id)).findFirst();
        }

        @Override
        public List<LoginHistory> findByUsernameAttempted(String usernameAttempted) {
            return histories.stream().filter(h -> usernameAttempted.equals(h.getUsernameAttempted()))
                    .sorted(Comparator.comparing(LoginHistory::getLoginHistoryId).reversed())
                    .collect(Collectors.toList());
        }

        @Override
        public List<LoginHistory> findAll() { return new ArrayList<>(histories); }

        @Override
        public boolean update(LoginHistory entity) { return true; }

        @Override
        public boolean delete(Long id) { return histories.removeIf(h -> h.getLoginHistoryId().equals(id)); }
    }

    private static class InMemoryOtpChallengeDao implements OtpChallengeDao {
        List<OtpChallenge> challenges = new ArrayList<>();
        AtomicLong idGen = new AtomicLong(1);

        @Override
        public long save(OtpChallenge entity) {
            entity.setOtpId(idGen.getAndIncrement());
            challenges.add(entity);
            return entity.getOtpId();
        }

        @Override
        public Optional<OtpChallenge> findById(Long id) {
            return challenges.stream().filter(c -> c.getOtpId().equals(id)).findFirst();
        }

        @Override
        public List<OtpChallenge> findByUserId(Long userId) {
            return challenges.stream().filter(c -> c.getUserId().equals(userId)).collect(Collectors.toList());
        }

        @Override
        public List<OtpChallenge> findAll() { return new ArrayList<>(challenges); }

        @Override
        public boolean update(OtpChallenge entity) { return true; }

        @Override
        public boolean delete(Long id) { return challenges.removeIf(c -> c.getOtpId().equals(id)); }
    }
}
