package com.amdocs.telecom.controller;

import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ProvisioningService;

import java.io.PrintStream;
import java.util.Scanner;

public class ProvisioningController {

    private final ProvisioningService provisioningService;

    public ProvisioningController(ProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    public void runMenu(Scanner scanner, PrintStream out, UserSession session) {
        if (session == null || !session.isActive()) {
            out.println("ERROR: Active provisioning engineer session required.");
            return;
        }

        while (session.isActive()) {
            out.println("\n----------------------------------------------------");
            out.println("          PROVISIONING ENGINEER DASHBOARD");
            out.println("----------------------------------------------------");
            out.println("1. View My Active Tasks");
            out.println("2. Complete Provisioning Request (SUCCESS / FAILED)");
            out.println("3. View Workload & Specialization");
            out.println("4. Logout");
            out.print("Choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": viewActiveTasks(scanner, out, session); break;
                case "2": updateTaskStatus(scanner, out, session); break;
                case "3": viewWorkload(out, session); break;
                case "4":
                    session.invalidate();
                    out.println("Logged out provisioning engineer session.");
                    return;
                default:
                    out.println("Invalid option. Please try again.");
            }
        }
    }

    private void viewActiveTasks(Scanner scanner, PrintStream out, UserSession session) {
        out.print("\nEnter Provisioning Request ID to Inspect: ");
        String input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            try {
                Long provId = Long.parseLong(input);
                ProvisioningRequest req = provisioningService.getProvisioningRequestById(session, provId);
                out.println("\n--- PROVISIONING REQUEST DETAILS ---");
                out.println("ID: " + req.getProvisioningId() + " | Order ID: " + req.getOrderId() + " | Service ID: " + req.getServiceId() + " | Status: " + req.getStatus());
            } catch (Exception e) {
                out.println("ERROR: " + e.getMessage());
            }
        } else {
            viewWorkload(out, session);
        }
    }

    private void updateTaskStatus(Scanner scanner, PrintStream out, UserSession session) {
        out.print("\nEnter Provisioning Request ID: ");
        Long provId = Long.parseLong(scanner.nextLine().trim());
        out.print("Enter Status (1. SUCCESS, 2. FAILED): ");
        String choice = scanner.nextLine().trim();
        ProvisioningStatus status = "2".equals(choice) ? ProvisioningStatus.FAILED : ProvisioningStatus.SUCCESS;
        String errMsg = status == ProvisioningStatus.FAILED ? "Network activation error" : null;

        try {
            provisioningService.updateProvisioningStatus(session, provId, status, errMsg);
            out.println("SUCCESS: Provisioning request #" + provId + " updated to " + status + ".");
        } catch (Exception e) {
            out.println("UPDATE FAILED: " + e.getMessage());
        }
    }

    private void viewWorkload(PrintStream out, UserSession session) {
        ProvisioningEngineer eng = session.getEngineer();
        out.println("\n--- ENGINEER PROFILE & WORKLOAD ---");
        if (eng != null) {
            out.println("Employee Code: " + eng.getEmployeeCode());
            out.println("Engineer Name: " + eng.getEngineerName());
            out.println("Specialization: " + eng.getSpecialization());
            out.println("Region: " + eng.getRegion());
            out.println("Active Tasks Count: " + (eng.getActiveTasks() != null ? eng.getActiveTasks() : 0));
        } else {
            out.println("User Username: " + session.getUsername());
            out.println("Roles: " + session.getRoles());
        }
    }
}
