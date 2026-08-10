package com.amdocs.telecom.model;

public enum ProvisioningType {
    SIM_ACTIVATION, ESIM_ACTIVATION, MOBILE_SERVICE, BROADBAND, VPN, FIVE_G_SERVICE;

    /** Java identifiers cannot start with a digit; this preserves the SQL value exactly. */
    public String getDatabaseValue() {
        return this == FIVE_G_SERVICE ? "5G_SERVICE" : name();
    }
}
