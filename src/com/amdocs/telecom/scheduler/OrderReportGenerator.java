package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class OrderReportGenerator {
    private final TelecomOrderDao telecomOrderDao;
    private final TelecomProductDao telecomProductDao;
    private final InventoryItemDao inventoryItemDao;
    private final ProvisioningRequestDao provisioningRequestDao;
    private final OrderItemDao orderItemDao;
    private final AuditService auditService;
    private final UserSession systemSession;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int scheduledPoolSize;

    public OrderReportGenerator(TelecomOrderDao telecomOrderDao, TelecomProductDao telecomProductDao,
                                InventoryItemDao inventoryItemDao, ProvisioningRequestDao provisioningRequestDao,
                                OrderItemDao orderItemDao, AuditService auditService, UserSession systemSession) {
        this(telecomOrderDao, telecomProductDao, inventoryItemDao, provisioningRequestDao, orderItemDao, auditService, systemSession, 1);
    }

    public OrderReportGenerator(TelecomOrderDao telecomOrderDao, TelecomProductDao telecomProductDao,
                                InventoryItemDao inventoryItemDao, ProvisioningRequestDao provisioningRequestDao,
                                OrderItemDao orderItemDao, AuditService auditService, UserSession systemSession,
                                int scheduledPoolSize) {
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.telecomProductDao = Objects.requireNonNull(telecomProductDao, "telecomProductDao must not be null");
        this.inventoryItemDao = Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        this.provisioningRequestDao = Objects.requireNonNull(provisioningRequestDao, "provisioningRequestDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.systemSession = Objects.requireNonNull(systemSession, "systemSession must not be null");
        this.scheduledPoolSize = scheduledPoolSize > 0 ? scheduledPoolSize : 1;
    }

    public synchronized void start(long initialDelaySeconds, long periodSeconds) {
        if (!running.get()) {
            if (scheduler == null || scheduler.isShutdown()) {
                this.scheduler = Executors.newScheduledThreadPool(scheduledPoolSize, new NamedThreadFactory("tonpms-report-generator-"));
            }
            running.set(true);
            scheduledTask = scheduler.scheduleAtFixedRate(
                    this::generateReportTask,
                    initialDelaySeconds,
                    periodSeconds,
                    TimeUnit.SECONDS
            );
        }
    }

    public Future<ReportMetrics> generateReportAsync() {
        if (scheduler == null || scheduler.isShutdown()) {
            this.scheduler = Executors.newScheduledThreadPool(scheduledPoolSize, new NamedThreadFactory("tonpms-report-generator-"));
        }
        return scheduler.submit(new Callable<ReportMetrics>() {
            @Override
            public ReportMetrics call() throws Exception {
                return computeMetrics();
            }
        });
    }

    public void generateReportTask() {
        try {
            ReportMetrics metrics = computeMetrics();
            auditService.logAction(systemSession.getUserId(), "PERIODIC_METRICS_REPORT", metrics.toString());
        } catch (Exception ignored) { }
    }

    public ReportMetrics computeMetrics() {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn != null) {
                DatabaseConnection.setThreadConnection(conn);
            }

            List<TelecomOrder> orders = telecomOrderDao.findAll();
            int totalOrders = orders.size();

            Map<OrderStatus, Long> ordersByStatus = orders.stream()
                    .collect(Collectors.groupingBy(TelecomOrder::getOrderStatus, Collectors.counting()));

            BigDecimal totalRevenue = BigDecimal.ZERO;
            for (TelecomOrder order : orders) {
                if (order.getTotalAmount() != null) {
                    totalRevenue = totalRevenue.add(order.getTotalAmount());
                }
            }

            Map<String, BigDecimal> revenueByProduct = new HashMap<>();
            List<OrderItem> allItems = orderItemDao.findAll();
            for (OrderItem item : allItems) {
                Long prodId = item.getProductId();
                if (prodId != null) {
                    String prodName = "PRODUCT_" + prodId;
                    Optional<TelecomProduct> prodOpt = telecomProductDao.findById(prodId);
                    if (prodOpt.isPresent()) {
                        prodName = prodOpt.get().getProductName();
                    }

                    BigDecimal itemTotal;
                    if (item.getTotalAmount() != null) {
                        itemTotal = item.getTotalAmount();
                    } else if (item.getUnitPrice() != null && item.getQuantity() != null) {
                        itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    } else {
                        itemTotal = BigDecimal.ZERO;
                    }

                    revenueByProduct.put(prodName, revenueByProduct.getOrDefault(prodName, BigDecimal.ZERO).add(itemTotal));
                }
            }

            List<ProvisioningRequest> reqs = provisioningRequestDao.findAll();
            int failedProvisioningCount = (int) reqs.stream()
                    .filter(r -> r.getStatus() == ProvisioningStatus.FAILED)
                    .count();

            List<InventoryItem> invItems = inventoryItemDao.findAll();
            int availableInventoryCount = (int) invItems.stream()
                    .filter(inv -> inv.getStatus() == InventoryStatus.AVAILABLE)
                    .count();

            return new ReportMetrics(totalOrders, ordersByStatus, revenueByProduct, totalRevenue, failedProvisioningCount, availableInventoryCount);

        } finally {
            DatabaseConnection.clearThreadConnection();
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) { }
            }
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduledTask != null) {
                scheduledTask.cancel(true);
            }
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get() && scheduler != null && !scheduler.isShutdown();
    }

    public static class ReportMetrics {
        private final int totalOrders;
        private final Map<OrderStatus, Long> ordersByStatus;
        private final Map<String, BigDecimal> revenueByProduct;
        private final BigDecimal totalRevenue;
        private final int failedProvisioningCount;
        private final int availableInventoryCount;

        public ReportMetrics(int totalOrders, Map<OrderStatus, Long> ordersByStatus,
                             Map<String, BigDecimal> revenueByProduct, BigDecimal totalRevenue,
                             int failedProvisioningCount, int availableInventoryCount) {
            this.totalOrders = totalOrders;
            this.ordersByStatus = Collections.unmodifiableMap(ordersByStatus != null ? ordersByStatus : Collections.emptyMap());
            this.revenueByProduct = Collections.unmodifiableMap(revenueByProduct != null ? revenueByProduct : Collections.emptyMap());
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
            this.failedProvisioningCount = failedProvisioningCount;
            this.availableInventoryCount = availableInventoryCount;
        }

        public int getTotalOrders() { return totalOrders; }
        public Map<OrderStatus, Long> getOrdersByStatus() { return ordersByStatus; }
        public Map<String, BigDecimal> getRevenueByProduct() { return revenueByProduct; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public int getFailedProvisioningCount() { return failedProvisioningCount; }
        public int getAvailableInventoryCount() { return availableInventoryCount; }

        @Override
        public String toString() {
            return "ReportMetrics{totalOrders=" + totalOrders +
                    ", ordersByStatus=" + ordersByStatus +
                    ", revenueByProduct=" + revenueByProduct +
                    ", totalRevenue=" + totalRevenue +
                    ", failedProvisioningCount=" + failedProvisioningCount +
                    ", availableInventoryCount=" + availableInventoryCount + "}";
        }
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
