package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.security.UserSession;
import java.util.List;

public interface NotificationService {
    void sendNotification(Long customerId, String message);
    List<Notification> getNotificationsForCustomer(UserSession session, Long customerId) throws AccessDeniedException;
    void markAsRead(UserSession session, Long notificationId) throws AccessDeniedException;
}
