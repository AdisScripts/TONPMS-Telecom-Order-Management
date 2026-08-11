package com.amdocs.telecom.integration;

import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.dao.impl.CustomerDaoImpl;
import com.amdocs.telecom.dao.impl.TelecomOrderDaoImpl;
import com.amdocs.telecom.dao.impl.TelecomProductDaoImpl;
import com.amdocs.telecom.exception.DatabaseException;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Manual real-MySQL smoke test; temporary rows are removed in reverse FK order. */
public final class DaoIntegrationSmokeTest {
    private DaoIntegrationSmokeTest() { }
    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            System.out.println("Connected to: " + connection.getMetaData().getDatabaseProductName());
        } catch (DatabaseException ex) {
            System.out.println("SKIPPED: local MySQL configuration/Connector-J is unavailable: " + ex.getMessage());
            return;
        } catch (Exception ex) { throw new IllegalStateException("Connection close failed.", ex); }
        String suffix = String.valueOf(System.currentTimeMillis());
        CustomerDao customers = new CustomerDaoImpl(); TelecomProductDao products = new TelecomProductDaoImpl(); TelecomOrderDao orders = new TelecomOrderDaoImpl();
        Long customerId = null; Long productId = null; Long orderId = null;
        try {
            Customer c = new Customer("TEST-CUST-" + suffix, "DAO Test", "dao" + suffix + "@example.test", "900" + suffix.substring(suffix.length() - 7), CustomerType.INDIVIDUAL);
            c.setAddress("Test Address"); c.setCity("Mumbai"); c.setIdentityStatus(IdentityStatus.VERIFIED); c.setAccountStatus(CustomerAccountStatus.ACTIVE); c.setRegistrationDate(LocalDate.now());
            customerId = customers.save(c); require(customerId > 0 && customers.findById(customerId).isPresent(), "Customer insert/select/generated key failed"); c.setCity("Pune"); require(customers.update(c), "Customer update failed");
            TelecomProduct p = new TelecomProduct("TEST-PROD-" + suffix, "DAO Test Product", "MOBILE_PLAN", new BigDecimal("1.00")); p.setActivationFee(BigDecimal.ZERO); p.setContractPeriod(0); p.setStatus(ProductStatus.ACTIVE);
            productId = products.save(p); require(productId > 0 && products.findByProductCode(p.getProductCode()).isPresent(), "Product insert/select failed");
            TelecomOrder o = new TelecomOrder("TEST-ORD-" + suffix, customerId, OrderType.NEW_CONNECTION); o.setOrderDate(LocalDateTime.now()); o.setTotalAmount(new BigDecimal("1.00")); o.setPaymentStatus(PaymentStatus.PENDING); o.setOrderStatus(OrderStatus.CREATED);
            orderId = orders.save(o); require(orderId > 0 && orders.findByOrderNumber(o.getOrderNumber()).isPresent(), "Order insert/select failed"); System.out.println("PASS: connection, prepared-statement CRUD, and generated keys verified.");
        } finally { if (orderId != null) orders.delete(orderId); if (productId != null) products.delete(productId); if (customerId != null) customers.delete(customerId); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
