package com.amdocs.telecom.main;

import com.amdocs.telecom.controller.AdminController;
import com.amdocs.telecom.controller.AuthenticationController;
import com.amdocs.telecom.controller.CustomerController;
import com.amdocs.telecom.controller.InventoryController;
import com.amdocs.telecom.controller.ProvisioningController;
import com.amdocs.telecom.controller.ReportController;
import com.amdocs.telecom.dao.AppRoleDao;
import com.amdocs.telecom.dao.AppUserDao;
import com.amdocs.telecom.dao.AppUserRoleDao;
import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.CustomerSubscriptionDao;
import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.LoginHistoryDao;
import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.OrderPaymentDao;
import com.amdocs.telecom.dao.OtpChallengeDao;
import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.dao.impl.AppRoleDaoImpl;
import com.amdocs.telecom.dao.impl.AppUserDaoImpl;
import com.amdocs.telecom.dao.impl.AppUserRoleDaoImpl;
import com.amdocs.telecom.dao.impl.AuditLogDaoImpl;
import com.amdocs.telecom.dao.impl.CustomerDaoImpl;
import com.amdocs.telecom.dao.impl.CustomerSubscriptionDaoImpl;
import com.amdocs.telecom.dao.impl.InventoryItemDaoImpl;
import com.amdocs.telecom.dao.impl.LoginHistoryDaoImpl;
import com.amdocs.telecom.dao.impl.NotificationDaoImpl;
import com.amdocs.telecom.dao.impl.OrderItemDaoImpl;
import com.amdocs.telecom.dao.impl.OrderPaymentDaoImpl;
import com.amdocs.telecom.dao.impl.OtpChallengeDaoImpl;
import com.amdocs.telecom.dao.impl.ProvisioningEngineerDaoImpl;
import com.amdocs.telecom.dao.impl.ProvisioningRequestDaoImpl;
import com.amdocs.telecom.dao.impl.TelecomOrderDaoImpl;
import com.amdocs.telecom.dao.impl.TelecomProductDaoImpl;
import com.amdocs.telecom.scheduler.SchedulerManager;
import com.amdocs.telecom.security.CaptchaService;
import com.amdocs.telecom.security.OtpService;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ActivationService;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.AuthenticationService;
import com.amdocs.telecom.service.CustomerService;
import com.amdocs.telecom.service.InventoryService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.PaymentService;
import com.amdocs.telecom.service.ProductService;
import com.amdocs.telecom.service.ProvisioningService;
import com.amdocs.telecom.service.ReportService;
import com.amdocs.telecom.service.impl.ActivationServiceImpl;
import com.amdocs.telecom.service.impl.AuditServiceImpl;
import com.amdocs.telecom.service.impl.AuthenticationServiceImpl;
import com.amdocs.telecom.service.impl.CustomerServiceImpl;
import com.amdocs.telecom.service.impl.InventoryServiceImpl;
import com.amdocs.telecom.service.impl.NotificationServiceImpl;
import com.amdocs.telecom.service.impl.OrderServiceImpl;
import com.amdocs.telecom.service.impl.PaymentServiceImpl;
import com.amdocs.telecom.service.impl.ProductServiceImpl;
import com.amdocs.telecom.service.impl.ProvisioningServiceImpl;
import com.amdocs.telecom.service.impl.ReportServiceImpl;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

public class MainApplication {

    private final InputStream in;
    private final PrintStream out;

    private final SchedulerManager schedulerManager;

    private final AuthenticationController authenticationController;
    private final CustomerController customerController;
    private final AdminController adminController;
    private final InventoryController inventoryController;
    private final ProvisioningController provisioningController;
    private final ReportController reportController;

    public MainApplication() {
        this(System.in, System.out);
    }

    public MainApplication(InputStream in, PrintStream out) {
        this.in = in;
        this.out = out;

        // 1. DAOs
        AppUserDao appUserDao = new AppUserDaoImpl();
        AppRoleDao appRoleDao = new AppRoleDaoImpl();
        AppUserRoleDao appUserRoleDao = new AppUserRoleDaoImpl();
        CustomerDao customerDao = new CustomerDaoImpl();
        ProvisioningEngineerDao provisioningEngineerDao = new ProvisioningEngineerDaoImpl();
        LoginHistoryDao loginHistoryDao = new LoginHistoryDaoImpl();
        OtpChallengeDao otpChallengeDao = new OtpChallengeDaoImpl();
        CustomerSubscriptionDao customerSubscriptionDao = new CustomerSubscriptionDaoImpl();
        TelecomProductDao telecomProductDao = new TelecomProductDaoImpl();
        TelecomOrderDao telecomOrderDao = new TelecomOrderDaoImpl();
        OrderItemDao orderItemDao = new OrderItemDaoImpl();
        InventoryItemDao inventoryItemDao = new InventoryItemDaoImpl();
        ProvisioningRequestDao provisioningRequestDao = new ProvisioningRequestDaoImpl();
        OrderPaymentDao orderPaymentDao = new OrderPaymentDaoImpl();
        NotificationDao notificationDao = new NotificationDaoImpl();
        AuditLogDao auditLogDao = new AuditLogDaoImpl();

        // 2. Security Infrastructure
        CaptchaService captchaService = new CaptchaService();
        OtpService otpService = new OtpService(otpChallengeDao);

        // 3. Services
        AuthenticationService authenticationService = new AuthenticationServiceImpl(appUserDao, appRoleDao, appUserRoleDao, customerDao, provisioningEngineerDao, loginHistoryDao, captchaService, otpService);
        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(telecomProductDao);
        NotificationService notificationService = new NotificationServiceImpl(notificationDao);
        AuditService auditService = new AuditServiceImpl(auditLogDao);

        InventoryService inventoryService = new InventoryServiceImpl(inventoryItemDao, telecomOrderDao, orderItemDao, telecomProductDao);
        ProvisioningService provisioningService = new ProvisioningServiceImpl(provisioningRequestDao, provisioningEngineerDao, telecomOrderDao, customerDao, auditService);
        ActivationService activationService = new ActivationServiceImpl(customerSubscriptionDao, telecomOrderDao, orderItemDao, telecomProductDao, inventoryItemDao, provisioningRequestDao, notificationService, auditService);
        OrderService orderService = new OrderServiceImpl(telecomOrderDao, orderItemDao, customerService, productService);
        PaymentService paymentService = new PaymentServiceImpl(orderPaymentDao, telecomOrderDao, orderService, inventoryService, auditService);

        ReportService reportService = new ReportServiceImpl(
                telecomOrderDao, orderItemDao, telecomProductDao, customerDao,
                inventoryItemDao, provisioningRequestDao, provisioningEngineerDao,
                orderPaymentDao, auditLogDao
        );

        // 4. Scheduler Manager
        this.schedulerManager = new SchedulerManager(
                notificationDao, notificationService, telecomOrderDao, orderService,
                paymentService, provisioningService, activationService, inventoryItemDao,
                telecomProductDao, provisioningRequestDao, orderItemDao, auditService, 1L
        );

        // 5. Controllers
        this.authenticationController = new AuthenticationController(authenticationService, captchaService);
        this.reportController = new ReportController(reportService);
        this.customerController = new CustomerController(customerService, productService, orderService, paymentService, activationService, notificationService, provisioningService);
        this.adminController = new AdminController(customerService, productService, orderService, inventoryService, provisioningService, paymentService, auditService, schedulerManager, reportController);
        this.inventoryController = new InventoryController(inventoryService, reportService);
        this.provisioningController = new ProvisioningController(provisioningService);
    }

    public static void main(String[] args) {
        MainApplication app = new MainApplication();
        app.run();
    }

    public void run() {
        // Start background scheduler
        schedulerManager.startAll();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            schedulerManager.shutdownAll();
        }));

        Scanner scanner = new Scanner(in);
        out.println("====================================================");
        out.println("       TELECOM ORDER & PROVISIONING SYSTEM");
        out.println("====================================================");

        boolean running = true;
        while (running) {
            out.println("\n--- MAIN MENU ---");
            out.println("1. Customer Login");
            out.println("2. Customer Registration");
            out.println("3. Order Administrator Login");
            out.println("4. Provisioning Engineer Login");
            out.println("5. Inventory Administrator Login");
            out.println("6. Forgot Password");
            out.println("7. Exit");
            out.print("Choice: ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": {
                    UserSession session = authenticationController.handleCustomerLogin(scanner, out);
                    if (session != null) {
                        customerController.runMenu(scanner, out, session);
                    }
                    break;
                }
                case "2": {
                    authenticationController.handleCustomerRegistration(scanner, out);
                    break;
                }
                case "3": {
                    UserSession session = authenticationController.handleAdminLogin(scanner, out);
                    if (session != null) {
                        adminController.runMenu(scanner, out, session);
                    }
                    break;
                }
                case "4": {
                    UserSession session = authenticationController.handleProvisioningEngineerLogin(scanner, out);
                    if (session != null) {
                        provisioningController.runMenu(scanner, out, session);
                    }
                    break;
                }
                case "5": {
                    UserSession session = authenticationController.handleInventoryAdminLogin(scanner, out);
                    if (session != null) {
                        inventoryController.runMenu(scanner, out, session);
                    }
                    break;
                }
                case "6": {
                    authenticationController.handleForgotPassword(scanner, out);
                    break;
                }
                case "7": {
                    out.println("Exiting TONPMS Telecom Order System. Goodbye!");
                    running = false;
                    break;
                }
                default:
                    out.println("Invalid choice. Please select 1-7.");
            }
        }

        // Graceful Shutdown
        schedulerManager.shutdownAll();
    }

    public SchedulerManager getSchedulerManager() {
        return schedulerManager;
    }
}
