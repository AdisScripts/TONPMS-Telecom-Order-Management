package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.CustomerNotEligibleException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.IdentityStatus;
import com.amdocs.telecom.security.UserSession;
import java.util.List;

public interface CustomerService {
    Customer getCustomerProfile(Long customerId);
    Customer getCustomerByNumber(String customerNumber);
    Customer getCustomerByEmail(String email);
    Customer updateCustomerProfile(UserSession session, Customer customer) throws AccessDeniedException;
    void updateAccountStatus(UserSession session, Long customerId, CustomerAccountStatus newStatus) throws AccessDeniedException;
    void updateIdentityStatus(UserSession session, Long customerId, IdentityStatus newStatus) throws AccessDeniedException;
    void checkCustomerEligibility(Long customerId) throws CustomerNotEligibleException;
    List<Customer> getAllCustomers(UserSession session) throws AccessDeniedException;
}
