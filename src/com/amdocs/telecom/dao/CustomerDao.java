package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.Customer;
import java.util.Optional;
public interface CustomerDao extends CrudDao<Customer> {
    Optional<Customer> findByCustomerNumber(String customerNumber);
    Optional<Customer> findByEmail(String email);
}
