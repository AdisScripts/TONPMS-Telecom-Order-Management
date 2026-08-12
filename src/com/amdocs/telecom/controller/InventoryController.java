package com.amdocs.telecom.controller;

import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.report.ReportData;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.InventoryService;
import com.amdocs.telecom.service.ReportService;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

public class InventoryController {

    private final InventoryService inventoryService;
    private final ReportService reportService;

    public InventoryController(InventoryService inventoryService, ReportService reportService) {
        this.inventoryService = inventoryService;
        this.reportService = reportService;
    }

    public void runMenu(Scanner scanner, PrintStream out, UserSession session) {
        if (session == null || !session.isActive()) {
            out.println("ERROR: Active inventory administrator session required.");
            return;
        }

        while (session.isActive()) {
            out.println("\n----------------------------------------------------");
            out.println("         INVENTORY ADMINISTRATOR DASHBOARD");
            out.println("----------------------------------------------------");
            out.println("1. View Available Stock");
            out.println("2. View Low Inventory Stock Alerts");
            out.println("3. View Stock by Warehouse");
            out.println("4. Logout");
            out.print("Choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": viewAvailableStock(out); break;
                case "2": viewLowInventoryAlerts(scanner, out, session); break;
                case "3": viewStockByWarehouse(out, session); break;
                case "4":
                    session.invalidate();
                    out.println("Logged out inventory administrator session.");
                    return;
                default:
                    out.println("Invalid option. Please try again.");
            }
        }
    }

    private void viewAvailableStock(PrintStream out) {
        List<InventoryItem> items = inventoryService.getInventoryItemsByStatus(InventoryStatus.AVAILABLE);
        out.println("\n--- AVAILABLE INVENTORY STOCK ---");
        out.printf("%-10s %-15s %-15s %-15s\n", "ID", "Code", "Type", "Warehouse");
        out.println("---------------------------------------------------------------");
        for (InventoryItem i : items) {
            out.printf("%-10d %-15s %-15s %-15s\n", i.getInventoryId(), i.getItemCode(), i.getItemType(), i.getWarehouse());
        }
    }

    private void viewLowInventoryAlerts(Scanner scanner, PrintStream out, UserSession session) {
        out.print("\nEnter stock alert threshold (e.g. 5): ");
        int threshold = 5;
        try {
            threshold = Integer.parseInt(scanner.nextLine().trim());
        } catch (Exception ignored) {}

        ReportData data = reportService.getLowInventory(session, threshold);
        out.println("\n--- LOW INVENTORY STOCK ALERTS ---");
        displayReport(out, data);
    }

    private void viewStockByWarehouse(PrintStream out, UserSession session) {
        ReportData data = reportService.getInventoryByWarehouse(session);
        out.println("\n--- INVENTORY BREAKDOWN BY WAREHOUSE ---");
        displayReport(out, data);
    }

    private void displayReport(PrintStream out, ReportData data) {
        if (data.getHeaders() != null && !data.getHeaders().isEmpty()) {
            out.println(String.join(" | ", data.getHeaders()));
            out.println("---------------------------------------------------------------");
        }
        for (List<String> row : data.getRows()) {
            out.println(String.join(" | ", row));
        }
    }
}
