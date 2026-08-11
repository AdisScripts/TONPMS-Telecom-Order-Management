package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.AuditLog;
import java.util.List;
public interface AuditLogDao extends CrudDao<AuditLog> {
    List<AuditLog> findByEntity(String entityType, Long entityId);
}
