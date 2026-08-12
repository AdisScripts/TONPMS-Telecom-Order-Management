package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.impl.NotificationServiceImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InventoryMonitorTest {

    public static void main(String[] args) {
        System.out.println("Running InventoryMonitorTest...");
        testLowInventoryAlertTrigger();
        testScheduledExecution();
        System.out.println("PASS: InventoryMonitorTest completed successfully.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }

    private static void testLowInventoryAlertTrigger() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockNotificationDao notifDao = new MockNotificationDao();

        NotificationService notificationService = new NotificationServiceImpl(notifDao);
        NotificationProcessor notificationProcessor = new NotificationProcessor(notifDao, 1);

        for (int i = 1; i <= 3; i++) {
            InventoryItem item = new InventoryItem("SIM-10" + i, InventoryItemType.SIM, "Mumbai");
            item.setStatus(InventoryStatus.AVAILABLE);
            inventoryDao.save(item);
        }

        long adminId = 99L;
        InventoryMonitor monitor = new InventoryMonitor(inventoryDao, notificationService, notificationProcessor, adminId, 10, 1);

        monitor.checkLowInventoryTask();

        List<Notification> notifs = notifDao.findByCustomerId(adminId);
        require(notifs.size() >= 1, "Alert notification should be sent to admin");
        require(notifs.get(0).getMessage().contains("LOW INVENTORY ALERT"), "Message should contain LOW INVENTORY ALERT");
    }

    private static void testScheduledExecution() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockNotificationDao notifDao = new MockNotificationDao();

        NotificationService notificationService = new NotificationServiceImpl(notifDao);
        NotificationProcessor notificationProcessor = new NotificationProcessor(notifDao, 1);

        for (int i = 1; i <= 2; i++) {
            InventoryItem item = new InventoryItem("SIM-20" + i, InventoryItemType.SIM, "Delhi");
            item.setStatus(InventoryStatus.AVAILABLE);
            inventoryDao.save(item);
        }

        long adminId = 98L;
        InventoryMonitor monitor = new InventoryMonitor(inventoryDao, notificationService, notificationProcessor, adminId, 10, 1);

        notificationProcessor.start();
        monitor.start(0, 1); // Start immediately with 1-second period

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        monitor.stop();
        notificationProcessor.stop();

        List<Notification> notifs = notifDao.findByCustomerId(adminId);
        require(notifs.size() >= 1, "Scheduled monitor task should execute automatically and send alert");
    }

    private static class MockInventoryItemDao implements InventoryItemDao {
        private final Map<Long, InventoryItem> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(InventoryItem entity) { long id = idSeq++; entity.setInventoryId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<InventoryItem> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<InventoryItem> findByItemCode(String code) { return storage.values().stream().filter(i -> code.equals(i.getItemCode())).findFirst(); }
        @Override public synchronized List<InventoryItem> findByStatus(String status) { List<InventoryItem> res = new ArrayList<>(); for (InventoryItem i : storage.values()) { if (i.getStatus() != null && i.getStatus().name().equalsIgnoreCase(status)) res.add(i); } return res; }
        @Override public synchronized List<InventoryItem> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(InventoryItem entity) { if (storage.containsKey(entity.getInventoryId())) { storage.put(entity.getInventoryId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockNotificationDao implements NotificationDao {
        private final Map<Long, Notification> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(Notification entity) { long id = idSeq++; entity.setNotificationId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<Notification> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized List<Notification> findByCustomerId(Long cId) { List<Notification> res = new ArrayList<>(); for (Notification n : storage.values()) { if (cId.equals(n.getCustomerId())) res.add(n); } return res; }
        @Override public synchronized List<Notification> findByRecipientUserId(Long uId) { return new ArrayList<>(); }
        @Override public synchronized List<Notification> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(Notification entity) { if (storage.containsKey(entity.getNotificationId())) { storage.put(entity.getNotificationId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }
}
