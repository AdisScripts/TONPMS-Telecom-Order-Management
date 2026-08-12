package com.amdocs.telecom.controller;

import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerSubscription;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.NotificationStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ActivationService;
import com.amdocs.telecom.service.CustomerService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.PaymentService;
import com.amdocs.telecom.service.ProductService;
import com.amdocs.telecom.service.ProvisioningService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CustomerController {

    private final CustomerService customerService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ActivationService activationService;
    private final NotificationService notificationService;
    private final ProvisioningService provisioningService;

    public CustomerController(CustomerService customerService, ProductService productService,
                              OrderService orderService, PaymentService paymentService,
                              ActivationService activationService, NotificationService notificationService,
                              ProvisioningService provisioningService) {
        this.customerService = customerService;
        this.productService = productService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.activationService = activationService;
        this.notificationService = notificationService;
        this.provisioningService = provisioningService;
    }

    public void runMenu(Scanner scanner, PrintStream out, UserSession session) {
        if (session == null || session.getCustomer() == null) {
            out.println("ERROR: Active customer session required.");
            return;
        }

        while (session.isActive()) {
            out.println("\n----------------------------------------------------");
            out.println("               CUSTOMER DASHBOARD");
            out.println("----------------------------------------------------");
            out.println("1. View Profile");
            out.println("2. Browse Products");
            out.println("3. Create Order");
            out.println("4. View My Orders");
            out.println("5. Track Order");
            out.println("6. Make Payment");
            out.println("7. View Active Services");
            out.println("8. Cancel Order");
            out.println("9. Notifications");
            out.println("10. Logout");
            out.print("Choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": viewProfile(out, session); break;
                case "2": browseProducts(out); break;
                case "3": createOrder(scanner, out, session); break;
                case "4": viewMyOrders(out, session); break;
                case "5": trackOrder(scanner, out, session); break;
                case "6": makePayment(scanner, out, session); break;
                case "7": viewActiveServices(out, session); break;
                case "8": cancelOrder(scanner, out, session); break;
                case "9": viewNotifications(out, session); break;
                case "10":
                    session.invalidate();
                    out.println("Logged out successfully.");
                    return;
                default:
                    out.println("Invalid option. Please try again.");
            }
        }
    }

    private void viewProfile(PrintStream out, UserSession session) {
        Customer c = customerService.getCustomerProfile(session.getCustomer().getCustomerId());
        out.println("\n--- CUSTOMER PROFILE ---");
        out.println("Customer Number: " + c.getCustomerNumber());
        out.println("Name: " + c.getCustomerName());
        out.println("Email: " + c.getEmail());
        out.println("Phone: " + c.getMobileNumber());
        out.println("Type: " + c.getCustomerType());
        out.println("Account Status: " + c.getAccountStatus());
        out.println("Identity Status: " + c.getIdentityStatus());
    }

    private void browseProducts(PrintStream out) {
        List<TelecomProduct> list = productService.getAllActiveProducts();
        out.println("\n--- AVAILABLE TELECOM PRODUCTS ---");
        out.printf("%-10s %-25s %-15s %-10s\n", "ID", "Name", "Code", "Price");
        out.println("---------------------------------------------------------------");
        for (TelecomProduct p : list) {
            out.printf("%-10d %-25s %-15s %-10.2f\n", p.getProductId(), p.getProductName(), p.getProductCode(), p.getMonthlyPrice());
        }
    }

    private void createOrder(Scanner scanner, PrintStream out, UserSession session) {
        out.println("\n--- CREATE NEW ORDER ---");
        browseProducts(out);
        out.print("Enter Product ID to order: ");
        Long productId = Long.parseLong(scanner.nextLine().trim());
        out.print("Enter Quantity: ");
        int quantity = Integer.parseInt(scanner.nextLine().trim());

        List<OrderService.OrderItemRequest> items = new ArrayList<>();
        items.add(new OrderService.OrderItemRequest(productId, quantity));

        try {
            TelecomOrder order = orderService.createOrder(session, session.getCustomer().getCustomerId(),
                    OrderType.NEW_CONNECTION, LocalDate.now().plusDays(1), items);
            out.println("SUCCESS: Order created! Order Number: " + order.getOrderNumber() + " | Total: $" + order.getTotalAmount());
        } catch (Exception e) {
            out.println("ORDER CREATION FAILED: " + e.getMessage());
        }
    }

    private void viewMyOrders(PrintStream out, UserSession session) {
        try {
            List<TelecomOrder> orders = orderService.getOrdersByCustomer(session, session.getCustomer().getCustomerId());
            out.println("\n--- MY ORDERS ---");
            out.printf("%-10s %-18s %-15s %-12s %-10s\n", "Order ID", "Order Number", "Status", "Payment", "Total");
            out.println("-------------------------------------------------------------------");
            for (TelecomOrder o : orders) {
                out.printf("%-10d %-18s %-15s %-12s %-10.2f\n", o.getOrderId(), o.getOrderNumber(), o.getOrderStatus(), o.getPaymentStatus(), o.getTotalAmount());
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void trackOrder(Scanner scanner, PrintStream out, UserSession session) {
        out.print("\nEnter Order ID to Track: ");
        Long orderId = Long.parseLong(scanner.nextLine().trim());
        try {
            TelecomOrder o = orderService.getOrderById(session, orderId);
            out.println("\n--- ORDER TRACKING STATUS ---");
            out.println("Order Number: " + o.getOrderNumber());
            out.println("Current Order Status: " + o.getOrderStatus());
            out.println("Payment Status: " + o.getPaymentStatus());
            out.println("Order Tracking Pipeline:");
            out.println("   Order Created");
            out.println("        ↓");
            out.println("   Validated");
            out.println("        ↓");
            out.println("   Payment Confirmed");
            out.println("        ↓");
            out.println("   Inventory Reserved");
            out.println("        ↓");
            out.println("   Provisioning");
            out.println("        ↓");
            out.println("   Activated");
            out.println("        ↓");
            out.println("   Completed");

            List<ProvisioningRequest> reqs = provisioningService.getProvisioningRequestsByOrder(session, orderId);
            if (!reqs.isEmpty()) {
                out.println("Provisioning Details: " + reqs.get(0).getStatus());
            }
        } catch (Exception e) {
            out.println("TRACKING ERROR: " + e.getMessage());
        }
    }

    private void makePayment(Scanner scanner, PrintStream out, UserSession session) {
        viewMyOrders(out, session);
        out.print("\nEnter Order ID to Pay: ");
        Long orderId = Long.parseLong(scanner.nextLine().trim());
        out.print("Payment Mode (1. CARD, 2. UPI, 3. NET_BANKING, 4. BANK_TRANSFER): ");
        String modeChoice = scanner.nextLine().trim();
        PaymentMode mode = PaymentMode.CARD;
        if ("2".equals(modeChoice)) mode = PaymentMode.UPI;
        else if ("3".equals(modeChoice)) mode = PaymentMode.NET_BANKING;
        else if ("4".equals(modeChoice)) mode = PaymentMode.BANK_TRANSFER;

        try {
            TelecomOrder order = orderService.getOrderById(session, orderId);
            OrderPayment payment = paymentService.processPayment(session, orderId, order.getTotalAmount(), mode);
            out.println("SUCCESS: Payment processed! Txn Ref: " + payment.getTransactionReference() + " | Status: " + payment.getStatus());
        } catch (Exception e) {
            out.println("PAYMENT FAILED: " + e.getMessage());
        }
    }

    private void viewActiveServices(PrintStream out, UserSession session) {
        try {
            List<CustomerSubscription> subs = activationService.getCustomerSubscriptions(session, session.getCustomer().getCustomerId());
            out.println("\n--- ACTIVE SERVICES & SUBSCRIPTIONS ---");
            out.printf("%-10s %-20s %-15s %-12s\n", "Sub ID", "Service ID", "Status", "Activated On");
            out.println("-------------------------------------------------------------------");
            for (CustomerSubscription s : subs) {
                out.printf("%-10d %-20s %-15s %-12s\n", s.getSubscriptionId(), s.getServiceId(), s.getStatus(), s.getActivationDate());
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void cancelOrder(Scanner scanner, PrintStream out, UserSession session) {
        out.print("\nEnter Order ID to Cancel: ");
        Long orderId = Long.parseLong(scanner.nextLine().trim());
        try {
            orderService.cancelOrder(session, orderId);
            out.println("SUCCESS: Order #" + orderId + " has been cancelled.");
        } catch (Exception e) {
            out.println("CANCELLATION FAILED: " + e.getMessage());
        }
    }

    private void viewNotifications(PrintStream out, UserSession session) {
        try {
            List<Notification> list = notificationService.getNotificationsForCustomer(session, session.getCustomer().getCustomerId());
            out.println("\n--- MY NOTIFICATIONS ---");
            for (Notification n : list) {
                out.println("[" + n.getCreatedAt() + "] " + n.getMessage() + " (Status: " + n.getStatus() + ")");
                NotificationStatus status = n.getStatus();
                if (status == NotificationStatus.SENT || status == NotificationStatus.PENDING) {
                    notificationService.markAsRead(session, n.getNotificationId());
                }
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }
}
