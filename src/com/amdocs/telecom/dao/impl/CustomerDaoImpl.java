package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.IdentityStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CustomerDaoImpl extends AbstractJdbcDao implements CustomerDao {
    private static final String COLUMNS = "customer_id, customer_number, customer_name, email, mobile_number, customer_type, address, city, identity_status, account_status, registration_date, created_at, updated_at";

    public long save(final Customer customer) {
        long id = insert("INSERT INTO customer (customer_number, customer_name, email, mobile_number, customer_type, address, city, identity_status, account_status, registration_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", s -> {
            s.setString(1, customer.getCustomerNumber()); s.setString(2, customer.getCustomerName()); s.setString(3, customer.getEmail()); s.setString(4, customer.getMobileNumber());
            s.setString(5, customer.getCustomerType().name()); s.setString(6, customer.getAddress()); s.setString(7, customer.getCity()); s.setString(8, customer.getIdentityStatus().name());
            s.setString(9, customer.getAccountStatus().name()); s.setDate(10, Date.valueOf(customer.getRegistrationDate()));
        }); customer.setCustomerId(id); return id;
    }
    public Optional<Customer> findById(Long id) { return queryOne("SELECT " + COLUMNS + " FROM customer WHERE customer_id = ?", s -> s.setLong(1, id), this::mapRow); }
    public Optional<Customer> findByCustomerNumber(String number) { return queryOne("SELECT " + COLUMNS + " FROM customer WHERE customer_number = ?", s -> s.setString(1, number), this::mapRow); }
    public Optional<Customer> findByEmail(String email) { return queryOne("SELECT " + COLUMNS + " FROM customer WHERE email = ?", s -> s.setString(1, email), this::mapRow); }
    public List<Customer> findAll() { return queryList("SELECT " + COLUMNS + " FROM customer ORDER BY customer_id", s -> { }, this::mapRow); }
    public boolean update(final Customer c) { return executeUpdate("UPDATE customer SET customer_number=?, customer_name=?, email=?, mobile_number=?, customer_type=?, address=?, city=?, identity_status=?, account_status=?, registration_date=? WHERE customer_id=?", s -> {
        s.setString(1,c.getCustomerNumber()); s.setString(2,c.getCustomerName()); s.setString(3,c.getEmail()); s.setString(4,c.getMobileNumber()); s.setString(5,c.getCustomerType().name()); s.setString(6,c.getAddress()); s.setString(7,c.getCity()); s.setString(8,c.getIdentityStatus().name()); s.setString(9,c.getAccountStatus().name()); s.setDate(10,Date.valueOf(c.getRegistrationDate())); s.setLong(11,c.getCustomerId()); }); }
    public boolean delete(Long id) { return executeUpdate("DELETE FROM customer WHERE customer_id = ?", s -> s.setLong(1,id)); }
    private Customer mapRow(ResultSet rs) throws SQLException { Customer c=new Customer(); c.setCustomerId(rs.getLong("customer_id")); c.setCustomerNumber(rs.getString("customer_number")); c.setCustomerName(rs.getString("customer_name")); c.setEmail(rs.getString("email")); c.setMobileNumber(rs.getString("mobile_number")); c.setCustomerType(CustomerType.valueOf(rs.getString("customer_type"))); c.setAddress(rs.getString("address")); c.setCity(rs.getString("city")); c.setIdentityStatus(IdentityStatus.valueOf(rs.getString("identity_status"))); c.setAccountStatus(CustomerAccountStatus.valueOf(rs.getString("account_status"))); c.setRegistrationDate(localDate(rs,"registration_date")); c.setCreatedAt(localDateTime(rs,"created_at")); c.setUpdatedAt(localDateTime(rs,"updated_at")); return c; }
}
