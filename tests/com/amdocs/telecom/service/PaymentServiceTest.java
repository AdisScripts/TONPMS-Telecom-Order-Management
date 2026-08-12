package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.OrderPaymentDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.InvalidOrderException;
import com.amdocs.telecom.exception.InventoryUnavailableException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.IdentityStatus;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.PaymentTransactionStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.AuditServiceImpl;
import com.amdocs.telecom.service.impl.CustomerServiceImpl;
import com.amdocs.telecom.service.impl.InventoryServiceImpl;
import com.amdocs.telecom.service.impl.OrderServiceImpl;
import com.amdocs.telecom.service.impl.PaymentServiceImpl;
import com.amdocs.telecom.service.impl.ProductServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PaymentServiceTest {

    public static void main(String[] args) {
        System.out.println("Running PaymentServiceTest...");
        testPaymentAmountMismatch();
        testInvalidOrderStateForPayment();
        testSuccessfulPaymentAndReservation();
        testPaymentRollbackOnInventoryUnavailable();
        System.out.println("PASS: PaymentServiceTest completed successfully.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }

    private static UserSession createSession(Long userId, Long customerId, RoleCode... roles) {
        Customer c = null;
        if (customerId != null) {
            c = new Customer();
            c.setCustomerId(customerId);
        }
        Set<RoleCode> roleSet = new HashSet<>(Arrays.asList(roles));
        return new UserSession(userId, "user" + userId, c, null, roleSet);
    }

    private static void testPaymentAmountMismatch() {
        MockOrderPaymentDao paymentDao = new MockOrderPaymentDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);
        OrderService orderService = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        InventoryService inventoryService = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        PaymentService paymentService = new PaymentServiceImpl(paymentDao, orderDao, orderService, inventoryService, auditService);

        Customer c = new Customer("C-100", "Payment User", "pay@test.com", "9000000001", CustomerType.INDIVIDUAL);
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long custId = customerDao.save(c);

        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setStatus(com.amdocs.telecom.model.ProductStatus.ACTIVE);
        long prodId = productDao.save(p);

        UserSession session = createSession(100L, custId, RoleCode.CUSTOMER);
        TelecomOrder order = orderService.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));

        // Try paying wrong amount -> IllegalArgumentException
        try {
            paymentService.processPayment(session, order.getOrderId(), new BigDecimal("499.00"), PaymentMode.CARD);
            require(false, "Mismatched payment amount should throw IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            require(ex.getMessage().contains("does not match order total amount"), "Expected amount mismatch message");
        }
    }

    private static void testInvalidOrderStateForPayment() {
        MockOrderPaymentDao paymentDao = new MockOrderPaymentDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);
        OrderService orderService = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        InventoryService inventoryService = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        PaymentService paymentService = new PaymentServiceImpl(paymentDao, orderDao, orderService, inventoryService, auditService);

        Customer c = new Customer("C-101", "Cancel User", "cancel@test.com", "9000000002", CustomerType.INDIVIDUAL);
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long custId = customerDao.save(c);

        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setStatus(com.amdocs.telecom.model.ProductStatus.ACTIVE);
        long prodId = productDao.save(p);

        UserSession session = createSession(101L, custId, RoleCode.CUSTOMER);
        TelecomOrder order = orderService.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));

        // Cancel order then try payment -> InvalidOrderException
        orderService.cancelOrder(session, order.getOrderId());
        try {
            paymentService.processPayment(session, order.getOrderId(), new BigDecimal("500.00"), PaymentMode.UPI);
            require(false, "Payment for CANCELLED order should throw InvalidOrderException");
        } catch (InvalidOrderException ex) {
            require(ex.getMessage().contains("Cannot process payment"), "Expected state rejection message");
        }
    }

    private static void testSuccessfulPaymentAndReservation() {
        MockOrderPaymentDao paymentDao = new MockOrderPaymentDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);
        OrderService orderService = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        InventoryService inventoryService = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        PaymentService paymentService = new PaymentServiceImpl(paymentDao, orderDao, orderService, inventoryService, auditService);

        // Add available SIM
        InventoryItem sim = new InventoryItem("SIM-101", InventoryItemType.SIM, "Mumbai Warehouse");
        sim.setStatus(InventoryStatus.AVAILABLE);
        inventoryDao.save(sim);

        Customer c = new Customer("C-102", "Valid User", "valid@test.com", "9000000003", CustomerType.INDIVIDUAL);
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long custId = customerDao.save(c);

        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setStatus(com.amdocs.telecom.model.ProductStatus.ACTIVE);
        long prodId = productDao.save(p);

        UserSession session = createSession(102L, custId, RoleCode.CUSTOMER);
        TelecomOrder order = orderService.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));

        OrderPayment payment = paymentService.processPayment(session, order.getOrderId(), new BigDecimal("500.00"), PaymentMode.NET_BANKING);
        require(payment.getPaymentId() != null, "Payment should have generated ID");
        require(payment.getStatus() == PaymentTransactionStatus.SUCCESS, "Payment status should be SUCCESS");

        TelecomOrder updatedOrder = orderDao.findById(order.getOrderId()).get();
        require(updatedOrder.getPaymentStatus() == PaymentStatus.SUCCESS, "Order paymentStatus should be SUCCESS");
        require(updatedOrder.getOrderStatus() == OrderStatus.INVENTORY_RESERVED, "Order status should be INVENTORY_RESERVED");

        require(inventoryDao.findById(sim.getInventoryId()).get().getStatus() == InventoryStatus.RESERVED, "Inventory status should be RESERVED");
        require(auditDao.findAll().size() >= 1, "Audit log should be recorded");
    }

    private static void testPaymentRollbackOnInventoryUnavailable() {
        MockOrderPaymentDao paymentDao = new MockOrderPaymentDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);
        OrderService orderService = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        InventoryService inventoryService = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        PaymentService paymentService = new PaymentServiceImpl(paymentDao, orderDao, orderService, inventoryService, auditService);

        // 0 SIM items available in stock
        Customer c = new Customer("C-103", "No Stock User", "nostock@test.com", "9000000004", CustomerType.INDIVIDUAL);
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long custId = customerDao.save(c);

        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setStatus(com.amdocs.telecom.model.ProductStatus.ACTIVE);
        long prodId = productDao.save(p);

        UserSession session = createSession(103L, custId, RoleCode.CUSTOMER);
        TelecomOrder order = orderService.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));

        try {
            paymentService.processPayment(session, order.getOrderId(), new BigDecimal("500.00"), PaymentMode.CARD);
            require(false, "Payment with no inventory should throw InventoryUnavailableException");
        } catch (InventoryUnavailableException ex) {
            require(ex.getMessage().contains("Insufficient inventory"), "Expected inventory unavailable message");
        }
    }

    // Mock DAOs
    private static class MockOrderPaymentDao implements OrderPaymentDao {
        private final Map<Long, OrderPayment> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(OrderPayment entity) { long id = idSeq++; entity.setPaymentId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<OrderPayment> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<OrderPayment> findByTransactionReference(String ref) { return storage.values().stream().filter(p -> ref.equals(p.getTransactionReference())).findFirst(); }
        @Override public synchronized List<OrderPayment> findByOrderId(Long orderId) { List<OrderPayment> res = new ArrayList<>(); for (OrderPayment p : storage.values()) { if (orderId.equals(p.getOrderId())) res.add(p); } return res; }
        @Override public synchronized List<OrderPayment> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(OrderPayment entity) { if (storage.containsKey(entity.getPaymentId())) { storage.put(entity.getPaymentId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockTelecomOrderDao implements com.amdocs.telecom.dao.TelecomOrderDao {
        private final Map<Long, TelecomOrder> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(TelecomOrder entity) { long id = idSeq++; entity.setOrderId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<TelecomOrder> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<TelecomOrder> findByOrderNumber(String orderNumber) { return storage.values().stream().filter(o -> orderNumber.equals(o.getOrderNumber())).findFirst(); }
        @Override public synchronized List<TelecomOrder> findByCustomerId(Long customerId) { List<TelecomOrder> list = new ArrayList<>(); for (TelecomOrder o : storage.values()) { if (customerId.equals(o.getCustomerId())) list.add(o); } return list; }
        @Override public synchronized List<TelecomOrder> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(TelecomOrder entity) { if (storage.containsKey(entity.getOrderId())) { storage.put(entity.getOrderId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockOrderItemDao implements OrderItemDao {
        private final Map<Long, OrderItem> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(OrderItem entity) { long id = idSeq++; entity.setOrderItemId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<OrderItem> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized List<OrderItem> findByOrderId(Long orderId) { List<OrderItem> list = new ArrayList<>(); for (OrderItem item : storage.values()) { if (orderId.equals(item.getOrderId())) list.add(item); } return list; }
        @Override public synchronized int[] saveBatch(List<OrderItem> items) { for (OrderItem item : items) save(item); return new int[]{items.size()}; }
        @Override public synchronized List<OrderItem> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(OrderItem entity) { if (storage.containsKey(entity.getOrderItemId())) { storage.put(entity.getOrderItemId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockCustomerDao implements com.amdocs.telecom.dao.CustomerDao {
        private final Map<Long, Customer> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(Customer entity) { long id = idSeq++; entity.setCustomerId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<Customer> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<Customer> findByCustomerNumber(String num) { return storage.values().stream().filter(c -> num.equals(c.getCustomerNumber())).findFirst(); }
        @Override public synchronized Optional<Customer> findByEmail(String email) { return storage.values().stream().filter(c -> email.equals(c.getEmail())).findFirst(); }
        @Override public synchronized List<Customer> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(Customer entity) { if (storage.containsKey(entity.getCustomerId())) { storage.put(entity.getCustomerId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockTelecomProductDao implements TelecomProductDao {
        private final Map<Long, TelecomProduct> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(TelecomProduct entity) { long id = idSeq++; entity.setProductId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<TelecomProduct> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<TelecomProduct> findByProductCode(String productCode) { return storage.values().stream().filter(p -> productCode.equals(p.getProductCode())).findFirst(); }
        @Override public synchronized List<TelecomProduct> findActiveProducts() { return new ArrayList<>(storage.values()); }
        @Override public synchronized List<TelecomProduct> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(TelecomProduct entity) { if (storage.containsKey(entity.getProductId())) { storage.put(entity.getProductId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockInventoryItemDao implements InventoryItemDao {
        private final Map<Long, InventoryItem> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(InventoryItem entity) { long id = idSeq++; entity.setInventoryId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<InventoryItem> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<InventoryItem> findByItemCode(String code) { return storage.values().stream().filter(i -> code.equals(i.getItemCode())).findFirst(); }
        @Override public synchronized List<InventoryItem> findByStatus(String status) { List<InventoryItem> res = new ArrayList<>(); for (InventoryItem i : storage.values()) { if (i.getStatus() != null && i.getStatus().name().equalsIgnoreCase(status)) res.add(i); } return res; }
        @Override public synchronized List<InventoryItem> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(InventoryItem entity) { if (storage.containsKey(entity.getInventoryId())) { storage.put(entity.getInventoryId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockAuditLogDao implements AuditLogDao {
        private final Map<Long, AuditLog> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(AuditLog entity) { long id = idSeq++; entity.setAuditId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<AuditLog> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized List<AuditLog> findByEntity(String entityType, Long entityId) { return new ArrayList<>(storage.values()); }
        @Override public synchronized List<AuditLog> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(AuditLog entity) { if (storage.containsKey(entity.getAuditId())) { storage.put(entity.getAuditId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }
}
