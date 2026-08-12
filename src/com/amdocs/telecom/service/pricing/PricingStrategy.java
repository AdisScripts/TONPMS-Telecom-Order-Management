package com.amdocs.telecom.service.pricing;

import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.TelecomProduct;

public interface PricingStrategy {
    OrderItem calculateItemTotal(TelecomProduct product, int quantity);
}
