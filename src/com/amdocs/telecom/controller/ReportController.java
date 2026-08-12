package com.amdocs.telecom.controller;

import com.amdocs.telecom.report.ReportData;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ReportService;

import java.io.PrintStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    public void runMenu(Scanner scanner, PrintStream out, UserSession session) {
        if (session == null || !session.isActive()) {
            out.println("ERROR: Active administrative session required for reports.");
            return;
        }

        while (session.isActive()) {
            out.println("\n----------------------------------------------------");
            out.println("               REPORTS & ANALYTICS MENU");
            out.println("----------------------------------------------------");
            out.println("1. Orders by Date Range");
            out.println("2. Orders by Product");
            out.println("3. Most Ordered Products (Top N)");
            out.println("4. Orders by Status");
            out.println("5. Orders by Customer Type");
            out.println("6. Cancelled Orders");
            out.println("7. Failed Orders");
            out.println("8. Average Order Processing Time");
            out.println("9. Available Inventory Stock");
            out.println("10. Reserved Inventory Stock");
            out.println("11. Inventory Stock by Warehouse");
            out.println("12. Low Inventory Stock Alerts");
            out.println("13. Damaged Inventory Stock");
            out.println("14. Successful Provisioning Requests");
            out.println("15. Failed Provisioning Requests");
            out.println("16. Provisioning by Service Type");
            out.println("17. Average Provisioning Time");
            out.println("18. Engineer Workload & Utilization");
            out.println("19. Product-wise Revenue");
            out.println("20. Monthly Revenue");
            out.println("21. Customer-Type Revenue");
            out.println("22. Payment Mode Analysis (SUCCESS)");
            out.println("23. Top Customers by Revenue");
            out.println("24. Back to Main Menu");
            out.print("Choice: ");

            String choice = scanner.nextLine().trim();
            if ("24".equals(choice)) {
                return;
            }

            ReportData data = null;
            try {
                switch (choice) {
                    case "1":
                        data = reportService.getOrdersByDateRange(session, LocalDate.now().minusDays(30), LocalDate.now().plusDays(1));
                        break;
                    case "2": data = reportService.getOrdersByProduct(session); break;
                    case "3": data = reportService.getMostOrderedProducts(session, 5); break;
                    case "4": data = reportService.getOrdersByStatus(session); break;
                    case "5": data = reportService.getOrdersByCustomerType(session); break;
                    case "6": data = reportService.getCancelledOrders(session); break;
                    case "7": data = reportService.getFailedOrders(session); break;
                    case "8":
                        double avgOrd = reportService.getAverageOrderProcessingTimeMinutes(session);
                        out.println("\nAverage Order Processing Time: " + avgOrd + " minutes");
                        break;
                    case "9": data = reportService.getAvailableInventory(session); break;
                    case "10": data = reportService.getReservedInventory(session); break;
                    case "11": data = reportService.getInventoryByWarehouse(session); break;
                    case "12": data = reportService.getLowInventory(session, 10); break;
                    case "13": data = reportService.getDamagedInventory(session); break;
                    case "14": data = reportService.getSuccessfulProvisioningRequests(session); break;
                    case "15": data = reportService.getFailedProvisioningRequests(session); break;
                    case "16": data = reportService.getProvisioningByServiceType(session); break;
                    case "17":
                        double avgProv = reportService.getAverageProvisioningTimeMinutes(session);
                        out.println("\nAverage Provisioning Time: " + avgProv + " minutes");
                        break;
                    case "18": data = reportService.getEngineerWorkload(session); break;
                    case "19": data = reportService.getProductWiseRevenue(session); break;
                    case "20": data = reportService.getMonthlyRevenue(session, LocalDate.now().getYear()); break;
                    case "21": data = reportService.getCustomerTypeRevenue(session); break;
                    case "22": data = reportService.getPaymentModeAnalysis(session); break;
                    case "23": data = reportService.getTopCustomersByRevenue(session, 5); break;
                    default:
                        out.println("Invalid report option.");
                }

                if (data != null) {
                    displayReport(out, data);
                    promptExport(scanner, out, session, data);
                }
            } catch (Exception e) {
                out.println("REPORT ERROR: " + e.getMessage());
            }
        }
    }

    private void displayReport(PrintStream out, ReportData data) {
        out.println("\n=== " + data.getTitle().toUpperCase() + " ===");
        if (data.getHeaders() != null && !data.getHeaders().isEmpty()) {
            out.println(String.join(" | ", data.getHeaders()));
            out.println("-------------------------------------------------------------------");
        }
        for (List<String> row : data.getRows()) {
            out.println(String.join(" | ", row));
        }
    }

    private void promptExport(Scanner scanner, PrintStream out, UserSession session, ReportData data) {
        out.print("\nExport Report? (1. CSV, 2. TXT, 3. Skip): ");
        String choice = scanner.nextLine().trim();
        if ("1".equals(choice)) {
            out.print("Enter CSV File Path (e.g. reports/summary.csv): ");
            String path = scanner.nextLine().trim();
            boolean success = reportService.exportReportToCsv(session, data, path);
            out.println(success ? "SUCCESS: CSV exported to " + path : "CSV Export Failed.");
        } else if ("2".equals(choice)) {
            out.print("Enter TXT File Path (e.g. reports/summary.txt): ");
            String path = scanner.nextLine().trim();
            boolean success = reportService.exportReportToText(session, data, path);
            out.println(success ? "SUCCESS: TXT exported to " + path : "TXT Export Failed.");
        }
    }
}
