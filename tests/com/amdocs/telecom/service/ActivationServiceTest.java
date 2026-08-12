package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.dao.CustomerSubscriptionDao;
import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProvisioningException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerSubscription;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.SubscriptionStatus;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.ActivationServiceImpl;
import com.amdocs.telecom.service.impl.AuditServiceImpl;
import com.amdocs.telecom.service.impl.NotificationServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ActivationServiceTest {

    public static void main(String[] args) {
        System.out.println("Running ActivationServiceTest...");
        testServiceActivationUncompletedProvisioning();
        testSuccessfulServiceActivationTransaction();
        testCompleteOrderLifecycle();
        System.out.println("PASS: ActivationServiceTest completed successfully.");
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

    private static void testServiceActivationUncompletedProvisioning() {
        MockCustomerSubscriptionDao subDao = new MockCustomerSubscriptionDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockNotificationDao notifDao = new MockNotificationDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        NotificationService notifService = new NotificationServiceImpl(notifDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        ActivationService service = new ActivationServiceImpl(subDao, orderDao, itemDao, productDao,
                inventoryDao, reqDao, notifService, auditService);

        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(1L);
        order.setOrderStatus(OrderStatus.PROVISIONING);
        long orderId = orderDao.save(order);

        ProvisioningRequest req = new ProvisioningRequest();
        req.setOrderId(orderId);
        req.setStatus(ProvisioningStatus.IN_PROGRESS); // Not SUCCESS yet
        reqDao.save(req);

        UserSession engSession = createSession(10L, null, RoleCode.PROVISIONING_ENGINEER);
        try {
            service.activateService(engSession, orderId);
            require(false, "Activating with IN_PROGRESS provisioning should throw ProvisioningException");
        } catch (ProvisioningException ex) {
            require(ex.getMessage().contains("is not in SUCCESS state"), "Expected uncompleted provisioning message");
        }
    }

    private static void testSuccessfulServiceActivationTransaction() {
        MockCustomerSubscriptionDao subDao = new MockCustomerSubscriptionDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockNotificationDao notifDao = new MockNotificationDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        NotificationService notifService = new NotificationServiceImpl(notifDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        ActivationService service = new ActivationServiceImpl(subDao, orderDao, itemDao, productDao,
                inventoryDao, reqDao, notifService, auditService);

        // Product with 12-month contract
        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setContractPeriod(12);
        long prodId = productDao.save(p);

        TelecomOrder order = new TelecomOrder();
        order.setOrderNumber("ORD-2026-000100");
        order.setCustomerId(50L);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setOrderStatus(OrderStatus.PROVISIONING);
        long orderId = orderDao.save(order);

        OrderItem orderItem = new OrderItem(prodId, 1, new BigDecimal("500.00"));
        orderItem.setOrderId(orderId);
        itemDao.save(orderItem);

        InventoryItem inv = new InventoryItem("SIM-500", InventoryItemType.SIM, "Mumbai");
        inv.setAssignedOrderId(orderId);
        inv.setStatus(InventoryStatus.RESERVED);
        inventoryDao.save(inv);

        ProvisioningRequest req = new ProvisioningRequest();
        req.setOrderId(orderId);
        req.setStatus(ProvisioningStatus.SUCCESS);
        reqDao.save(req);

        UserSession engSession = createSession(10L, null, RoleCode.PROVISIONING_ENGINEER);
        service.activateService(engSession, orderId);

        // 1. Order status should be ACTIVATED
        require(orderDao.findById(orderId).get().getOrderStatus() == OrderStatus.ACTIVATED, "Order status should be ACTIVATED");

        // 2. CustomerSubscription created with ACTIVE status and activationDate set
        List<CustomerSubscription> subs = subDao.findByCustomerId(50L);
        require(subs.size() == 1, "Exactly 1 subscription should be created");
        CustomerSubscription sub = subs.get(0);
        require(sub.getStatus() == SubscriptionStatus.ACTIVE, "Subscription status should be ACTIVE");
        require(sub.getActivationDate() != null, "Subscription activation date should be set");
        require(sub.getTerminationDate() != null, "Subscription termination date should be set");

        // 3. Inventory item status updated to INSTALLED
        require(inventoryDao.findById(inv.getInventoryId()).get().getStatus() == InventoryStatus.INSTALLED, "Inventory status should be INSTALLED");

        // 4. Notification sent
        List<Notification> notifs = notifDao.findByCustomerId(50L);
        require(notifs.size() == 1, "Notification should be saved");
        require(notifs.get(0).getMessage().contains("successfully activated"), "Expected notification message");

        // 5. Audit log saved
        require(auditDao.findAll().size() >= 1, "Audit log should be saved");
    }

    private static void testCompleteOrderLifecycle() {
        MockCustomerSubscriptionDao subDao = new MockCustomerSubscriptionDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockNotificationDao notifDao = new MockNotificationDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        NotificationService notifService = new NotificationServiceImpl(notifDao);
        AuditService auditService = new AuditServiceImpl(auditDao);

        ActivationService service = new ActivationServiceImpl(subDao, orderDao, itemDao, productDao,
                inventoryDao, reqDao, notifService, auditService);

        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(50L);
        order.setOrderStatus(OrderStatus.ACTIVATED);
        long orderId = orderDao.save(order);

        UserSession adminSession = createSession(999L, null, RoleCode.ORDER_ADMINISTRATOR);
        service.completeOrderLifecycle(adminSession, orderId);

        require(orderDao.findById(orderId).get().getOrderStatus() == OrderStatus.COMPLETED, "Order status should be updated to COMPLETED");
    }

    // Mock DAOs
    private static class MockCustomerSubscriptionDao implements CustomerSubscriptionDao {
        private final Map<Long, CustomerSubscription> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(CustomerSubscription entity) { long id = idSeq++; entity.setSubscriptionId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<CustomerSubscription> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<CustomerSubscription> findByServiceId(String serviceId) { return storage.values().stream().filter(s -> serviceId.equals(s.getServiceId())).findFirst(); }
        @Override public synchronized List<CustomerSubscription> findByCustomerId(Long customerId) { List<CustomerSubscription> res = new ArrayList<>(); for (CustomerSubscription s : storage.values()) { if (customerId.equals(s.getCustomerId())) res.add(s); } return res; }
        @Override public synchronized List<CustomerSubscription> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(CustomerSubscription entity) { if (storage.containsKey(entity.getSubscriptionId())) { storage.put(entity.getSubscriptionId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockTelecomOrderDao implements com.amdocs.telecom.dao.TelecomOrderDao {
        private final Map<Long, TelecomOrder> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(TelecomOrder entity) { long id = idSeq++; entity.setOrderId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<TelecomOrder> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<TelecomOrder> findByOrderNumber(String num) { return storage.values().stream().filter(o -> num.equals(o.getOrderNumber())).findFirst(); }
        @Override public synchronized List<TelecomOrder> findByCustomerId(Long cId) { List<TelecomOrder> res = new ArrayList<>(); for (TelecomOrder o : storage.values()) { if (cId.equals(o.getCustomerId())) res.add(o); } return res; }
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

    private static class MockProvisioningRequestDao implements ProvisioningRequestDao {
        private final Map<Long, ProvisioningRequest> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(ProvisioningRequest entity) { long id = idSeq++; entity.setProvisioningId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<ProvisioningRequest> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized List<ProvisioningRequest> findByOrderId(Long orderId) { List<ProvisioningRequest> res = new ArrayList<>(); for (ProvisioningRequest r : storage.values()) { if (orderId.equals(r.getOrderId())) res.add(r); } return res; }
        @Override public synchronized List<ProvisioningRequest> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(ProvisioningRequest entity) { if (storage.containsKey(entity.getProvisioningId())) { storage.put(entity.getProvisioningId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockNotificationDao implements NotificationDao {
        private final Map<Long, Notification> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(Notification entity) { long id = idSeq++; entity.setNotificationId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<Notification> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized List<Notification> findByCustomerId(Long cId) { List<Notification> res = new ArrayList<>(); for (Notification n : storage.values()) { if (cId.equals(n.getCustomerId())) res.add(n); } return res; }
        @Override public synchronized List<Notification> findByRecipientUserId(Long uId) { return new ArrayList<>(); }
        @Override public synchronized List<Notification> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(Notification entity) { if (storage.containsKey(entity.getNotificationId())) { storage.put(entity.getNotificationId(), entity); return true; } return false; }
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
