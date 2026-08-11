package com.amdocs.telecom.security;

import com.amdocs.telecom.dao.OtpChallengeDao;
import com.amdocs.telecom.exception.InvalidOtpException;
import com.amdocs.telecom.model.OtpChallenge;
import com.amdocs.telecom.model.OtpPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class OtpServiceTest {

    public static void main(String[] args) {
        System.out.println("Running OtpServiceTest...");

        testOtpGenerationAndVerification();
        testInvalidOtpCode();
        testMaxOtpAttemptsExceeded();

        System.out.println("PASS: OtpServiceTest completed successfully.");
    }

    private static void testOtpGenerationAndVerification() {
        InMemoryOtpChallengeDao dao = new InMemoryOtpChallengeDao();
        OtpService otpService = new OtpService(dao);

        Long userId = 101L;
        OtpPurpose purpose = OtpPurpose.LOGIN_VERIFICATION;

        String otpCode = otpService.generateOtp(userId, purpose);
        if (otpCode == null || otpCode.length() != 6) {
            throw new AssertionError("Generated OTP must be a 6-digit numeric string.");
        }

        // Verification should succeed
        otpService.verifyOtp(userId, purpose, otpCode);

        // Verification again should fail because consumed
        try {
            otpService.verifyOtp(userId, purpose, otpCode);
            throw new AssertionError("Verifying consumed OTP should fail.");
        } catch (InvalidOtpException expected) {
            // Expected
        }
    }

    private static void testInvalidOtpCode() {
        InMemoryOtpChallengeDao dao = new InMemoryOtpChallengeDao();
        OtpService otpService = new OtpService(dao);

        Long userId = 102L;
        OtpPurpose purpose = OtpPurpose.LOGIN_VERIFICATION;

        otpService.generateOtp(userId, purpose);

        try {
            otpService.verifyOtp(userId, purpose, "000000");
            throw new AssertionError("Verifying wrong OTP code should fail.");
        } catch (InvalidOtpException expected) {
            // Expected
        }
    }

    private static void testMaxOtpAttemptsExceeded() {
        InMemoryOtpChallengeDao dao = new InMemoryOtpChallengeDao();
        OtpService otpService = new OtpService(dao);

        Long userId = 103L;
        OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

        otpService.generateOtp(userId, purpose);

        // Fail 3 times
        for (int i = 1; i <= 3; i++) {
            try {
                otpService.verifyOtp(userId, purpose, "99999" + i);
            } catch (InvalidOtpException e) {
                // expected
            }
        }

        // 4th attempt should complain about max attempts
        try {
            otpService.verifyOtp(userId, purpose, "999999");
            throw new AssertionError("4th attempt after 3 failures must fail.");
        } catch (InvalidOtpException expected) {
            // Expected
        }
    }

    private static class InMemoryOtpChallengeDao implements OtpChallengeDao {
        private final List<OtpChallenge> challenges = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        @Override
        public long save(OtpChallenge entity) {
            entity.setOtpId(idGenerator.getAndIncrement());
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
        public List<OtpChallenge> findAll() {
            return new ArrayList<>(challenges);
        }

        @Override
        public boolean update(OtpChallenge entity) {
            for (int i = 0; i < challenges.size(); i++) {
                if (challenges.get(i).getOtpId().equals(entity.getOtpId())) {
                    challenges.set(i, entity);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean delete(Long id) {
            return challenges.removeIf(c -> c.getOtpId().equals(id));
        }
    }
}
