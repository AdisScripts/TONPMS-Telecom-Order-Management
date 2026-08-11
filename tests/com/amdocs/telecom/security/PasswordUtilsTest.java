package com.amdocs.telecom.security;

public class PasswordUtilsTest {

    public static void main(String[] args) {
        System.out.println("Running PasswordUtilsTest...");

        testSaltGeneration();
        testPasswordHashingAndVerification();
        testDirectOtpHashing();

        System.out.println("PASS: PasswordUtilsTest completed successfully.");
    }

    private static void testSaltGeneration() {
        String salt1 = PasswordUtils.generateSalt();
        String salt2 = PasswordUtils.generateSalt();

        assertNotNull(salt1, "Salt 1 must not be null.");
        assertNotNull(salt2, "Salt 2 must not be null.");
        assertEquals(32, salt1.length(), "Salt length must be 32 hex chars.");
        assertTrue(!salt1.equals(salt2), "Random salts must be unique.");
    }

    private static void testPasswordHashingAndVerification() {
        String password = "SecretPassword123";
        String salt = PasswordUtils.generateSalt();

        String hash = PasswordUtils.hashPassword(password, salt);
        assertNotNull(hash, "Password hash must not be null.");
        assertTrue(hash.length() > 0, "Password hash must not be empty.");

        boolean verified = PasswordUtils.verifyPassword(password, hash, salt);
        assertTrue(verified, "Password verification must succeed for matching credentials.");

        boolean wrongPassword = PasswordUtils.verifyPassword("WrongPassword123", hash, salt);
        assertTrue(!wrongPassword, "Password verification must fail for wrong password.");
    }

    private static void testDirectOtpHashing() {
        String otpCode = "123456";
        String hash1 = PasswordUtils.hashOtpDirect(otpCode);
        String hash2 = PasswordUtils.hashOtpDirect(otpCode);

        assertNotNull(hash1, "OTP hash must not be null.");
        assertEquals(hash1, hash2, "Direct SHA-256 hash must be deterministic.");
        assertTrue(!hash1.equals(otpCode), "OTP hash must not equal plain OTP code.");
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
