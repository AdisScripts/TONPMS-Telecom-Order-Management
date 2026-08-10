package com.amdocs.telecom.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderPayment {
    private Long paymentId;
    private Long orderId;
    private String transactionReference;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private LocalDateTime paymentDate;
    private PaymentTransactionStatus status;
    private TelecomOrder order;

    public OrderPayment() { }

    public OrderPayment(Long orderId, String transactionReference, BigDecimal amount, PaymentMode paymentMode) {
        this.orderId = orderId;
        this.transactionReference = transactionReference;
        this.amount = amount;
        this.paymentMode = paymentMode;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public PaymentTransactionStatus getStatus() { return status; }
    public void setStatus(PaymentTransactionStatus status) { this.status = status; }
    public TelecomOrder getOrder() { return order; }
    public void setOrder(TelecomOrder order) { this.order = order; }

    @Override
    public String toString() {
        return "OrderPayment{paymentId=" + paymentId + ", transactionReference='" + transactionReference
                + "', amount=" + amount + ", status=" + status + "}";
    }
}
