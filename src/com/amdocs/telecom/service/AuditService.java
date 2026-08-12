package com.amdocs.telecom.service;

import com.amdocs.telecom.model.AuditLog;
import java.util.List;

public interface AuditService {
    void logAction(Long userId, String action, String details);
    List<AuditLog> getAuditLogsForEntity(String entityType, Long entityId);
}
