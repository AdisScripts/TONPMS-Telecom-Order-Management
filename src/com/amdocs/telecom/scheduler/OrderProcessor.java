package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.PaymentService;
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

public class OrderProcessor {
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ProvisioningService provisioningService;
    private final ProvisioningProcessor provisioningProcessor;
    private final TelecomOrderDao telecomOrderDao;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final NotificationProcessor notificationProcessor;
    private final UserSession systemSession;
    private final BlockingQueue<Long> orderQueue;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int threadPoolSize;

    public OrderProcessor(OrderService orderService, PaymentService paymentService,
                          ProvisioningService provisioningService, ProvisioningProcessor provisioningProcessor,
                          TelecomOrderDao telecomOrderDao, AuditService auditService,
                          NotificationService notificationService, NotificationProcessor notificationProcessor,
                          UserSession systemSession) {
        this(orderService, paymentService, provisioningService, provisioningProcessor, telecomOrderDao,
             auditService, notificationService, notificationProcessor, systemSession, 4);
    }

    public OrderProcessor(OrderService orderService, PaymentService paymentService,
                          ProvisioningService provisioningService, ProvisioningProcessor provisioningProcessor,
                          TelecomOrderDao telecomOrderDao, AuditService auditService,
                          NotificationService notificationService, UserSession systemSession) {
        this(orderService, paymentService, provisioningService, provisioningProcessor, telecomOrderDao,
             auditService, notificationService, null, systemSession, 4);
    }

    public OrderProcessor(OrderService orderService, PaymentService paymentService,
                          ProvisioningService provisioningService, ProvisioningProcessor provisioningProcessor,
                          TelecomOrderDao telecomOrderDao, AuditService auditService,
                          NotificationService notificationService, NotificationProcessor notificationProcessor,
                          UserSession systemSession, int threadPoolSize) {
        this.orderService = Objects.requireNonNull(orderService, "orderService must not be null");
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
        this.provisioningService = Objects.requireNonNull(provisioningService, "provisioningService must not be null");
        this.provisioningProcessor = Objects.requireNonNull(provisioningProcessor, "provisioningProcessor must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.notificationProcessor = notificationProcessor;
        this.systemSession = Objects.requireNonNull(systemSession, "systemSession must not be null");
        this.threadPoolSize = threadPoolSize > 0 ? threadPoolSize : 4;
        this.orderQueue = new LinkedBlockingQueue<>();
    }

    public void submitOrderForBackgroundProcessing(Long orderId) {
        if (orderId != null) {
            orderQueue.offer(orderId);
        }
    }

    public synchronized void start() {
        if (!running.get()) {
            if (executor == null || executor.isShutdown()) {
                this.executor = Executors.newFixedThreadPool(threadPoolSize, new NamedThreadFactory("tonpms-order-worker-"));
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
                Long orderId = orderQueue.poll(500, TimeUnit.MILLISECONDS);
                if (orderId != null) {
                    processOrder(orderId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void processOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        Connection conn = null;
        Long customerId = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn != null) {
                DatabaseConnection.setThreadConnection(conn);
            }

            TelecomOrder order = orderService.getOrderById(systemSession, orderId);
            customerId = order.getCustomerId();

            if (order.getPaymentStatus() == PaymentStatus.PENDING) {
                try {
                    paymentService.processPayment(systemSession, orderId, order.getTotalAmount(), PaymentMode.CARD);
                } catch (Exception payEx) {
                    // Payment or inventory reservation failure path
                    order.setOrderStatus(OrderStatus.FAILED);
                    telecomOrderDao.update(order);

                    auditService.logAction(systemSession.getUserId(), "ORDER_PROCESSING_FAILED",
                            "Order ID " + orderId + " failed payment/inventory processing: " + payEx.getMessage());

                    String msg = "Order processing failed for order " + order.getOrderNumber() + ": " + payEx.getMessage();
                    Notification notif = notificationService.createPendingNotification(customerId, msg);
                    if (notificationProcessor != null && notif != null && notif.getNotificationId() != null) {
                        notificationProcessor.enqueueNotification(notif.getNotificationId());
                    }
                    return;
                }
            }

            // Create provisioning request & handoff to ProvisioningProcessor
            ProvisioningType provType = determineProvisioningType(order.getOrderType());
            ProvisioningRequest provReq = provisioningService.createProvisioningRequest(systemSession, orderId, provType);
            if (provReq != null && provReq.getProvisioningId() != null) {
                provisioningProcessor.enqueueProvisioningRequest(provReq.getProvisioningId());
            }

        } catch (Exception ex) {
            // General failure path
            try {
                Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
                if (orderOpt.isPresent()) {
                    TelecomOrder order = orderOpt.get();
                    order.setOrderStatus(OrderStatus.FAILED);
                    telecomOrderDao.update(order);
                    if (customerId == null) customerId = order.getCustomerId();
                }
            } catch (Exception ignored) { }

            try {
                auditService.logAction(systemSession.getUserId(), "ORDER_PROCESSING_FAILED",
                        "Order ID " + orderId + " processing failed: " + ex.getMessage());
            } catch (Exception ignored) { }

            if (customerId != null) {
                try {
                    String msg = "Order processing failed for order ID " + orderId + ": " + ex.getMessage();
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

    private ProvisioningType determineProvisioningType(OrderType orderType) {
        if (orderType == OrderType.ESIM_ACTIVATION) {
            return ProvisioningType.ESIM_ACTIVATION;
        } else if (orderType == OrderType.SIM_REPLACEMENT) {
            return ProvisioningType.SIM_ACTIVATION;
        } else if (orderType == OrderType.BROADBAND) {
            return ProvisioningType.BROADBAND;
        }
        return ProvisioningType.MOBILE_SERVICE;
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
        return orderQueue.size();
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
