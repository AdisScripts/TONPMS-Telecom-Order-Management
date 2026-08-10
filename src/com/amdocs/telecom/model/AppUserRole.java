package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class AppUserRole {
    private Long userId;
    private Short roleId;
    private LocalDateTime assignedAt;
    private AppUser user;
    private AppRole role;

    public AppUserRole() { }

    public AppUserRole(Long userId, Short roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Short getRoleId() { return roleId; }
    public void setRoleId(Short roleId) { this.roleId = roleId; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AppRole getRole() { return role; }
    public void setRole(AppRole role) { this.role = role; }

    @Override
    public String toString() {
        return "AppUserRole{userId=" + userId + ", roleId=" + roleId + ", assignedAt=" + assignedAt + "}";
    }
}
