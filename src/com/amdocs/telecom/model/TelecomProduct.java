package com.amdocs.telecom.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TelecomProduct {
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private String description;
    private BigDecimal monthlyPrice;
    private BigDecimal activationFee;
    private Integer contractPeriod;
    private ProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TelecomProduct() { }

    public TelecomProduct(String productCode, String productName, String productType, BigDecimal monthlyPrice) {
        this.productCode = productCode;
        this.productName = productName;
        this.productType = productType;
        this.monthlyPrice = monthlyPrice;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public BigDecimal getActivationFee() { return activationFee; }
    public void setActivationFee(BigDecimal activationFee) { this.activationFee = activationFee; }
    public Integer getContractPeriod() { return contractPeriod; }
    public void setContractPeriod(Integer contractPeriod) { this.contractPeriod = contractPeriod; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "TelecomProduct{productId=" + productId + ", productCode='" + productCode
                + "', productName='" + productName + "', status=" + status + "}";
    }
}
