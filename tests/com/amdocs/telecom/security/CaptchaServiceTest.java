package com.amdocs.telecom.security;

import com.amdocs.telecom.exception.InvalidCaptchaException;

public class CaptchaServiceTest {

    public static void main(String[] args) {
        System.out.println("Running CaptchaServiceTest...");

        testCaptchaGenerationAndVerification();
        testSingleUseInvalidation();
        testInvalidCaptchaInput();

        System.out.println("PASS: CaptchaServiceTest completed successfully.");
    }

    private static void testCaptchaGenerationAndVerification() {
        CaptchaService captchaService = new CaptchaService();
        CaptchaService.CaptchaChallenge challenge = captchaService.generateCaptcha();

        if (challenge.getChallengeId() == null || challenge.getCaptchaText() == null) {
            throw new AssertionError("Challenge ID and CAPTCHA text must not be null.");
        }
        if (challenge.getCaptchaText().length() != 6) {
            throw new AssertionError("CAPTCHA length must be 6.");
        }

        captchaService.verifyCaptcha(challenge.getChallengeId(), challenge.getCaptchaText());
    }

    private static void testSingleUseInvalidation() {
        CaptchaService captchaService = new CaptchaService();
        CaptchaService.CaptchaChallenge challenge = captchaService.generateCaptcha();

        captchaService.verifyCaptcha(challenge.getChallengeId(), challenge.getCaptchaText());

        try {
            captchaService.verifyCaptcha(challenge.getChallengeId(), challenge.getCaptchaText());
            throw new AssertionError("Re-using the same CAPTCHA challenge must fail.");
        } catch (InvalidCaptchaException expected) {
            // Expected exception
        }
    }

    private static void testInvalidCaptchaInput() {
        CaptchaService captchaService = new CaptchaService();
        CaptchaService.CaptchaChallenge challenge = captchaService.generateCaptcha();

        try {
            captchaService.verifyCaptcha(challenge.getChallengeId(), "WRONG1");
            throw new AssertionError("Incorrect CAPTCHA text must fail.");
        } catch (InvalidCaptchaException expected) {
            // Expected exception
        }
    }
}
