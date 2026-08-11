package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.Notification;
import java.util.List;
public interface NotificationDao extends CrudDao<Notification> {
    List<Notification> findByCustomerId(Long customerId);
    List<Notification> findByRecipientUserId(Long recipientUserId);
}
