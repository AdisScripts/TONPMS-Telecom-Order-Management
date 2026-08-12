package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.NotificationStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NotificationServiceImpl implements NotificationService {
    private final NotificationDao notificationDao;

    public NotificationServiceImpl(NotificationDao notificationDao) {
        this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao must not be null");
    }

    @Override
    public void sendNotification(Long customerId, String message) {
        if (customerId == null || message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("customerId and message must not be null or empty.");
        }
        Notification notification = new Notification("ALERT", message);
        notification.setCustomerId(customerId);
        notification.setStatus(NotificationStatus.SENT);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setSentAt(LocalDateTime.now());
        notificationDao.save(notification);
    }

    @Override
    public List<Notification> getNotificationsForCustomer(UserSession session, Long customerId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(customerId);
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Customers can only view their own notifications.");
        }
        return notificationDao.findByCustomerId(customerId);
    }

    @Override
    public void markAsRead(UserSession session, Long notificationId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId must not be null.");
        }
        Optional<Notification> optional = notificationDao.findById(notificationId);
        Notification notification = optional.orElseThrow(() -> new IllegalArgumentException("Notification not found with ID: " + notificationId));

        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(notification.getCustomerId());
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Customers can only update their own notifications.");
        }

        notification.setStatus(NotificationStatus.READ);
        boolean updated = notificationDao.update(notification);
        if (!updated) {
            throw new IllegalStateException("Failed to mark notification as read.");
        }
    }
}
