package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class ProvisioningRequest {
    private Long provisioningId;
    private Long orderId;
    private String serviceId;
    private ProvisioningType provisioningType;
    private String networkElement;
    private LocalDateTime requestedDate;
    private LocalDateTime completedDate;
    private ProvisioningStatus status;
    private String errorMessage;
    private Long engineerId;
    private TelecomOrder order;
    private ProvisioningEngineer engineer;

    public ProvisioningRequest() { }

    public ProvisioningRequest(Long orderId, String serviceId, ProvisioningType provisioningType) {
        this.orderId = orderId;
        this.serviceId = serviceId;
        this.provisioningType = provisioningType;
    }

    public Long getProvisioningId() { return provisioningId; }
    public void setProvisioningId(Long provisioningId) { this.provisioningId = provisioningId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public ProvisioningType getProvisioningType() { return provisioningType; }
    public void setProvisioningType(ProvisioningType provisioningType) { this.provisioningType = provisioningType; }
    public String getNetworkElement() { return networkElement; }
    public void setNetworkElement(String networkElement) { this.networkElement = networkElement; }
    public LocalDateTime getRequestedDate() { return requestedDate; }
    public void setRequestedDate(LocalDateTime requestedDate) { this.requestedDate = requestedDate; }
    public LocalDateTime getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDateTime completedDate) { this.completedDate = completedDate; }
    public ProvisioningStatus getStatus() { return status; }
    public void setStatus(ProvisioningStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getEngineerId() { return engineerId; }
    public void setEngineerId(Long engineerId) { this.engineerId = engineerId; }
    public TelecomOrder getOrder() { return order; }
    public void setOrder(TelecomOrder order) { this.order = order; }
    public ProvisioningEngineer getEngineer() { return engineer; }
    public void setEngineer(ProvisioningEngineer engineer) { this.engineer = engineer; }

    @Override
    public String toString() {
        return "ProvisioningRequest{provisioningId=" + provisioningId + ", serviceId='" + serviceId
                + "', provisioningType=" + provisioningType + ", status=" + status + "}";
    }
}
