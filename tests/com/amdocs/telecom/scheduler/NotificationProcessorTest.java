package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.NotificationStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NotificationProcessorTest {

    public static void main(String[] args) {
        System.out.println("Running NotificationProcessorTest...");
        testNotificationProcessingSuccess();
        testNotificationProcessingFailure();
        System.out.println("PASS: NotificationProcessorTest completed successfully.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }

    private static void testNotificationProcessingSuccess() {
        MockNotificationDao notifDao = new MockNotificationDao();
        NotificationProcessor processor = new NotificationProcessor(notifDao, 2);

        Notification n1 = new Notification("ALERT", "Test notification 1");
        n1.setCustomerId(10L);
        n1.setStatus(NotificationStatus.PENDING);
        long n1Id = notifDao.save(n1);

        processor.start();
        processor.enqueueNotification(n1Id);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        processor.stop();

        Optional<Notification> processed = notifDao.findById(n1Id);
        require(processed.isPresent(), "Notification should exist in database");
        require(processed.get().getStatus() == NotificationStatus.SENT, "Notification status should be updated to SENT");
    }

    private static void testNotificationProcessingFailure() {
        FailingNotificationDao failingDao = new FailingNotificationDao();
        NotificationProcessor processor = new NotificationProcessor(failingDao, 2);

        Notification n2 = new Notification("ALERT", "Test notification failure");
        n2.setCustomerId(11L);
        n2.setStatus(NotificationStatus.PENDING);
        long n2Id = failingDao.save(n2);

        processor.start();
        processor.enqueueNotification(n2Id);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        processor.stop();

        Optional<Notification> processed = failingDao.findById(n2Id);
        require(processed.isPresent(), "Notification should exist in database");
        require(processed.get().getStatus() == NotificationStatus.FAILED, "Notification status should be updated to FAILED on update failure");
    }

    private static class MockNotificationDao implements NotificationDao {
        protected final Map<Long, Notification> storage = new HashMap<>();
        protected long idSeq = 1L;

        @Override public synchronized long save(Notification entity) { long id = idSeq++; entity.setNotificationId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<Notification> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized List<Notification> findByCustomerId(Long cId) { List<Notification> res = new ArrayList<>(); for (Notification n : storage.values()) { if (cId.equals(n.getCustomerId())) res.add(n); } return res; }
        @Override public synchronized List<Notification> findByRecipientUserId(Long uId) { return new ArrayList<>(); }
        @Override public synchronized List<Notification> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(Notification entity) { if (storage.containsKey(entity.getNotificationId())) { storage.put(entity.getNotificationId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class FailingNotificationDao extends MockNotificationDao {
        @Override
        public synchronized boolean update(Notification entity) {
            if (entity.getStatus() == NotificationStatus.SENT) {
                // Fail update to SENT -> triggers FAILED path in catch block
                return false;
            }
            if (storage.containsKey(entity.getNotificationId())) {
                storage.put(entity.getNotificationId(), entity);
                return true;
            }
            return false;
        }
    }
}
