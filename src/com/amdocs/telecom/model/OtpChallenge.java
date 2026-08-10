package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class OtpChallenge {
    private Long otpId;
    private Long userId;
    private OtpPurpose purpose;
    private String otpHash;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private Integer attempts;
    private AppUser user;

    public OtpChallenge() { }

    public OtpChallenge(Long userId, OtpPurpose purpose, String otpHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
    }

    public Long getOtpId() { return otpId; }
    public void setOtpId(Long otpId) { this.otpId = otpId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public OtpPurpose getPurpose() { return purpose; }
    public void setPurpose(OtpPurpose purpose) { this.purpose = purpose; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    @Override
    public String toString() {
        return "OtpChallenge{otpId=" + otpId + ", userId=" + userId
                + ", purpose=" + purpose + ", expiresAt=" + expiresAt + "}";
    }
}
