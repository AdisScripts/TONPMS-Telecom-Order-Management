package com.amdocs.telecom.controller;

import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.scheduler.SchedulerManager;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.CustomerService;
import com.amdocs.telecom.service.InventoryService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.PaymentService;
import com.amdocs.telecom.service.ProductService;
import com.amdocs.telecom.service.ProvisioningService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

public class AdminController {

    private final CustomerService customerService;
    private final ProductService productService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final ProvisioningService provisioningService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final SchedulerManager schedulerManager;
    private final ReportController reportController;

    public AdminController(CustomerService customerService, ProductService productService,
                           OrderService orderService, InventoryService inventoryService,
                           ProvisioningService provisioningService, PaymentService paymentService,
                           AuditService auditService, SchedulerManager schedulerManager,
                           ReportController reportController) {
        this.customerService = customerService;
        this.productService = productService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.provisioningService = provisioningService;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.schedulerManager = schedulerManager;
        this.reportController = reportController;
    }

    public void runMenu(Scanner scanner, PrintStream out, UserSession session) {
        if (session == null || !session.isActive()) {
            out.println("ERROR: Active administrator session required.");
            return;
        }

        while (session.isActive()) {
            out.println("\n----------------------------------------------------");
            out.println("           ORDER ADMINISTRATOR DASHBOARD");
            out.println("----------------------------------------------------");
            out.println("1. Customer Management");
            out.println("2. Product Management");
            out.println("3. Order Management");
            out.println("4. Inventory Management");
            out.println("5. Provisioning Management");
            out.println("6. Payment Management");
            out.println("7. Failed Orders");
            out.println("8. Audit Logs");
            out.println("9. Reports & Analytics");
            out.println("10. Background Scheduler Control");
            out.println("11. Logout");
            out.print("Choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": handleCustomerManagement(scanner, out, session); break;
                case "2": handleProductManagement(scanner, out, session); break;
                case "3": handleOrderManagement(scanner, out, session); break;
                case "4": handleInventoryManagement(scanner, out, session); break;
                case "5": handleProvisioningManagement(scanner, out, session); break;
                case "6": handlePaymentManagement(scanner, out, session); break;
                case "7": handleFailedOrders(out, session); break;
                case "8": handleAuditLogs(scanner, out); break;
                case "9": reportController.runMenu(scanner, out, session); break;
                case "10": handleSchedulerControl(scanner, out); break;
                case "11":
                    session.invalidate();
                    out.println("Logged out administrator session.");
                    return;
                default:
                    out.println("Invalid option. Please try again.");
            }
        }
    }

    private void handleCustomerManagement(Scanner scanner, PrintStream out, UserSession session) {
        out.println("\n--- CUSTOMER MANAGEMENT ---");
        try {
            List<Customer> list = customerService.getAllCustomers(session);
            out.printf("%-10s %-20s %-25s %-12s %-12s\n", "ID", "Name", "Email", "Type", "Status");
            out.println("----------------------------------------------------------------------------------");
            for (Customer c : list) {
                out.printf("%-10d %-20s %-25s %-12s %-12s\n", c.getCustomerId(), c.getCustomerName(), c.getEmail(), c.getCustomerType(), c.getAccountStatus());
            }

            out.print("Update Customer Account Status? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                out.print("Enter Customer ID: ");
                Long cId = Long.parseLong(scanner.nextLine().trim());
                out.print("Enter New Status (1. ACTIVE, 2. SUSPENDED, 3. CLOSED): ");
                String stChoice = scanner.nextLine().trim();
                CustomerAccountStatus status = CustomerAccountStatus.ACTIVE;
                if ("2".equals(stChoice)) status = CustomerAccountStatus.SUSPENDED;
                else if ("3".equals(stChoice)) status = CustomerAccountStatus.CLOSED;

                customerService.updateAccountStatus(session, cId, status);
                out.println("SUCCESS: Customer account status updated.");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void handleProductManagement(Scanner scanner, PrintStream out, UserSession session) {
        out.println("\n--- PRODUCT CATALOG MANAGEMENT ---");
        try {
            List<TelecomProduct> list = productService.getAllProducts(session);
            out.printf("%-10s %-25s %-15s %-10s %-10s\n", "ID", "Name", "Code", "Price", "Status");
            out.println("-----------------------------------------------------------------------------");
            for (TelecomProduct p : list) {
                out.printf("%-10d %-25s %-15s %-10.2f %-10s\n", p.getProductId(), p.getProductName(), p.getProductCode(), p.getMonthlyPrice(), p.getStatus());
            }

            out.print("Add New Product? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                out.print("Product Code: ");
                String code = scanner.nextLine().trim();
                out.print("Product Name: ");
                String name = scanner.nextLine().trim();
                out.print("Product Type: ");
                String type = scanner.nextLine().trim();
                out.print("Description: ");
                String description = scanner.nextLine().trim();
                out.print("Monthly Fee: ");
                BigDecimal price = new BigDecimal(scanner.nextLine().trim());
                out.print("Activation Fee: ");
                String actFeeStr = scanner.nextLine().trim();
                BigDecimal activationFee = actFeeStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(actFeeStr);
                out.print("Contract Period (Months): ");
                String contractStr = scanner.nextLine().trim();
                int contractPeriod = contractStr.isEmpty() ? 12 : Integer.parseInt(contractStr);

                TelecomProduct newProd = new TelecomProduct(code, name, type, price);
                newProd.setDescription(description);
                newProd.setActivationFee(activationFee);
                newProd.setContractPeriod(contractPeriod);

                productService.createProduct(session, newProd);
                out.println("SUCCESS: New product created.");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void handleOrderManagement(Scanner scanner, PrintStream out, UserSession session) {
        out.println("\n--- ORDER MANAGEMENT ---");
        try {
            List<TelecomOrder> list = orderService.getOrdersByStatus(session, OrderStatus.CREATED);
            out.println("Total Orders in CREATED status: " + list.size());

            out.print("Update Order Status manually? (y/n): ");
            if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
                out.print("Enter Order ID: ");
                Long orderId = Long.parseLong(scanner.nextLine().trim());
                out.print("Enter New Status (1. VALIDATED, 2. PAYMENT_PENDING, 3. CANCELLED, 4. COMPLETED): ");
                String stChoice = scanner.nextLine().trim();
                OrderStatus newStatus = OrderStatus.VALIDATED;
                if ("2".equals(stChoice)) newStatus = OrderStatus.PAYMENT_PENDING;
                else if ("3".equals(stChoice)) newStatus = OrderStatus.CANCELLED;
                else if ("4".equals(stChoice)) newStatus = OrderStatus.COMPLETED;

                orderService.updateOrderStatus(session, orderId, newStatus);
                out.println("SUCCESS: Order status updated.");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void handleInventoryManagement(Scanner scanner, PrintStream out, UserSession session) {
        out.println("\n--- INVENTORY MANAGEMENT ---");
        try {
            List<InventoryItem> items = inventoryService.getAllInventoryItems(session);
            out.println("Total Inventory Items: " + items.size());
            out.println("1. Add Inventory Item");
            out.println("2. Edit Inventory Item");
            out.println("3. Back");
            out.print("Choice: ");

            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                out.print("Item Code: ");
                String code = scanner.nextLine().trim();
                out.print("Warehouse: ");
                String wh = scanner.nextLine().trim();
                out.print("Type (1. SIM, 2. ESIM, 3. ROUTER, 4. MODEM): ");
                String tChoice = scanner.nextLine().trim();
                InventoryItemType type = InventoryItemType.SIM;
                if ("2".equals(tChoice)) type = InventoryItemType.ESIM;
                else if ("3".equals(tChoice)) type = InventoryItemType.ROUTER;
                else if ("4".equals(tChoice)) type = InventoryItemType.MODEM;

                InventoryItem item = new InventoryItem(code, type, wh);
                inventoryService.addInventoryItem(session, item);
                out.println("SUCCESS: Inventory item added.");
            } else if ("2".equals(choice)) {
                out.print("Enter Inventory ID: ");
                Long invId = Long.parseLong(scanner.nextLine().trim());
                out.print("New Item Code: ");
                String code = scanner.nextLine().trim();
                out.print("New Warehouse: ");
                String wh = scanner.nextLine().trim();
                out.print("New Type (1. SIM, 2. ESIM, 3. ROUTER, 4. MODEM): ");
                String tChoice = scanner.nextLine().trim();
                InventoryItemType type = InventoryItemType.SIM;
                if ("2".equals(tChoice)) type = InventoryItemType.ESIM;
                else if ("3".equals(tChoice)) type = InventoryItemType.ROUTER;
                else if ("4".equals(tChoice)) type = InventoryItemType.MODEM;

                InventoryItem updated = inventoryService.updateInventoryItem(session, invId, code, type, wh);
                out.println("SUCCESS: Inventory item #" + updated.getInventoryId() + " updated successfully.");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void handleProvisioningManagement(Scanner scanner, PrintStream out, UserSession session) {
        out.println("\n--- PROVISIONING MANAGEMENT ---");
        try {
            out.print("Enter Order ID to Create Provisioning Request: ");
            Long orderId = Long.parseLong(scanner.nextLine().trim());
            out.print("Service Type (1. SIM_ACTIVATION, 2. ESIM_ACTIVATION, 3. BROADBAND, 4. VPN): ");
            String stChoice = scanner.nextLine().trim();
            ProvisioningType type = ProvisioningType.SIM_ACTIVATION;
            if ("2".equals(stChoice)) type = ProvisioningType.ESIM_ACTIVATION;
            else if ("3".equals(stChoice)) type = ProvisioningType.BROADBAND;
            else if ("4".equals(stChoice)) type = ProvisioningType.VPN;

            ProvisioningRequest req = provisioningService.createProvisioningRequest(session, orderId, type);
            out.println("SUCCESS: Provisioning Request #" + req.getProvisioningId() + " created. Assigned Engineer ID: " + req.getEngineerId());
        } catch (Exception e) {
            out.println("PROVISIONING ERROR: " + e.getMessage());
        }
    }

    private void handlePaymentManagement(Scanner scanner, PrintStream out, UserSession session) {
        out.print("\nEnter Order ID to View Payments: ");
        try {
            Long orderId = Long.parseLong(scanner.nextLine().trim());
            List<OrderPayment> payments = paymentService.getPaymentsForOrder(session, orderId);
            out.println("\n--- PAYMENT HISTORY ---");
            for (OrderPayment p : payments) {
                out.println("Txn Ref: " + p.getTransactionReference() + " | Amount: $" + p.getAmount() + " | Mode: " + p.getPaymentMode() + " | Status: " + p.getStatus());
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void handleFailedOrders(PrintStream out, UserSession session) {
        try {
            List<TelecomOrder> list = orderService.getOrdersByStatus(session, OrderStatus.FAILED);
            out.println("\n--- FAILED ORDERS ---");
            out.printf("%-10s %-18s %-15s %-10s\n", "Order ID", "Order Number", "Status", "Total");
            out.println("-------------------------------------------------------------------");
            for (TelecomOrder o : list) {
                out.printf("%-10d %-18s %-15s %-10.2f\n", o.getOrderId(), o.getOrderNumber(), o.getOrderStatus(), o.getTotalAmount());
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void handleAuditLogs(Scanner scanner, PrintStream out) {
        out.print("\nEnter Entity Type (e.g. TelecomOrder, Customer, InventoryItem): ");
        String entityType = scanner.nextLine().trim();
        out.print("Enter Entity ID: ");
        Long entityId = Long.parseLong(scanner.nextLine().trim());
        List<AuditLog> logs = auditService.getAuditLogsForEntity(entityType, entityId);
        out.println("\n--- AUDIT LOGS ---");
        for (AuditLog l : logs) {
            out.println("[" + l.getCreatedAt() + "] Action: " + l.getAction() + " | Details: " + l.getDetails());
        }
    }

    private void handleSchedulerControl(Scanner scanner, PrintStream out) {
        out.println("\n--- BACKGROUND SCHEDULER CONTROL ---");
        out.println("Current Status: " + (schedulerManager.isRunning() ? "RUNNING" : "STOPPED"));
        out.println("1. Start Scheduler");
        out.println("2. Stop Scheduler");
        out.print("Choice: ");
        String choice = scanner.nextLine().trim();
        if ("1".equals(choice)) {
            schedulerManager.startAll();
            out.println("SUCCESS: Scheduler started.");
        } else if ("2".equals(choice)) {
            schedulerManager.shutdownAll();
            out.println("SUCCESS: Scheduler stopped.");
        }
    }
}
