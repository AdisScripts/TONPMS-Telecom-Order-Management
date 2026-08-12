package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.NotificationStatus;
import com.amdocs.telecom.util.DatabaseConnection;
import java.sql.Connection;
import java.time.LocalDateTime;
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

public class NotificationProcessor {
    private final NotificationDao notificationDao;
    private final BlockingQueue<Long> notificationQueue;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int threadPoolSize;

    public NotificationProcessor(NotificationDao notificationDao) {
        this(notificationDao, 2);
    }

    public NotificationProcessor(NotificationDao notificationDao, int threadPoolSize) {
        this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao must not be null");
        this.threadPoolSize = threadPoolSize > 0 ? threadPoolSize : 2;
        this.notificationQueue = new LinkedBlockingQueue<>();
    }

    public void enqueueNotification(Long notificationId) {
        if (notificationId != null) {
            notificationQueue.offer(notificationId);
        }
    }

    public synchronized void start() {
        if (!running.get()) {
            if (executor == null || executor.isShutdown()) {
                this.executor = Executors.newFixedThreadPool(threadPoolSize, new NamedThreadFactory("tonpms-notification-worker-"));
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
                Long notificationId = notificationQueue.poll(500, TimeUnit.MILLISECONDS);
                if (notificationId != null) {
                    processNotification(notificationId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void processNotification(Long notificationId) {
        if (notificationId == null) {
            return;
        }
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn != null) {
                DatabaseConnection.setThreadConnection(conn);
            }

            Optional<Notification> notifOpt = notificationDao.findById(notificationId);
            if (notifOpt.isPresent()) {
                Notification notif = notifOpt.get();
                notif.setStatus(NotificationStatus.SENT);
                notif.setSentAt(LocalDateTime.now());
                boolean updated = notificationDao.update(notif);
                if (!updated) {
                    throw new IllegalStateException("Database update failed for notification " + notificationId);
                }
            }
        } catch (Exception ex) {
            try {
                Optional<Notification> notifOpt = notificationDao.findById(notificationId);
                if (notifOpt.isPresent()) {
                    Notification notif = notifOpt.get();
                    notif.setStatus(NotificationStatus.FAILED);
                    notificationDao.update(notif);
                }
            } catch (Exception ignored) { }
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
        return notificationQueue.size();
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
