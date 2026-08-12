package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.CustomerNotEligibleException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.IdentityStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.security.AuthorizationService;
import com.amdocs.telecom.security.AuthorizationServiceImpl;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.CustomerService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CustomerServiceImpl implements CustomerService {
    private final CustomerDao customerDao;
    private final AuthorizationService authorizationService;

    public CustomerServiceImpl(CustomerDao customerDao) {
        this.customerDao = Objects.requireNonNull(customerDao, "customerDao must not be null");
        this.authorizationService = new AuthorizationServiceImpl();
    }

    public CustomerServiceImpl(CustomerDao customerDao, AuthorizationService authorizationService) {
        this.customerDao = Objects.requireNonNull(customerDao, "customerDao must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
    }

    @Override
    public Customer getCustomerProfile(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }
        Optional<Customer> optional = customerDao.findById(customerId);
        return optional.orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + customerId));
    }

    @Override
    public Customer getCustomerByNumber(String customerNumber) {
        if (customerNumber == null || customerNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("customerNumber must not be null or empty");
        }
        Optional<Customer> optional = customerDao.findByCustomerNumber(customerNumber);
        return optional.orElseThrow(() -> new IllegalArgumentException("Customer not found with number: " + customerNumber));
    }

    @Override
    public Customer getCustomerByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("email must not be null or empty");
        }
        Optional<Customer> optional = customerDao.findByEmail(email);
        return optional.orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));
    }

    @Override
    public Customer updateCustomerProfile(UserSession session, Customer customer) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (customer == null || customer.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer and customerId must not be null.");
        }
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(customer.getCustomerId());
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Users can only update their own customer profile.");
        }
        Customer existing = getCustomerProfile(customer.getCustomerId());
        existing.setCustomerName(customer.getCustomerName());
        existing.setEmail(customer.getEmail());
        existing.setMobileNumber(customer.getMobileNumber());
        existing.setAddress(customer.getAddress());
        existing.setCity(customer.getCity());

        boolean updated = customerDao.update(existing);
        if (!updated) {
            throw new IllegalStateException("Failed to update customer profile.");
        }
        return existing;
    }

    @Override
    public void updateAccountStatus(UserSession session, Long customerId, CustomerAccountStatus newStatus) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (customerId == null || newStatus == null) {
            throw new IllegalArgumentException("customerId and newStatus must not be null.");
        }
        Customer customer = getCustomerProfile(customerId);
        customer.setAccountStatus(newStatus);
        boolean updated = customerDao.update(customer);
        if (!updated) {
            throw new IllegalStateException("Failed to update customer account status.");
        }
    }

    @Override
    public void updateIdentityStatus(UserSession session, Long customerId, IdentityStatus newStatus) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (customerId == null || newStatus == null) {
            throw new IllegalArgumentException("customerId and newStatus must not be null.");
        }
        Customer customer = getCustomerProfile(customerId);
        customer.setIdentityStatus(newStatus);
        boolean updated = customerDao.update(customer);
        if (!updated) {
            throw new IllegalStateException("Failed to update customer identity status.");
        }
    }

    @Override
    public void checkCustomerEligibility(Long customerId) throws CustomerNotEligibleException {
        Customer customer = getCustomerProfile(customerId);
        if (customer.getAccountStatus() == CustomerAccountStatus.SUSPENDED) {
            throw new CustomerNotEligibleException("Customer account is SUSPENDED.");
        }
        if (customer.getAccountStatus() == CustomerAccountStatus.CLOSED) {
            throw new CustomerNotEligibleException("Customer account is CLOSED.");
        }
        if (customer.getIdentityStatus() == IdentityStatus.REJECTED) {
            throw new CustomerNotEligibleException("Customer identity status is REJECTED.");
        }
    }

    @Override
    public List<Customer> getAllCustomers(UserSession session) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        return customerDao.findAll();
    }
}
