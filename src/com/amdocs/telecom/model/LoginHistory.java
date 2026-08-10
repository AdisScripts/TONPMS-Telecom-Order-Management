package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class LoginHistory {
    private Long loginHistoryId;
    private Long userId;
    private String usernameAttempted;
    private LocalDateTime attemptedAt;
    private Boolean success;
    private String ipAddress;
    private String failureReason;
    private AppUser user;

    public LoginHistory() { }

    public LoginHistory(String usernameAttempted, Boolean success) {
        this.usernameAttempted = usernameAttempted;
        this.success = success;
    }

    public Long getLoginHistoryId() { return loginHistoryId; }
    public void setLoginHistoryId(Long loginHistoryId) { this.loginHistoryId = loginHistoryId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsernameAttempted() { return usernameAttempted; }
    public void setUsernameAttempted(String usernameAttempted) { this.usernameAttempted = usernameAttempted; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    @Override
    public String toString() {
        return "LoginHistory{loginHistoryId=" + loginHistoryId + ", usernameAttempted='"
                + usernameAttempted + "', success=" + success + "}";
    }
}
