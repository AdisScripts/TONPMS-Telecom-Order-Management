package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class AuditLog {
    private Long auditId;
    private Long actorUserId;
    private String entityType;
    private Long entityId;
    private String action;
    private String details;
    private LocalDateTime createdAt;
    private AppUser actorUser;

    public AuditLog() { }

    public AuditLog(String entityType, Long entityId, String action) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
    }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public AppUser getActorUser() { return actorUser; }
    public void setActorUser(AppUser actorUser) { this.actorUser = actorUser; }

    @Override
    public String toString() {
        return "AuditLog{auditId=" + auditId + ", entityType='" + entityType
                + "', entityId=" + entityId + ", action='" + action + "'}";
    }
}
