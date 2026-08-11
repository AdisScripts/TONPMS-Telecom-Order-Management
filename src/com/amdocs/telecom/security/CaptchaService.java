package com.amdocs.telecom.security;

import com.amdocs.telecom.exception.InvalidCaptchaException;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory CAPTCHA generation and verification service. */
public class CaptchaService {

    private static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final int CAPTCHA_LENGTH = 6;
    private static final long EXPIRATION_MS = 3 * 60 * 1000L; // 3 minutes

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaItem> captchaStore = new ConcurrentHashMap<>();

    public static class CaptchaChallenge {
        private final String challengeId;
        private final String captchaText;

        public CaptchaChallenge(String challengeId, String captchaText) {
            this.challengeId = challengeId;
            this.captchaText = captchaText;
        }

        public String getChallengeId() { return challengeId; }
        public String getCaptchaText() { return captchaText; }
    }

    private static class CaptchaItem {
        private final String code;
        private final long createdAt;

        public CaptchaItem(String code, long createdAt) {
            this.code = code;
            this.createdAt = createdAt;
        }
    }

    /** Generates a new CAPTCHA challenge and stores it in memory. */
    public CaptchaChallenge generateCaptcha() {
        StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        String captchaText = sb.toString();
        String challengeId = UUID.randomUUID().toString();
        captchaStore.put(challengeId, new CaptchaItem(captchaText, System.currentTimeMillis()));
        return new CaptchaChallenge(challengeId, captchaText);
    }

    /**
     * Verifies the user's CAPTCHA input.
     * CAPTCHAs are strictly single-use and removed immediately upon verification.
     */
    public void verifyCaptcha(String challengeId, String userInput) {
        if (challengeId == null || userInput == null || userInput.trim().isEmpty()) {
            throw new InvalidCaptchaException("CAPTCHA input and challenge ID cannot be empty.");
        }
        CaptchaItem item = captchaStore.remove(challengeId);
        if (item == null) {
            throw new InvalidCaptchaException("Invalid or expired CAPTCHA challenge ID.");
        }
        if (System.currentTimeMillis() - item.createdAt > EXPIRATION_MS) {
            throw new InvalidCaptchaException("CAPTCHA has expired. Please request a new one.");
        }
        if (!item.code.equalsIgnoreCase(userInput.trim())) {
            throw new InvalidCaptchaException("Incorrect CAPTCHA entered.");
        }
    }

    /** Clears all stored CAPTCHAs (for testing or maintenance). */
    public void clear() {
        captchaStore.clear();
    }
}
