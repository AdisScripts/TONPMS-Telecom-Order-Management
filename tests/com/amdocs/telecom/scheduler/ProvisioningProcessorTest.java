package com.amdocs.telecom.scheduler;

import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.dao.CustomerSubscriptionDao;
import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.CustomerSubscription;
import com.amdocs.telecom.model.EngineerAvailability;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.Notification;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ActivationService;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.service.ProvisioningService;
import com.amdocs.telecom.service.impl.ActivationServiceImpl;
import com.amdocs.telecom.service.impl.AuditServiceImpl;
import com.amdocs.telecom.service.impl.NotificationServiceImpl;
import com.amdocs.telecom.service.impl.ProvisioningServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProvisioningProcessorTest {

    public static void main(String[] args) {
        System.out.println("Running ProvisioningProcessorTest...");
        testProvisioningExecutionAndActivation();
        testConcurrentProvisioningRequests();
        System.out.println("PASS: ProvisioningProcessorTest completed successfully.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }

    private static void testProvisioningExecutionAndActivation() {
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockProvisioningEngineerDao engDao = new MockProvisioningEngineerDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockCustomerSubscriptionDao subDao = new MockCustomerSubscriptionDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockNotificationDao notifDao = new MockNotificationDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        AuditService auditService = new AuditServiceImpl(auditDao);
        NotificationService notificationService = new NotificationServiceImpl(notifDao);
        NotificationProcessor notificationProcessor = new NotificationProcessor(notifDao, 1);

        ProvisioningService provisioningService = new ProvisioningServiceImpl(reqDao, engDao, orderDao, customerDao, auditService);
        ActivationService activationService = new ActivationServiceImpl(subDao, orderDao, itemDao, productDao, inventoryDao, reqDao, notificationService, auditService);

        UserSession systemSession = new UserSession(0L, "SYSTEM_SCHEDULER", null, null,
                new java.util.HashSet<>(Arrays.asList(RoleCode.ORDER_ADMINISTRATOR, RoleCode.PROVISIONING_ENGINEER)));

        ProvisioningProcessor processor = new ProvisioningProcessor(
                provisioningService, activationService, orderDao, auditService,
                notificationService, notificationProcessor, systemSession, 2
        );

        // Setup product & order
        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setContractPeriod(12);
        long prodId = productDao.save(p);

        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(50L);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setOrderStatus(OrderStatus.INVENTORY_RESERVED);
        long orderId = orderDao.save(order);

        OrderItem oi = new OrderItem(prodId, 1, new BigDecimal("500.00"));
        oi.setOrderId(orderId);
        itemDao.save(oi);

        // Engineer
        ProvisioningEngineer eng = new ProvisioningEngineer("E101", "Eng 1", "MOBILE_SERVICE", "Mumbai");
        eng.setAvailability(EngineerAvailability.AVAILABLE);
        eng.setActiveTasks(0);
        engDao.save(eng);

        // Create provisioning request -> moves order to PROVISIONING
        ProvisioningRequest req = provisioningService.createProvisioningRequest(systemSession, orderId, ProvisioningType.MOBILE_SERVICE);

        processor.start();
        processor.enqueueProvisioningRequest(req.getProvisioningId());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        processor.stop();

        Optional<TelecomOrder> updatedOrder = orderDao.findById(orderId);
        require(updatedOrder.get().getOrderStatus() == OrderStatus.ACTIVATED, "Order status should be updated to ACTIVATED");
    }

    private static void testConcurrentProvisioningRequests() {
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockProvisioningEngineerDao engDao = new MockProvisioningEngineerDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockCustomerSubscriptionDao subDao = new MockCustomerSubscriptionDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockNotificationDao notifDao = new MockNotificationDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        AuditService auditService = new AuditServiceImpl(auditDao);
        NotificationService notificationService = new NotificationServiceImpl(notifDao);
        NotificationProcessor notificationProcessor = new NotificationProcessor(notifDao, 2);

        ProvisioningService provisioningService = new ProvisioningServiceImpl(reqDao, engDao, orderDao, customerDao, auditService);
        ActivationService activationService = new ActivationServiceImpl(subDao, orderDao, itemDao, productDao, inventoryDao, reqDao, notificationService, auditService);

        UserSession systemSession = new UserSession(0L, "SYSTEM_SCHEDULER", null, null,
                new java.util.HashSet<>(Arrays.asList(RoleCode.ORDER_ADMINISTRATOR, RoleCode.PROVISIONING_ENGINEER)));

        ProvisioningProcessor processor = new ProvisioningProcessor(
                provisioningService, activationService, orderDao, auditService,
                notificationService, notificationProcessor, systemSession, 4
        );

        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        p.setContractPeriod(12);
        long prodId = productDao.save(p);

        // Engineer
        ProvisioningEngineer eng = new ProvisioningEngineer("E102", "Eng 2", "MOBILE_SERVICE", "Mumbai");
        eng.setAvailability(EngineerAvailability.AVAILABLE);
        eng.setActiveTasks(0);
        engDao.save(eng);

        List<Long> reqIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            TelecomOrder order = new TelecomOrder();
            order.setCustomerId((long) (100 + i));
            order.setPaymentStatus(PaymentStatus.SUCCESS);
            order.setOrderStatus(OrderStatus.INVENTORY_RESERVED);
            long orderId = orderDao.save(order);

            OrderItem oi = new OrderItem(prodId, 1, new BigDecimal("500.00"));
            oi.setOrderId(orderId);
            itemDao.save(oi);

            ProvisioningRequest req = provisioningService.createProvisioningRequest(systemSession, orderId, ProvisioningType.MOBILE_SERVICE);
            reqIds.add(req.getProvisioningId());
        }

        processor.start();
        for (Long rId : reqIds) {
            processor.enqueueProvisioningRequest(rId);
        }

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        processor.stop();

        for (Long rId : reqIds) {
            ProvisioningRequest r = reqDao.findById(rId).get();
            require(r.getStatus() == ProvisioningStatus.SUCCESS, "All concurrent requests should complete with SUCCESS");
        }
    }

    // Mocks
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

    private static class MockProvisioningEngineerDao implements ProvisioningEngineerDao {
        private final Map<Long, ProvisioningEngineer> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(ProvisioningEngineer entity) { long id = idSeq++; entity.setEngineerId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<ProvisioningEngineer> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<ProvisioningEngineer> findByEmployeeCode(String code) { return storage.values().stream().filter(e -> code.equals(e.getEmployeeCode())).findFirst(); }
        @Override public synchronized List<ProvisioningEngineer> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(ProvisioningEngineer entity) { if (storage.containsKey(entity.getEngineerId())) { storage.put(entity.getEngineerId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockTelecomOrderDao implements com.amdocs.telecom.dao.TelecomOrderDao {
        private final Map<Long, TelecomOrder> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(TelecomOrder entity) { long id = idSeq++; entity.setOrderId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<TelecomOrder> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<TelecomOrder> findByOrderNumber(String num) { return storage.values().stream().filter(o -> num.equals(o.getOrderNumber())).findFirst(); }
        @Override public synchronized List<TelecomOrder> findByCustomerId(Long cId) { List<TelecomOrder> list = new ArrayList<>(); for (TelecomOrder o : storage.values()) { if (cId.equals(o.getCustomerId())) list.add(o); } return list; }
        @Override public synchronized List<TelecomOrder> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(TelecomOrder entity) { if (storage.containsKey(entity.getOrderId())) { storage.put(entity.getOrderId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockCustomerDao implements com.amdocs.telecom.dao.CustomerDao {
        private final Map<Long, com.amdocs.telecom.model.Customer> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(com.amdocs.telecom.model.Customer entity) { long id = idSeq++; entity.setCustomerId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<com.amdocs.telecom.model.Customer> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<com.amdocs.telecom.model.Customer> findByCustomerNumber(String num) { return storage.values().stream().filter(c -> num.equals(c.getCustomerNumber())).findFirst(); }
        @Override public synchronized Optional<com.amdocs.telecom.model.Customer> findByEmail(String email) { return storage.values().stream().filter(c -> email.equals(c.getEmail())).findFirst(); }
        @Override public synchronized List<com.amdocs.telecom.model.Customer> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(com.amdocs.telecom.model.Customer entity) { if (storage.containsKey(entity.getCustomerId())) { storage.put(entity.getCustomerId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockCustomerSubscriptionDao implements CustomerSubscriptionDao {
        private final Map<Long, CustomerSubscription> storage = new HashMap<>();
        private long idSeq = 1L;
        @Override public synchronized long save(CustomerSubscription entity) { long id = idSeq++; entity.setSubscriptionId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<CustomerSubscription> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<CustomerSubscription> findByServiceId(String sId) { return storage.values().stream().filter(s -> sId.equals(s.getServiceId())).findFirst(); }
        @Override public synchronized List<CustomerSubscription> findByCustomerId(Long cId) { List<CustomerSubscription> list = new ArrayList<>(); for (CustomerSubscription s : storage.values()) { if (cId.equals(s.getCustomerId())) list.add(s); } return list; }
        @Override public synchronized List<CustomerSubscription> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(CustomerSubscription entity) { if (storage.containsKey(entity.getSubscriptionId())) { storage.put(entity.getSubscriptionId(), entity); return true; } return false; }
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
        @Override public synchronized Optional<TelecomProduct> findByProductCode(String code) { return storage.values().stream().filter(p -> code.equals(p.getProductCode())).findFirst(); }
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
