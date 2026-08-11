package com.amdocs.telecom.security;

import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.RoleCode;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Authenticated user session state holding user profile, linked entities, and assigned roles. */
public class UserSession {

    private final Long userId;
    private final String username;
    private final Customer customer;
    private final ProvisioningEngineer engineer;
    private final Set<RoleCode> roles;
    private final LocalDateTime loginTime;
    private boolean active;

    public UserSession(Long userId, String username, Customer customer, ProvisioningEngineer engineer, Set<RoleCode> roles) {
        this.userId = userId;
        this.username = username;
        this.customer = customer;
        this.engineer = engineer;
        this.roles = roles != null ? Collections.unmodifiableSet(new HashSet<>(roles)) : Collections.emptySet();
        this.loginTime = LocalDateTime.now();
        this.active = true;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Customer getCustomer() { return customer; }
    public ProvisioningEngineer getEngineer() { return engineer; }
    public Set<RoleCode> getRoles() { return roles; }
    public LocalDateTime getLoginTime() { return loginTime; }
    public boolean isActive() { return active; }
    public void invalidate() { this.active = false; }

    public boolean hasRole(RoleCode roleCode) {
        return active && roleCode != null && roles.contains(roleCode);
    }

    public boolean hasAnyRole(RoleCode... roleCodes) {
        if (!active || roleCodes == null) return false;
        for (RoleCode r : roleCodes) {
            if (roles.contains(r)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "UserSession{userId=" + userId + ", username='" + username + "', roles=" + roles + ", active=" + active + '}';
    }
}
