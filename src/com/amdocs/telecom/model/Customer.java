package com.amdocs.telecom.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Customer {
    private Long customerId;
    private String customerNumber;
    private String customerName;
    private String email;
    private String mobileNumber;
    private CustomerType customerType;
    private String address;
    private String city;
    private IdentityStatus identityStatus;
    private CustomerAccountStatus accountStatus;
    private LocalDate registrationDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Non-persistent relationship collections from the approved ER design.
    private List<TelecomOrder> orders = new ArrayList<TelecomOrder>();
    private List<CustomerSubscription> subscriptions = new ArrayList<CustomerSubscription>();
    private List<Notification> notifications = new ArrayList<Notification>();

    public Customer() { }

    public Customer(String customerNumber, String customerName, String email, String mobileNumber,
                    CustomerType customerType) {
        this.customerNumber = customerNumber;
        this.customerName = customerName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.customerType = customerType;
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public IdentityStatus getIdentityStatus() { return identityStatus; }
    public void setIdentityStatus(IdentityStatus identityStatus) { this.identityStatus = identityStatus; }
    public CustomerAccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(CustomerAccountStatus accountStatus) { this.accountStatus = accountStatus; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<TelecomOrder> getOrders() { return orders; }
    public void setOrders(List<TelecomOrder> orders) { this.orders = orders; }
    public List<CustomerSubscription> getSubscriptions() { return subscriptions; }
    public void setSubscriptions(List<CustomerSubscription> subscriptions) { this.subscriptions = subscriptions; }
    public List<Notification> getNotifications() { return notifications; }
    public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }

    @Override
    public String toString() {
        return "Customer{customerId=" + customerId + ", customerNumber='" + customerNumber
                + "', customerName='" + customerName + "', customerType=" + customerType + "}";
    }
}
