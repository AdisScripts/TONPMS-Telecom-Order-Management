package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.util.DatabaseConnection;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryMonitor {
    private final InventoryItemDao inventoryItemDao;
    private final NotificationService notificationService;
    private final NotificationProcessor notificationProcessor;
    private final Long adminCustomerId;
    private final int lowInventoryThreshold;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int scheduledPoolSize;

    public InventoryMonitor(InventoryItemDao inventoryItemDao, NotificationService notificationService,
                            NotificationProcessor notificationProcessor, Long adminCustomerId) {
        this(inventoryItemDao, notificationService, notificationProcessor, adminCustomerId, 10, 1);
    }

    public InventoryMonitor(InventoryItemDao inventoryItemDao, NotificationService notificationService,
                            NotificationProcessor notificationProcessor, Long adminCustomerId,
                            int lowInventoryThreshold, int scheduledPoolSize) {
        this.inventoryItemDao = Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.notificationProcessor = notificationProcessor;
        this.adminCustomerId = adminCustomerId != null ? adminCustomerId : 1L;
        this.lowInventoryThreshold = lowInventoryThreshold > 0 ? lowInventoryThreshold : 10;
        this.scheduledPoolSize = scheduledPoolSize > 0 ? scheduledPoolSize : 1;
    }

    public synchronized void start(long initialDelaySeconds, long periodSeconds) {
        if (!running.get()) {
            if (scheduler == null || scheduler.isShutdown()) {
                this.scheduler = Executors.newScheduledThreadPool(scheduledPoolSize, new NamedThreadFactory("tonpms-inventory-monitor-"));
            }
            running.set(true);
            scheduledTask = scheduler.scheduleAtFixedRate(
                    this::checkLowInventoryTask,
                    initialDelaySeconds,
                    periodSeconds,
                    TimeUnit.SECONDS
            );
        }
    }

    public void checkLowInventoryTask() {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn != null) {
                DatabaseConnection.setThreadConnection(conn);
            }

            List<InventoryItem> allItems = inventoryItemDao.findAll();
            Map<String, Integer> availableStockMap = new HashMap<>();

            for (InventoryItem item : allItems) {
                if (item.getStatus() == InventoryStatus.AVAILABLE) {
                    String warehouse = item.getWarehouse() != null ? item.getWarehouse() : "DEFAULT_WAREHOUSE";
                    InventoryItemType itemType = item.getItemType() != null ? item.getItemType() : InventoryItemType.SIM;
                    String key = warehouse + ":" + itemType.name();

                    availableStockMap.put(key, availableStockMap.getOrDefault(key, 0) + 1);
                }
            }

            for (Map.Entry<String, Integer> entry : availableStockMap.entrySet()) {
                String key = entry.getKey();
                int count = entry.getValue();
                if (count < lowInventoryThreshold) {
                    String[] parts = key.split(":");
                    String warehouse = parts[0];
                    String itemType = parts[1];
                    String alertMessage = "LOW INVENTORY ALERT: " + itemType + " in " + warehouse + " warehouse count: " + count + " (Threshold: " + lowInventoryThreshold + ")";
                    Notification notif = notificationService.createPendingNotification(adminCustomerId, alertMessage);
                    if (notificationProcessor != null && notif != null && notif.getNotificationId() != null) {
                        notificationProcessor.enqueueNotification(notif.getNotificationId());
                    }
                }
            }

        } catch (Exception ex) {
            // Log or ignore during scheduled scan failure
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

    public int getLowInventoryThreshold() {
        return lowInventoryThreshold;
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
