package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.CustomerSubscription;
import java.util.List;
import java.util.Optional;
public interface CustomerSubscriptionDao extends CrudDao<CustomerSubscription> {
    Optional<CustomerSubscription> findByServiceId(String serviceId);
    List<CustomerSubscription> findByCustomerId(Long customerId);
}
