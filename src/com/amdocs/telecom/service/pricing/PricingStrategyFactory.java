package com.amdocs.telecom.service.pricing;

import com.amdocs.telecom.model.CustomerType;

public final class PricingStrategyFactory {
    private static final PricingStrategy INDIVIDUAL_STRATEGY = new IndividualPricingStrategy();
    private static final PricingStrategy SME_STRATEGY = new SmePricingStrategy();
    private static final PricingStrategy ENTERPRISE_STRATEGY = new EnterprisePricingStrategy();

    private PricingStrategyFactory() { }

    public static PricingStrategy getStrategy(CustomerType customerType) {
        if (customerType == null) {
            return INDIVIDUAL_STRATEGY;
        }
        switch (customerType) {
            case SME:
                return SME_STRATEGY;
            case ENTERPRISE:
                return ENTERPRISE_STRATEGY;
            case INDIVIDUAL:
            default:
                return INDIVIDUAL_STRATEGY;
        }
    }
}
