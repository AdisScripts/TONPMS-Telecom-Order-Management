package com.amdocs.telecom.main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MainApplicationTest {

    public static void main(String[] args) {
        System.out.println("Running MainApplicationTest...");

        // Simulated user CLI flow:
        // 1. Select option 2 (Customer Registration)
        // 2. Provide registration inputs (Name, Email, Phone, Type, Username, Password, Captcha)
        // 3. Select option 7 (Exit application)
        String inputData = "2\nTest User\ntestuser@example.com\n9876543210\n1\ntestuser123\nPassword@123\nWRONG_CAPTCHA\n7\n";

        ByteArrayInputStream in = new ByteArrayInputStream(inputData.getBytes());
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes);

        MainApplication app = new MainApplication(in, out);
        app.run();

        String output = outBytes.toString();

        require(output.contains("TELECOM ORDER & PROVISIONING SYSTEM"), "Console missing main header.");
        require(output.contains("CUSTOMER REGISTRATION"), "Console missing registration section.");
        require(output.contains("REGISTRATION FAILED"), "Console missing registration validation execution.");
        require(output.contains("Exiting TONPMS Telecom Order System. Goodbye!"), "Console missing exit message.");
        require(!app.getSchedulerManager().isRunning(), "SchedulerManager should be stopped after application exit.");

        System.out.println("PASS: MainApplicationTest completed successfully with full CLI flow testing.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }
}
