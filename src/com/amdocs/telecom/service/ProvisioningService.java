package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProvisioningException;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.security.UserSession;
import java.util.List;
import java.util.Optional;

public interface ProvisioningService {
    ProvisioningRequest createProvisioningRequest(UserSession session, Long orderId, ProvisioningType provisioningType)
            throws AccessDeniedException, ProvisioningException;
    Optional<ProvisioningEngineer> recommendEngineer(ProvisioningType provisioningType, String customerCity);
    void updateProvisioningStatus(UserSession session, Long provisioningId, ProvisioningStatus status, String errorMessage)
            throws AccessDeniedException, ProvisioningException;
    ProvisioningRequest getProvisioningRequestById(UserSession session, Long provisioningId) throws AccessDeniedException;
    List<ProvisioningRequest> getProvisioningRequestsByOrder(UserSession session, Long orderId) throws AccessDeniedException;
}
