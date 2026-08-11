package com.amdocs.telecom.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** Password and hash utility implementing SHA-256 with random salt. */
public final class PasswordUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() { }

    /** Generates a 16-byte random salt returned as a 32-character Hex string. */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /** Hashes raw password + salt using SHA-256. Returns Hex string. */
    public static String hashPassword(String rawPassword, String saltHex) {
        if (rawPassword == null || saltHex == null) {
            throw new IllegalArgumentException("Password and salt cannot be null.");
        }
        String salted = rawPassword + saltHex;
        return sha256Hex(salted);
    }

    /** Verifies raw password against stored SHA-256 hash and salt. */
    public static boolean verifyPassword(String rawPassword, String storedHash, String saltHex) {
        if (rawPassword == null || storedHash == null || saltHex == null) {
            return false;
        }
        String computed = hashPassword(rawPassword, saltHex);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /** Hashes direct transient OTP value using SHA-256 without salt (for otp_challenge schema). */
    public static String hashOtpDirect(String otpCode) {
        if (otpCode == null) {
            throw new IllegalArgumentException("OTP code cannot be null.");
        }
        return sha256Hex(otpCode);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable.", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
