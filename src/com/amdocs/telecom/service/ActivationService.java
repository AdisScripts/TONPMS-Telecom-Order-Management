package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProvisioningException;
import com.amdocs.telecom.model.CustomerSubscription;
import com.amdocs.telecom.security.UserSession;
import java.util.List;

public interface ActivationService {
    void activateService(UserSession session, Long orderId) throws AccessDeniedException, ProvisioningException;
    void completeOrderLifecycle(UserSession session, Long orderId) throws AccessDeniedException;
    List<CustomerSubscription> getCustomerSubscriptions(UserSession session, Long customerId) throws AccessDeniedException;
}
