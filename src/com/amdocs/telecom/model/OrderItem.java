package com.amdocs.telecom.model;

import java.math.BigDecimal;

public class OrderItem {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal totalAmount;
    private TelecomOrder order;
    private TelecomProduct product;

    public OrderItem() { }

    public OrderItem(Long productId, Integer quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public TelecomOrder getOrder() { return order; }
    public void setOrder(TelecomOrder order) { this.order = order; }
    public TelecomProduct getProduct() { return product; }
    public void setProduct(TelecomProduct product) { this.product = product; }

    @Override
    public String toString() {
        return "OrderItem{orderItemId=" + orderItemId + ", productId=" + productId
                + ", quantity=" + quantity + ", totalAmount=" + totalAmount + "}";
    }
}
