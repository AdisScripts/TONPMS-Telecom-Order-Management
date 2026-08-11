package com.amdocs.telecom.security;

import com.amdocs.telecom.dao.OtpChallengeDao;
import com.amdocs.telecom.exception.InvalidOtpException;
import com.amdocs.telecom.model.OtpChallenge;
import com.amdocs.telecom.model.OtpPurpose;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Service managing OTP challenge generation, SHA-256 direct hashing, and database persistence. */
public class OtpService {

    private final OtpChallengeDao otpChallengeDao;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeDao otpChallengeDao) {
        this.otpChallengeDao = otpChallengeDao;
    }

    /** Generates a 6-digit numeric OTP, saves its SHA-256 hash to database, and returns the raw OTP. */
    public String generateOtp(Long userId, OtpPurpose purpose) {
        if (userId == null || purpose == null) {
            throw new IllegalArgumentException("User ID and OTP purpose cannot be null.");
        }
        int codeInt = random.nextInt(1_000_000);
        String otpCode = String.format("%06d", codeInt);

        String otpHash = PasswordUtils.hashOtpDirect(otpCode);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        OtpChallenge challenge = new OtpChallenge(userId, purpose, otpHash, expiresAt);
        challenge.setAttempts(0);
        challenge.setConsumedAt(null);
        otpChallengeDao.save(challenge);

        System.out.println("[SIMULATED OTP] Sent OTP '" + otpCode + "' for purpose " + purpose + " to userId " + userId);
        return otpCode;
    }

    /** Verifies user-entered OTP against database records using direct SHA-256 matching. */
    public void verifyOtp(Long userId, OtpPurpose purpose, String otpInput) {
        if (userId == null || purpose == null || otpInput == null || otpInput.trim().isEmpty()) {
            throw new InvalidOtpException("User ID, purpose, and OTP input are required.");
        }

        List<OtpChallenge> userChallenges = otpChallengeDao.findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        Optional<OtpChallenge> latestValid = userChallenges.stream()
                .filter(c -> c.getPurpose() == purpose)
                .filter(c -> c.getConsumedAt() == null)
                .filter(c -> c.getExpiresAt() != null && c.getExpiresAt().isAfter(now))
                .max(Comparator.comparing(OtpChallenge::getOtpId));

        if (!latestValid.isPresent()) {
            throw new InvalidOtpException("No active OTP challenge found or OTP has expired.");
        }

        OtpChallenge challenge = latestValid.get();
        int attempts = (challenge.getAttempts() == null ? 0 : challenge.getAttempts()) + 1;
        challenge.setAttempts(attempts);

        if (attempts > 3) {
            otpChallengeDao.update(challenge);
            throw new InvalidOtpException("Maximum OTP verification attempts (3) exceeded.");
        }

        String inputHash = PasswordUtils.hashOtpDirect(otpInput.trim());
        if (!inputHash.equals(challenge.getOtpHash())) {
            otpChallengeDao.update(challenge);
            throw new InvalidOtpException("Invalid OTP code provided.");
        }

        // OTP verified successfully -> mark consumed
        challenge.setConsumedAt(now);
        otpChallengeDao.update(challenge);
    }
}
