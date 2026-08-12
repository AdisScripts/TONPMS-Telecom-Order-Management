package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ActivationService;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.PaymentService;
import com.amdocs.telecom.service.ProvisioningService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class SchedulerManager {
    private final NotificationProcessor notificationProcessor;
    private final ProvisioningProcessor provisioningProcessor;
    private final OrderProcessor orderProcessor;
    private final InventoryMonitor inventoryMonitor;
    private final OrderReportGenerator orderReportGenerator;
    private final UserSession systemSession;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SchedulerManager(NotificationDao notificationDao, NotificationService notificationService,
                            TelecomOrderDao telecomOrderDao, OrderService orderService,
                            PaymentService paymentService, ProvisioningService provisioningService,
                            ActivationService activationService, InventoryItemDao inventoryItemDao,
                            TelecomProductDao telecomProductDao, ProvisioningRequestDao provisioningRequestDao,
                            OrderItemDao orderItemDao, AuditService auditService, Long adminCustomerId) {
        Objects.requireNonNull(notificationDao, "notificationDao must not be null");
        Objects.requireNonNull(notificationService, "notificationService must not be null");
        Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        Objects.requireNonNull(orderService, "orderService must not be null");
        Objects.requireNonNull(paymentService, "paymentService must not be null");
        Objects.requireNonNull(provisioningService, "provisioningService must not be null");
        Objects.requireNonNull(activationService, "activationService must not be null");
        Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        Objects.requireNonNull(telecomProductDao, "telecomProductDao must not be null");
        Objects.requireNonNull(provisioningRequestDao, "provisioningRequestDao must not be null");
        Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        Objects.requireNonNull(auditService, "auditService must not be null");

        Set<RoleCode> systemRoles = new HashSet<>(Arrays.asList(
                RoleCode.ORDER_ADMINISTRATOR,
                RoleCode.PROVISIONING_ENGINEER,
                RoleCode.INVENTORY_ADMINISTRATOR
        ));
        this.systemSession = new UserSession(0L, "SYSTEM_SCHEDULER", null, null, systemRoles);

        this.notificationProcessor = new NotificationProcessor(notificationDao);
        this.provisioningProcessor = new ProvisioningProcessor(
                provisioningService, activationService, telecomOrderDao, auditService,
                notificationService, this.notificationProcessor, this.systemSession
        );
        this.orderProcessor = new OrderProcessor(
                orderService, paymentService, provisioningService, this.provisioningProcessor,
                telecomOrderDao, auditService, notificationService, this.notificationProcessor, this.systemSession
        );
        this.inventoryMonitor = new InventoryMonitor(
                inventoryItemDao, notificationService, this.notificationProcessor, adminCustomerId
        );
        this.orderReportGenerator = new OrderReportGenerator(
                telecomOrderDao, telecomProductDao, inventoryItemDao, provisioningRequestDao,
                orderItemDao, auditService, this.systemSession, 1
        );
    }

    public SchedulerManager(NotificationProcessor notificationProcessor, ProvisioningProcessor provisioningProcessor,
                            OrderProcessor orderProcessor, InventoryMonitor inventoryMonitor,
                            OrderReportGenerator orderReportGenerator, UserSession systemSession) {
        this.notificationProcessor = Objects.requireNonNull(notificationProcessor, "notificationProcessor must not be null");
        this.provisioningProcessor = Objects.requireNonNull(provisioningProcessor, "provisioningProcessor must not be null");
        this.orderProcessor = Objects.requireNonNull(orderProcessor, "orderProcessor must not be null");
        this.inventoryMonitor = Objects.requireNonNull(inventoryMonitor, "inventoryMonitor must not be null");
        this.orderReportGenerator = Objects.requireNonNull(orderReportGenerator, "orderReportGenerator must not be null");
        this.systemSession = Objects.requireNonNull(systemSession, "systemSession must not be null");
    }

    public void startAll() {
        if (running.compareAndSet(false, true)) {
            notificationProcessor.start();
            provisioningProcessor.start();
            orderProcessor.start();
            inventoryMonitor.start(1, 10);
            orderReportGenerator.start(1, 30);
        }
    }

    public void shutdownAll() {
        if (running.compareAndSet(true, false)) {
            orderProcessor.stop();
            provisioningProcessor.stop();
            notificationProcessor.stop();
            inventoryMonitor.stop();
            orderReportGenerator.stop();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public NotificationProcessor getNotificationProcessor() {
        return notificationProcessor;
    }

    public ProvisioningProcessor getProvisioningProcessor() {
        return provisioningProcessor;
    }

    public OrderProcessor getOrderProcessor() {
        return orderProcessor;
    }

    public InventoryMonitor getInventoryMonitor() {
        return inventoryMonitor;
    }

    public OrderReportGenerator getOrderReportGenerator() {
        return orderReportGenerator;
    }

    public UserSession getSystemSession() {
        return systemSession;
    }
}
