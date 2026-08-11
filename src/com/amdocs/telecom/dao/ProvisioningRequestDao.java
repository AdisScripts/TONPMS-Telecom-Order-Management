package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.ProvisioningRequest;
import java.util.List;
public interface ProvisioningRequestDao extends CrudDao<ProvisioningRequest> {
    List<ProvisioningRequest> findByOrderId(Long orderId);
}
