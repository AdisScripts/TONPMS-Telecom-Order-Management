package com.amdocs.telecom.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TelecomOrder {
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private LocalDateTime orderDate;
    private OrderType orderType;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;
    private LocalDate requestedActivationDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Customer customer;
    private List<OrderItem> orderItems = new ArrayList<OrderItem>();
    private List<OrderPayment> payments = new ArrayList<OrderPayment>();
    private List<ProvisioningRequest> provisioningRequests = new ArrayList<ProvisioningRequest>();

    public TelecomOrder() { }

    public TelecomOrder(String orderNumber, Long customerId, OrderType orderType) {
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.orderType = orderType;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }
    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) { this.orderStatus = orderStatus; }
    public LocalDate getRequestedActivationDate() { return requestedActivationDate; }
    public void setRequestedActivationDate(LocalDate requestedActivationDate) { this.requestedActivationDate = requestedActivationDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public List<OrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }
    public List<OrderPayment> getPayments() { return payments; }
    public void setPayments(List<OrderPayment> payments) { this.payments = payments; }
    public List<ProvisioningRequest> getProvisioningRequests() { return provisioningRequests; }
    public void setProvisioningRequests(List<ProvisioningRequest> provisioningRequests) { this.provisioningRequests = provisioningRequests; }

    @Override
    public String toString() {
        return "TelecomOrder{orderId=" + orderId + ", orderNumber='" + orderNumber
                + "', orderStatus=" + orderStatus + ", paymentStatus=" + paymentStatus + "}";
    }
}
