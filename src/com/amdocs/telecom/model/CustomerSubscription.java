package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class CustomerSubscription {
    private Long subscriptionId;
    private Long customerId;
    private Long orderId;
    private String serviceId;
    private String serviceType;
    private LocalDateTime activationDate;
    private LocalDateTime terminationDate;
    private SubscriptionStatus status;
    private Customer customer;
    private TelecomOrder order;

    public CustomerSubscription() { }

    public CustomerSubscription(Long customerId, Long orderId, String serviceId, String serviceType) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.serviceId = serviceId;
        this.serviceType = serviceType;
    }

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public LocalDateTime getActivationDate() { return activationDate; }
    public void setActivationDate(LocalDateTime activationDate) { this.activationDate = activationDate; }
    public LocalDateTime getTerminationDate() { return terminationDate; }
    public void setTerminationDate(LocalDateTime terminationDate) { this.terminationDate = terminationDate; }
    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public TelecomOrder getOrder() { return order; }
    public void setOrder(TelecomOrder order) { this.order = order; }

    @Override
    public String toString() {
        return "CustomerSubscription{subscriptionId=" + subscriptionId + ", serviceId='" + serviceId
                + "', status=" + status + "}";
    }
}
