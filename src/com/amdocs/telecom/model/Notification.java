package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class Notification {
    private Long notificationId;
    private Long customerId;
    private Long recipientUserId;
    private String notificationType;
    private String message;
    private NotificationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private Customer customer;
    private AppUser recipientUser;

    public Notification() { }

    public Notification(String notificationType, String message) {
        this.notificationType = notificationType;
        this.message = message;
    }

    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public AppUser getRecipientUser() { return recipientUser; }
    public void setRecipientUser(AppUser recipientUser) { this.recipientUser = recipientUser; }

    @Override
    public String toString() {
        return "Notification{notificationId=" + notificationId + ", notificationType='" + notificationType
                + "', status=" + status + "}";
    }
}
