package com.amdocs.telecom.service.pricing;

import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.TelecomProduct;
import java.math.BigDecimal;

public class IndividualPricingStrategy implements PricingStrategy {
    @Override
    public OrderItem calculateItemTotal(TelecomProduct product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        BigDecimal unitPrice = product.getMonthlyPrice() != null ? product.getMonthlyPrice() : BigDecimal.ZERO;
        BigDecimal grossAmount = unitPrice.multiply(new BigDecimal(quantity));
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        BigDecimal totalAmount = grossAmount.subtract(discount).add(tax);

        OrderItem item = new OrderItem(product.getProductId(), quantity, unitPrice);
        item.setDiscount(discount);
        item.setTax(tax);
        item.setTotalAmount(totalAmount);
        item.setProduct(product);
        return item;
    }
}
