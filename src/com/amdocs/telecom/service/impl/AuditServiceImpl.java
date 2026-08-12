package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.service.AuditService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class AuditServiceImpl implements AuditService {
    private final AuditLogDao auditLogDao;

    public AuditServiceImpl(AuditLogDao auditLogDao) {
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao must not be null");
    }

    @Override
    public void logAction(Long userId, String action, String details) {
        AuditLog log = new AuditLog("SYSTEM", userId, action);
        log.setActorUserId(userId);
        log.setDetails(details);
        log.setCreatedAt(LocalDateTime.now());
        auditLogDao.save(log);
    }

    @Override
    public List<AuditLog> getAuditLogsForEntity(String entityType, Long entityId) {
        return auditLogDao.findByEntity(entityType, entityId);
    }
}
