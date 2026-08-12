package com.amdocs.telecom.service;

import com.amdocs.telecom.report.ReportData;
import com.amdocs.telecom.security.UserSession;
import java.time.LocalDate;

public interface ReportService {
    // Order Reports
    ReportData getOrdersByDateRange(UserSession session, LocalDate startDate, LocalDate endDate);
    ReportData getOrdersByProduct(UserSession session);
    ReportData getMostOrderedProducts(UserSession session, int limit);
    ReportData getOrdersByStatus(UserSession session);
    ReportData getOrdersByCustomerType(UserSession session);
    ReportData getCancelledOrders(UserSession session);
    ReportData getFailedOrders(UserSession session);
    double getAverageOrderProcessingTimeMinutes(UserSession session);

    // Inventory Reports
    ReportData getAvailableInventory(UserSession session);
    ReportData getReservedInventory(UserSession session);
    ReportData getInventoryByWarehouse(UserSession session);
    ReportData getLowInventory(UserSession session, int threshold);
    ReportData getDamagedInventory(UserSession session);

    // Provisioning Reports
    ReportData getSuccessfulProvisioningRequests(UserSession session);
    ReportData getFailedProvisioningRequests(UserSession session);
    ReportData getProvisioningByServiceType(UserSession session);
    double getAverageProvisioningTimeMinutes(UserSession session);
    ReportData getEngineerWorkload(UserSession session);

    // Revenue Reports
    ReportData getProductWiseRevenue(UserSession session);
    ReportData getMonthlyRevenue(UserSession session, int year);
    ReportData getCustomerTypeRevenue(UserSession session);
    ReportData getPaymentModeAnalysis(UserSession session);
    ReportData getTopCustomersByRevenue(UserSession session, int limit);

    // File Exports
    boolean exportReportToCsv(UserSession session, ReportData reportData, String filePath);
    boolean exportReportToText(UserSession session, ReportData reportData, String filePath);
}
