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

        AuditService auditService = new AuditServiceImpl(auditDao);
        ProvisioningService service = new ProvisioningServiceImpl(reqDao, engDao, orderDao, customerDao, auditService);

        ProvisioningEngineer e1 = new ProvisioningEngineer("ENG-20", "Engineer 20", "5G_SERVICE", "Delhi");
        e1.setAvailability(EngineerAvailability.AVAILABLE); e1.setActiveTasks(1); e1.setExperienceYears(6);
        engDao.save(e1);

        ProvisioningRequest req = new ProvisioningRequest();
        req.setOrderId(50L); req.setServiceId("SRV-50"); req.setProvisioningType(ProvisioningType.FIVE_G_SERVICE);
        req.setEngineerId(e1.getEngineerId()); req.setStatus(ProvisioningStatus.IN_PROGRESS);
        long reqId = reqDao.save(req);

        UserSession engSession = createSession(20L, null, RoleCode.PROVISIONING_ENGINEER);
        service.updateProvisioningStatus(engSession, reqId, ProvisioningStatus.SUCCESS, null);

        require(reqDao.findById(reqId).get().getStatus() == ProvisioningStatus.SUCCESS, "Status should be SUCCESS");
        require(engDao.findById(e1.getEngineerId()).get().getActiveTasks() == 0, "Engineer activeTasks should decrement to 0");
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
}
