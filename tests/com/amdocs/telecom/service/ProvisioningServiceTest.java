package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProvisioningException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.EngineerAvailability;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.AuditServiceImpl;
import com.amdocs.telecom.service.impl.ProvisioningServiceImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ProvisioningServiceTest {

    public static void main(String[] args) {
        System.out.println("Running ProvisioningServiceTest...");
        testEngineerRecommendationRanking();
        testCreateProvisioningRequestSuccess();
        testProvisioningStatusUpdate();
        System.out.println("PASS: ProvisioningServiceTest completed successfully.");
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

    private static void testEngineerRecommendationRanking() {
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockProvisioningEngineerDao engDao = new MockProvisioningEngineerDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        AuditService auditService = new AuditServiceImpl(auditDao);
        ProvisioningService service = new ProvisioningServiceImpl(reqDao, engDao, orderDao, customerDao, auditService);

        // Eng 1: SIM_ACTIVATION, Mumbai, 2 tasks, 5 yrs
        ProvisioningEngineer e1 = new ProvisioningEngineer("ENG-01", "Engineer 1", "SIM_ACTIVATION", "Mumbai");
        e1.setAvailability(EngineerAvailability.AVAILABLE); e1.setActiveTasks(2); e1.setExperienceYears(5);
        engDao.save(e1);

        // Eng 2: SIM_ACTIVATION, Mumbai, 1 task, 3 yrs (Lowest workload in Mumbai -> Should be picked for Mumbai)
        ProvisioningEngineer e2 = new ProvisioningEngineer("ENG-02", "Engineer 2", "SIM_ACTIVATION", "Mumbai");
        e2.setAvailability(EngineerAvailability.AVAILABLE); e2.setActiveTasks(1); e2.setExperienceYears(3);
        engDao.save(e2);

        // Eng 3: SIM_ACTIVATION, Delhi, 0 tasks, 10 yrs (Different region)
        ProvisioningEngineer e3 = new ProvisioningEngineer("ENG-03", "Engineer 3", "SIM_ACTIVATION", "Delhi");
        e3.setAvailability(EngineerAvailability.AVAILABLE); e3.setActiveTasks(0); e3.setExperienceYears(10);
        engDao.save(e3);

        // 1. Mumbai customer -> e2 (Mumbai, lowest tasks in Mumbai)
        Optional<ProvisioningEngineer> rec1 = service.recommendEngineer(ProvisioningType.SIM_ACTIVATION, "Mumbai");
        require(rec1.isPresent(), "Should recommend engineer for Mumbai");
        require(rec1.get().getEngineerId().equals(e2.getEngineerId()), "Should pick e2 due to lowest workload in Mumbai region");

        // 2. Pune customer -> e3 (No Pune engineer -> Fallback to lowest workload overall: e3 with 0 tasks)
        Optional<ProvisioningEngineer> rec2 = service.recommendEngineer(ProvisioningType.SIM_ACTIVATION, "Pune");
        require(rec2.isPresent(), "Should fallback to available engineer");
        require(rec2.get().getEngineerId().equals(e3.getEngineerId()), "Fallback should pick e3 due to 0 tasks");
    }

    private static void testCreateProvisioningRequestSuccess() {
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockProvisioningEngineerDao engDao = new MockProvisioningEngineerDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();

        AuditService auditService = new AuditServiceImpl(auditDao);
        ProvisioningService service = new ProvisioningServiceImpl(reqDao, engDao, orderDao, customerDao, auditService);

        ProvisioningEngineer e1 = new ProvisioningEngineer("ENG-10", "Broadband Tech", "BROADBAND", "Mumbai");
        e1.setAvailability(EngineerAvailability.AVAILABLE); e1.setActiveTasks(0); e1.setExperienceYears(4);
        engDao.save(e1);

        Customer c = new Customer();
        c.setCity("Mumbai");
        long custId = customerDao.save(c);

        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(custId);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setOrderStatus(OrderStatus.INVENTORY_RESERVED);
        long orderId = orderDao.save(order);

        UserSession adminSession = createSession(999L, null, RoleCode.ORDER_ADMINISTRATOR);
        ProvisioningRequest req = service.createProvisioningRequest(adminSession, orderId, ProvisioningType.BROADBAND);

        require(req.getProvisioningId() != null, "Request should have generated ID");
        require(req.getStatus() == ProvisioningStatus.IN_PROGRESS, "Request status should be IN_PROGRESS");
        require(req.getEngineerId().equals(e1.getEngineerId()), "Should assign e1");
        require(engDao.findById(e1.getEngineerId()).get().getActiveTasks() == 1, "Engineer activeTasks should be incremented to 1");
        require(orderDao.findById(orderId).get().getOrderStatus() == OrderStatus.PROVISIONING, "Order status should be PROVISIONING");
    }

    private static void testProvisioningStatusUpdate() {
        MockProvisioningRequestDao reqDao = new MockProvisioningRequestDao();
        MockProvisioningEngineerDao engDao = new MockProvisioningEngineerDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockAuditLogDao auditDao = new MockAuditLogDao();
        MockCustomerSubscriptionDao subDao = new MockCustomerSubscriptionDao();
        MockOrderItemDao orderItemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockNotificationService notificationService = new MockNotificationService();

        AuditService auditService = new AuditServiceImpl(auditDao);
        ActivationService activationService = new com.amdocs.telecom.service.impl.ActivationServiceImpl(
                subDao, orderDao, orderItemDao, productDao, inventoryDao, reqDao, notificationService, auditService
        );
        ProvisioningService service = new ProvisioningServiceImpl(reqDao, engDao, orderDao, customerDao, auditService, activationService);

        // Engineer 20
        ProvisioningEngineer e1 = new ProvisioningEngineer("ENG-20", "Engineer 20", "5G_SERVICE", "Delhi");
        e1.setAvailability(EngineerAvailability.AVAILABLE); e1.setActiveTasks(1); e1.setExperienceYears(6);
        engDao.save(e1);

        // Product
        com.amdocs.telecom.model.TelecomProduct prod = new com.amdocs.telecom.model.TelecomProduct("P-5G", "5G Plan", "MOBILE_PLAN", new java.math.BigDecimal("999.00"));
        prod.setContractPeriod(12);
        long prodId = productDao.save(prod);

        // Order
        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(101L);
        order.setOrderNumber("ORD-50");
        order.setOrderStatus(OrderStatus.PROVISIONING);
        long orderId = orderDao.save(order);

        com.amdocs.telecom.model.OrderItem oItem = new com.amdocs.telecom.model.OrderItem(prodId, 1, new java.math.BigDecimal("999.00"));
        oItem.setOrderId(orderId);
        orderItemDao.save(oItem);

        com.amdocs.telecom.model.InventoryItem inv = new com.amdocs.telecom.model.InventoryItem("SIM-50", com.amdocs.telecom.model.InventoryItemType.SIM, "Delhi");
        inv.setStatus(com.amdocs.telecom.model.InventoryStatus.RESERVED);
        inv.setAssignedOrderId(orderId);
        inventoryDao.save(inv);

        ProvisioningRequest req = new ProvisioningRequest();
        req.setOrderId(orderId); req.setServiceId("SRV-50"); req.setProvisioningType(ProvisioningType.FIVE_G_SERVICE);
        req.setEngineerId(e1.getEngineerId()); req.setStatus(ProvisioningStatus.IN_PROGRESS);
        long reqId = reqDao.save(req);

        UserSession engSession = createSession(20L, null, RoleCode.PROVISIONING_ENGINEER);

        // Test 1: SUCCESS update triggers activation
        service.updateProvisioningStatus(engSession, reqId, ProvisioningStatus.SUCCESS, null);

        require(reqDao.findById(reqId).get().getStatus() == ProvisioningStatus.SUCCESS, "Status should be SUCCESS");
        require(engDao.findById(e1.getEngineerId()).get().getActiveTasks() == 0, "Engineer activeTasks should decrement to 0");
        require(orderDao.findById(orderId).get().getOrderStatus() == OrderStatus.ACTIVATED, "Order status should become ACTIVATED");
        require(subDao.findByCustomerId(101L).size() == 1, "CustomerSubscription should be created");
        require(inventoryDao.findById(inv.getInventoryId()).get().getStatus() == com.amdocs.telecom.model.InventoryStatus.INSTALLED, "Inventory status should be INSTALLED");

        // Test 1B: Resubmitting SUCCESS on already ACTIVATED order does not duplicate subscription or decrement activeTasks below 0
        service.updateProvisioningStatus(engSession, reqId, ProvisioningStatus.SUCCESS, null);
        require(subDao.findByCustomerId(101L).size() == 1, "Subscription count must remain 1 on repeated SUCCESS call");
        require(engDao.findById(e1.getEngineerId()).get().getActiveTasks() == 0, "Engineer activeTasks must remain 0 on repeated SUCCESS call");

        // Test 2: Already-SUCCESS request whose order is still PROVISIONING (e.g. Order 25 case) -> Triggers activation
        TelecomOrder order25 = new TelecomOrder();
        order25.setCustomerId(105L);
        order25.setOrderNumber("ORD-25");
        order25.setOrderStatus(OrderStatus.PROVISIONING);
        long order25Id = orderDao.save(order25);

        com.amdocs.telecom.model.OrderItem oItem25 = new com.amdocs.telecom.model.OrderItem(prodId, 1, new java.math.BigDecimal("999.00"));
        oItem25.setOrderId(order25Id);
        orderItemDao.save(oItem25);

        ProvisioningRequest req25 = new ProvisioningRequest();
        req25.setOrderId(order25Id); req25.setServiceId("SRV-25"); req25.setProvisioningType(ProvisioningType.FIVE_G_SERVICE);
        req25.setEngineerId(e1.getEngineerId()); req25.setStatus(ProvisioningStatus.SUCCESS); // already SUCCESS
        long req25Id = reqDao.save(req25);

        service.updateProvisioningStatus(engSession, req25Id, ProvisioningStatus.SUCCESS, null);
        require(orderDao.findById(order25Id).get().getOrderStatus() == OrderStatus.ACTIVATED, "Already-SUCCESS request with PROVISIONING order must activate order");
        require(subDao.findByCustomerId(105L).size() == 1, "CustomerSubscription must be created for Order 25");
        require(engDao.findById(e1.getEngineerId()).get().getActiveTasks() == 0, "Active tasks must not decrement when request was already SUCCESS");

        // Test 3: FAILED update on another request does not activate
        ProvisioningEngineer e2 = new ProvisioningEngineer("ENG-21", "Engineer 21", "BROADBAND", "Delhi");
        e2.setAvailability(EngineerAvailability.AVAILABLE); e2.setActiveTasks(1); e2.setExperienceYears(4);
        engDao.save(e2);

        TelecomOrder orderFail = new TelecomOrder();
        orderFail.setCustomerId(102L);
        orderFail.setOrderNumber("ORD-51");
        orderFail.setOrderStatus(OrderStatus.PROVISIONING);
        long failOrderId = orderDao.save(orderFail);

        ProvisioningRequest reqFail = new ProvisioningRequest();
        reqFail.setOrderId(failOrderId); reqFail.setServiceId("SRV-51"); reqFail.setProvisioningType(ProvisioningType.BROADBAND);
        reqFail.setEngineerId(e2.getEngineerId()); reqFail.setStatus(ProvisioningStatus.IN_PROGRESS);
        long reqFailId = reqDao.save(reqFail);

        service.updateProvisioningStatus(engSession, reqFailId, ProvisioningStatus.FAILED, "Line fault detected");
        require(reqDao.findById(reqFailId).get().getStatus() == ProvisioningStatus.FAILED, "Status should be FAILED");
        require(engDao.findById(e2.getEngineerId()).get().getActiveTasks() == 0, "Engineer activeTasks should decrement to 0");
        require(orderDao.findById(failOrderId).get().getOrderStatus() == OrderStatus.PROVISIONING, "FAILED provisioning must not activate order");
        require(subDao.findByCustomerId(102L).isEmpty(), "FAILED provisioning must not create subscription");
    }

    // Mock DAOs
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
        @Override public synchronized Optional<ProvisioningEngineer> findByEmployeeCode(String empCode) { return storage.values().stream().filter(e -> empCode.equals(e.getEmployeeCode())).findFirst(); }
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
        @Override public synchronized List<TelecomOrder> findByCustomerId(Long cId) { List<TelecomOrder> res = new ArrayList<>(); for (TelecomOrder o : storage.values()) { if (cId.equals(o.getCustomerId())) res.add(o); } return res; }
        @Override public synchronized List<TelecomOrder> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(TelecomOrder entity) { if (storage.containsKey(entity.getOrderId())) { storage.put(entity.getOrderId(), entity); return true; } return false; }
        @Override public synchronized boolean delete(Long id) { return storage.remove(id) != null; }
    }

    private static class MockCustomerDao implements CustomerDao {
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

    private static class MockCustomerSubscriptionDao implements com.amdocs.telecom.dao.CustomerSubscriptionDao {
        private final Map<Long, com.amdocs.telecom.model.CustomerSubscription> map = new HashMap<>();
        private long seq = 1L;
        @Override public synchronized long save(com.amdocs.telecom.model.CustomerSubscription sub) { sub.setSubscriptionId(seq++); map.put(sub.getSubscriptionId(), sub); return sub.getSubscriptionId(); }
        @Override public synchronized Optional<com.amdocs.telecom.model.CustomerSubscription> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<com.amdocs.telecom.model.CustomerSubscription> findByServiceId(String serviceId) { return map.values().stream().filter(s -> serviceId.equals(s.getServiceId())).findFirst(); }
        @Override public synchronized List<com.amdocs.telecom.model.CustomerSubscription> findByCustomerId(Long customerId) { List<com.amdocs.telecom.model.CustomerSubscription> res = new ArrayList<>(); for (com.amdocs.telecom.model.CustomerSubscription s : map.values()) { if (customerId.equals(s.getCustomerId())) res.add(s); } return res; }
        @Override public synchronized List<com.amdocs.telecom.model.CustomerSubscription> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(com.amdocs.telecom.model.CustomerSubscription sub) { map.put(sub.getSubscriptionId(), sub); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockOrderItemDao implements com.amdocs.telecom.dao.OrderItemDao {
        private final Map<Long, com.amdocs.telecom.model.OrderItem> map = new HashMap<>();
        private long seq = 1L;
        @Override public synchronized long save(com.amdocs.telecom.model.OrderItem item) { item.setOrderItemId(seq++); map.put(item.getOrderItemId(), item); return item.getOrderItemId(); }
        @Override public synchronized Optional<com.amdocs.telecom.model.OrderItem> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized List<com.amdocs.telecom.model.OrderItem> findByOrderId(Long orderId) { List<com.amdocs.telecom.model.OrderItem> res = new ArrayList<>(); for (com.amdocs.telecom.model.OrderItem item : map.values()) { if (orderId.equals(item.getOrderId())) res.add(item); } return res; }
        @Override public synchronized int[] saveBatch(List<com.amdocs.telecom.model.OrderItem> items) { for (com.amdocs.telecom.model.OrderItem item : items) save(item); return new int[]{items.size()}; }
        @Override public synchronized List<com.amdocs.telecom.model.OrderItem> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(com.amdocs.telecom.model.OrderItem item) { map.put(item.getOrderItemId(), item); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockTelecomProductDao implements com.amdocs.telecom.dao.TelecomProductDao {
        private final Map<Long, com.amdocs.telecom.model.TelecomProduct> map = new HashMap<>();
        private long seq = 1L;
        @Override public synchronized long save(com.amdocs.telecom.model.TelecomProduct prod) { prod.setProductId(seq++); map.put(prod.getProductId(), prod); return prod.getProductId(); }
        @Override public synchronized Optional<com.amdocs.telecom.model.TelecomProduct> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<com.amdocs.telecom.model.TelecomProduct> findByProductCode(String code) { return map.values().stream().filter(p -> code.equals(p.getProductCode())).findFirst(); }
        @Override public synchronized List<com.amdocs.telecom.model.TelecomProduct> findActiveProducts() { return new ArrayList<>(map.values()); }
        @Override public synchronized List<com.amdocs.telecom.model.TelecomProduct> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(com.amdocs.telecom.model.TelecomProduct prod) { map.put(prod.getProductId(), prod); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockInventoryItemDao implements com.amdocs.telecom.dao.InventoryItemDao {
        private final Map<Long, com.amdocs.telecom.model.InventoryItem> map = new HashMap<>();
        private long seq = 1L;
        @Override public synchronized long save(com.amdocs.telecom.model.InventoryItem item) { item.setInventoryId(seq++); map.put(item.getInventoryId(), item); return item.getInventoryId(); }
        @Override public synchronized Optional<com.amdocs.telecom.model.InventoryItem> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<com.amdocs.telecom.model.InventoryItem> findByItemCode(String code) { return map.values().stream().filter(i -> code.equalsIgnoreCase(i.getItemCode())).findFirst(); }
        @Override public synchronized List<com.amdocs.telecom.model.InventoryItem> findByStatus(String status) { List<com.amdocs.telecom.model.InventoryItem> res = new ArrayList<>(); for (com.amdocs.telecom.model.InventoryItem i : map.values()) { if (i.getStatus() != null && i.getStatus().name().equalsIgnoreCase(status)) res.add(i); } return res; }
        @Override public synchronized List<com.amdocs.telecom.model.InventoryItem> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(com.amdocs.telecom.model.InventoryItem item) { map.put(item.getInventoryId(), item); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockNotificationService implements com.amdocs.telecom.service.NotificationService {
        @Override public void sendNotification(Long customerId, String message) { }
        @Override public com.amdocs.telecom.model.Notification createPendingNotification(Long customerId, String message) { com.amdocs.telecom.model.Notification n = new com.amdocs.telecom.model.Notification("INFO", message); n.setCustomerId(customerId); return n; }
        @Override public List<com.amdocs.telecom.model.Notification> getNotificationsForCustomer(UserSession session, Long customerId) { return new ArrayList<>(); }
        @Override public void markAsRead(UserSession session, Long notificationId) { }
    }
}
