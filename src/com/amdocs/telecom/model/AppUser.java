package com.amdocs.telecom.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AppUser {
    private Long userId;
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private Long customerId;
    private UserAccountStatus accountStatus;
    private Integer failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Customer customer;
    private List<AppUserRole> userRoles = new ArrayList<AppUserRole>();

    public AppUser() { }

    public AppUser(String username, String passwordHash, String passwordSalt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public UserAccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(UserAccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public List<AppUserRole> getUserRoles() { return userRoles; }
    public void setUserRoles(List<AppUserRole> userRoles) { this.userRoles = userRoles; }

    @Override
    public String toString() {
        return "AppUser{userId=" + userId + ", username='" + username
                + "', accountStatus=" + accountStatus + "}";
    }
}
