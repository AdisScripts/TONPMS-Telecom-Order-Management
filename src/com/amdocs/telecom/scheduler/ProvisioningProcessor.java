package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ActivationService;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.ProvisioningService;
import com.amdocs.telecom.util.DatabaseConnection;
import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProvisioningProcessor {
    private final ProvisioningService provisioningService;
    private final ActivationService activationService;
    private final TelecomOrderDao telecomOrderDao;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final NotificationProcessor notificationProcessor;
    private final UserSession systemSession;
    private final BlockingQueue<Long> provisioningQueue;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int threadPoolSize;

    public ProvisioningProcessor(ProvisioningService provisioningService, ActivationService activationService,
                                 TelecomOrderDao telecomOrderDao, AuditService auditService,
                                 NotificationService notificationService, NotificationProcessor notificationProcessor,
                                 UserSession systemSession) {
        this(provisioningService, activationService, telecomOrderDao, auditService, notificationService,
             notificationProcessor, systemSession, 4);
    }

    public ProvisioningProcessor(ProvisioningService provisioningService, ActivationService activationService,
                                 TelecomOrderDao telecomOrderDao, AuditService auditService,
                                 NotificationService notificationService, NotificationProcessor notificationProcessor,
                                 UserSession systemSession, int threadPoolSize) {
        this.provisioningService = Objects.requireNonNull(provisioningService, "provisioningService must not be null");
        this.activationService = Objects.requireNonNull(activationService, "activationService must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.notificationProcessor = notificationProcessor;
        this.systemSession = Objects.requireNonNull(systemSession, "systemSession must not be null");
        this.threadPoolSize = threadPoolSize > 0 ? threadPoolSize : 4;
        this.provisioningQueue = new LinkedBlockingQueue<>();
    }

    public void enqueueProvisioningRequest(Long provisioningId) {
        if (provisioningId != null) {
            provisioningQueue.offer(provisioningId);
        }
    }

    public synchronized void start() {
        if (!running.get()) {
            if (executor == null || executor.isShutdown()) {
                this.executor = Executors.newFixedThreadPool(threadPoolSize, new NamedThreadFactory("tonpms-provisioning-worker-"));
            }
            running.set(true);
            for (int i = 0; i < threadPoolSize; i++) {
                executor.submit(this::processWorkerLoop);
            }
        }
    }

    private void processWorkerLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Long reqId = provisioningQueue.poll(500, TimeUnit.MILLISECONDS);
                if (reqId != null) {
                    processProvisioningRequest(reqId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void processProvisioningRequest(Long provisioningId) {
        if (provisioningId == null) {
            return;
        }
        Connection conn = null;
        Long orderId = null;
        Long customerId = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn != null) {
                DatabaseConnection.setThreadConnection(conn);
            }

            ProvisioningRequest req = provisioningService.getProvisioningRequestById(systemSession, provisioningId);
            orderId = req.getOrderId();

            Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
            if (orderOpt.isPresent()) {
                customerId = orderOpt.get().getCustomerId();
            }

            // Simulate provisioning execution -> set SUCCESS
            provisioningService.updateProvisioningStatus(systemSession, provisioningId, ProvisioningStatus.SUCCESS, null);

            // Execute service activation
            activationService.activateService(systemSession, orderId);

        } catch (Exception ex) {
            // Failure path
            try {
                provisioningService.updateProvisioningStatus(systemSession, provisioningId, ProvisioningStatus.FAILED, ex.getMessage());
            } catch (Exception ignored) { }

            if (orderId != null) {
                try {
                    Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
                    if (orderOpt.isPresent()) {
                        TelecomOrder order = orderOpt.get();
                        order.setOrderStatus(OrderStatus.FAILED);
                        telecomOrderDao.update(order);
                        if (customerId == null) {
                            customerId = order.getCustomerId();
                        }
                    }
                } catch (Exception ignored) { }
            }

            try {
                auditService.logAction(systemSession.getUserId(), "PROVISIONING_FAILED",
                        "Provisioning request ID " + provisioningId + " failed: " + ex.getMessage());
            } catch (Exception ignored) { }

            if (customerId != null) {
                try {
                    String msg = "Provisioning failed for request ID " + provisioningId + ": " + ex.getMessage();
                    Notification notif = notificationService.createPendingNotification(customerId, msg);
                    if (notificationProcessor != null && notif != null && notif.getNotificationId() != null) {
                        notificationProcessor.enqueueNotification(notif.getNotificationId());
                    }
                } catch (Exception ignored) { }
            }
        } finally {
            DatabaseConnection.clearThreadConnection();
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) { }
            }
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public int getQueueSize() {
        return provisioningQueue.size();
    }

    public boolean isRunning() {
        return running.get() && executor != null && !executor.isShutdown();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
