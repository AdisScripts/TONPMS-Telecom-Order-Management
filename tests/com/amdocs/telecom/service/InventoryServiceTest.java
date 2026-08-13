package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.InventoryUnavailableException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.InventoryServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryServiceTest {

    public static void main(String[] args) {
        System.out.println("Running InventoryServiceTest...");
        testInventoryItemManagement();
        testUpdateInventoryItem();
        testInventoryTypeMapping();
        testInventoryReservationSuccess();
        testInventoryReservationUnavailable();
        testConcurrentInventoryReservation();
        System.out.println("PASS: InventoryServiceTest completed successfully.");
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

    private static void testInventoryItemManagement() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();

        InventoryService service = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);
        UserSession adminSession = createSession(100L, null, RoleCode.INVENTORY_ADMINISTRATOR);
        UserSession custSession = createSession(200L, 50L, RoleCode.CUSTOMER);

        InventoryItem item = new InventoryItem("SIM-101", InventoryItemType.SIM, "Mumbai Warehouse");
        InventoryItem added = service.addInventoryItem(adminSession, item);
        require(added.getInventoryId() != null, "Added item should have ID");
        require(added.getStatus() == InventoryStatus.AVAILABLE, "New item default status should be AVAILABLE");

        // Customer trying to add inventory item -> AccessDeniedException
        try {
            service.addInventoryItem(custSession, new InventoryItem("SIM-102", InventoryItemType.SIM, "Delhi Warehouse"));
            require(false, "Customer adding inventory should throw AccessDeniedException");
        } catch (AccessDeniedException ex) {
            // expected
        }

        // Status update by admin -> OK
        service.updateInventoryStatus(adminSession, added.getInventoryId(), InventoryStatus.RESERVED);
        require(service.getInventoryItemById(added.getInventoryId()).getStatus() == InventoryStatus.RESERVED, "Status update failed");
    }

    private static void testUpdateInventoryItem() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();

        InventoryService service = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);
        UserSession adminSession = createSession(100L, null, RoleCode.INVENTORY_ADMINISTRATOR);
        UserSession custSession = createSession(200L, 50L, RoleCode.CUSTOMER);

        InventoryItem item1 = new InventoryItem("SIM-001", InventoryItemType.SIM, "");
        item1.setSerialNumber("SN-111");
        item1.setStatus(InventoryStatus.AVAILABLE);
        item1.setAssignedOrderId(99L);
        inventoryDao.save(item1);

        InventoryItem item5 = new InventoryItem("ESIM-002", InventoryItemType.SIM, "Delhi");
        item5.setSerialNumber("SN-555");
        item5.setStatus(InventoryStatus.RESERVED);
        inventoryDao.save(item5);

        // 1. Edit item 1 (warehouse: blank -> Mumbai)
        InventoryItem updated1 = service.updateInventoryItem(adminSession, item1.getInventoryId(), "SIM-001", InventoryItemType.SIM, "Mumbai");
        require("Mumbai".equals(updated1.getWarehouse()), "Warehouse should be updated to Mumbai");
        require(updated1.getItemType() == InventoryItemType.SIM, "Item type should remain SIM");
        require(InventoryStatus.AVAILABLE == updated1.getStatus(), "Status should be preserved");
        require("SN-111".equals(updated1.getSerialNumber()), "SerialNumber should be preserved");
        require(Long.valueOf(99L).equals(updated1.getAssignedOrderId()), "AssignedOrderId should be preserved");

        // 2. Edit item 5 (type: SIM -> ESIM)
        InventoryItem updated5 = service.updateInventoryItem(adminSession, item5.getInventoryId(), "ESIM-002", InventoryItemType.ESIM, "Delhi");
        require(updated5.getItemType() == InventoryItemType.ESIM, "Item type should be updated to ESIM");
        require("Delhi".equals(updated5.getWarehouse()), "Warehouse should be Delhi");
        require(InventoryStatus.RESERVED == updated5.getStatus(), "Status RESERVED should be preserved");
        require("SN-555".equals(updated5.getSerialNumber()), "SerialNumber should be preserved");

        // 3. Customer session -> AccessDeniedException
        try {
            service.updateInventoryItem(custSession, item1.getInventoryId(), "SIM-001", InventoryItemType.SIM, "Mumbai");
            require(false, "Customer session should throw AccessDeniedException");
        } catch (AccessDeniedException ex) {
            // expected
        }

        // 4. Invalid inventory ID -> IllegalArgumentException
        try {
            service.updateInventoryItem(adminSession, 999L, "INVALID", InventoryItemType.SIM, "Mumbai");
            require(false, "Invalid inventory ID should throw IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    private static void testInventoryTypeMapping() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();

        InventoryService service = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);

        require(service.determineRequiredInventoryType("MOBILE_PLAN", "5G Plan", "NEW_CONNECTION") == InventoryItemType.SIM, "MOBILE_PLAN should map to SIM");
        require(service.determineRequiredInventoryType("MOBILE_PLAN", "eSIM Plan", "ESIM_ACTIVATION") == InventoryItemType.ESIM, "eSIM should map to ESIM");
        require(service.determineRequiredInventoryType("BROADBAND", "Fiber 500", "BROADBAND") == InventoryItemType.ONT, "BROADBAND should map to ONT");
        require(service.determineRequiredInventoryType("ENTERPRISE", "VPN Core", "NEW_CONNECTION") == InventoryItemType.NETWORK_DEVICE, "ENTERPRISE should map to NETWORK_DEVICE");
    }

    private static void testInventoryReservationSuccess() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();

        InventoryService service = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);

        // Add available inventory
        InventoryItem sim = new InventoryItem("SIM-201", InventoryItemType.SIM, "Mumbai Warehouse");
        sim.setStatus(InventoryStatus.AVAILABLE);
        inventoryDao.save(sim);

        // Setup product & order
        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        long prodId = productDao.save(p);

        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(1L);
        order.setOrderType(OrderType.NEW_CONNECTION);
        order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
        long orderId = orderDao.save(order);

        OrderItem item = new OrderItem(prodId, 1, new BigDecimal("500.00"));
        item.setOrderId(orderId);
        itemDao.save(item);

        // Reserve inventory -> OK
        service.reserveInventoryForOrder(orderId);
        require(inventoryDao.findById(sim.getInventoryId()).get().getStatus() == InventoryStatus.RESERVED, "Sim status should be RESERVED");
        require(Long.valueOf(orderId).equals(inventoryDao.findById(sim.getInventoryId()).get().getAssignedOrderId()), "Assigned order ID mismatch");
    }

    private static void testInventoryReservationUnavailable() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();

        InventoryService service = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);

        // Setup product & order with 0 available SIM items
        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        long prodId = productDao.save(p);

        TelecomOrder order = new TelecomOrder();
        order.setCustomerId(1L);
        order.setOrderType(OrderType.NEW_CONNECTION);
        order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
        long orderId = orderDao.save(order);

        OrderItem item = new OrderItem(prodId, 1, new BigDecimal("500.00"));
        item.setOrderId(orderId);
        itemDao.save(item);

        try {
            service.reserveInventoryForOrder(orderId);
            require(false, "Reserving out-of-stock inventory should throw InventoryUnavailableException");
        } catch (InventoryUnavailableException ex) {
            require(ex.getMessage().contains("Insufficient inventory"), "Expected insufficient inventory message");
        }
    }

    private static void testConcurrentInventoryReservation() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();

        InventoryService service = new InventoryServiceImpl(inventoryDao, orderDao, itemDao, productDao);

        // Save 5 available SIM items
        for (int i = 1; i <= 5; i++) {
            InventoryItem sim = new InventoryItem("SIM-30" + i, InventoryItemType.SIM, "Mumbai Warehouse");
            sim.setStatus(InventoryStatus.AVAILABLE);
            inventoryDao.save(sim);
        }

        TelecomProduct p = new TelecomProduct("PROD-1", "5G Mobile", "MOBILE_PLAN", new BigDecimal("500.00"));
        long prodId = productDao.save(p);

        // Create 10 orders competing for 5 SIMs
        int numThreads = 10;
        List<Long> orderIds = new ArrayList<>();
        for (int i = 1; i <= numThreads; i++) {
            TelecomOrder order = new TelecomOrder();
            order.setCustomerId((long) i);
            order.setOrderType(OrderType.NEW_CONNECTION);
            order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
            long oId = orderDao.save(order);
            orderIds.add(oId);
            OrderItem oi = new OrderItem(prodId, 1, new BigDecimal("500.00"));
            oi.setOrderId(oId);
            itemDao.save(oi);
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (Long oId : orderIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.reserveInventoryForOrder(oId);
                    successCount.incrementAndGet();
                } catch (InventoryUnavailableException ex) {
                    failCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        require(successCount.get() == 5, "Exactly 5 reservations should succeed (got " + successCount.get() + ")");
        require(failCount.get() == 5, "Exactly 5 reservations should fail due to stock depletion (got " + failCount.get() + ")");
    }

    // Mock DAO Implementations
    private static class MockInventoryItemDao implements InventoryItemDao {
        private final Map<Long, InventoryItem> storage = new HashMap<>();
        private long idSeq = 1L;

        @Override public synchronized long save(InventoryItem entity) { long id = idSeq++; entity.setInventoryId(id); storage.put(id, entity); return id; }
        @Override public synchronized Optional<InventoryItem> findById(Long id) { return Optional.ofNullable(storage.get(id)); }
        @Override public synchronized Optional<InventoryItem> findByItemCode(String itemCode) { return storage.values().stream().filter(i -> i.getItemCode().equalsIgnoreCase(itemCode)).findFirst(); }
        @Override public synchronized List<InventoryItem> findByStatus(String status) { List<InventoryItem> res = new ArrayList<>(); for (InventoryItem i : storage.values()) { if (i.getStatus() != null && i.getStatus().name().equalsIgnoreCase(status)) { res.add(i); } } return res; }
        @Override public synchronized List<InventoryItem> findAll() { return new ArrayList<>(storage.values()); }
        @Override public synchronized boolean update(InventoryItem entity) { if (storage.containsKey(entity.getInventoryId())) { storage.put(entity.getInventoryId(), entity); return true; } return false; }
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
}
