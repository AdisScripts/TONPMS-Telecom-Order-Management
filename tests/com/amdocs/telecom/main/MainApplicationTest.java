package com.amdocs.telecom.main;

import com.amdocs.telecom.controller.AdminController;
import com.amdocs.telecom.controller.ReportController;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.model.ProductStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ProductService;
import com.amdocs.telecom.service.ReportService;
import com.amdocs.telecom.service.impl.ProductServiceImpl;
import com.amdocs.telecom.service.impl.ReportServiceImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

public class MainApplicationTest {

    public static void main(String[] args) {
        System.out.println("Running MainApplicationTest...");

        testApplicationLifecycle();
        testAdminProductCreationFlow();
        testAdminInventoryEditFlow();

        System.out.println("PASS: MainApplicationTest completed successfully with full CLI flow testing.");
    }

    private static void testApplicationLifecycle() {
        // Simulated user CLI flow:
        // 1. Select option 2 (Customer Registration)
        // 2. Provide registration inputs (Name, Email, Phone, Address, City, Type, Username, Password, Captcha)
        // 3. Select option 7 (Exit application)
        String inputData = "2\nTest User\ntestuser@example.com\n9876543210\n123 Telecom Street\nMetropolis\n1\ntestuser123\nPassword@123\nWRONG_CAPTCHA\n7\n";

        ByteArrayInputStream in = new ByteArrayInputStream(inputData.getBytes());
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes);

        MainApplication app = new MainApplication(in, out);
        app.run();

        String output = outBytes.toString();

        require(output.contains("TELECOM ORDER & PROVISIONING SYSTEM"), "Console missing main header.");
        require(output.contains("CUSTOMER REGISTRATION"), "Console missing registration section.");
        require(output.contains("REGISTRATION FAILED"), "Console missing registration validation execution.");
        require(output.contains("Exiting TONPMS Telecom Order System. Goodbye!"), "Console missing exit message.");
        require(!app.getSchedulerManager().isRunning(), "SchedulerManager should be stopped after application exit.");
    }

    private static void testAdminProductCreationFlow() {
        // Test AdminController product creation CLI flow ensuring contractPeriod, activationFee, and description are collected
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        ProductService productService = new ProductServiceImpl(productDao);
        AdminController adminController = new AdminController(null, productService, null, null, null, null, null, null, null);

        Set<RoleCode> roles = new HashSet<>();
        roles.add(RoleCode.ORDER_ADMINISTRATOR);
        UserSession adminSession = new UserSession(1L, "admin", null, null, roles);

        String cliInput = "2\ny\nP999\nSuper 5G Plan\nMOBILE_PLAN\nHigh speed mobile data\n499.00\n50.00\n12\n11\n";
        ByteArrayInputStream in = new ByteArrayInputStream(cliInput.getBytes());
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes);

        adminController.runMenu(new Scanner(in), out, adminSession);

        String output = outBytes.toString();
        require(output.contains("SUCCESS: New product created."), "Product creation output missing success message.");

        TelecomProduct created = productService.getProductByCode("P999");
        require(created.getContractPeriod() != null && created.getContractPeriod() == 12, "Contract period must be 12.");
        require(created.getActivationFee() != null && created.getActivationFee().compareTo(new BigDecimal("50.00")) == 0, "Activation fee mismatch.");
        require("High speed mobile data".equals(created.getDescription()), "Description mismatch.");
    }

    private static void testAdminInventoryEditFlow() {
        MockInventoryItemDao inventoryDao = new MockInventoryItemDao();
        com.amdocs.telecom.service.InventoryService inventoryService = new com.amdocs.telecom.service.impl.InventoryServiceImpl(inventoryDao, new MockTelecomOrderDao(), new MockOrderItemDao(), new MockTelecomProductDao());
        AdminController adminController = new AdminController(null, null, null, inventoryService, null, null, null, null, null);

        // Seed inventory items 1 and 5
        com.amdocs.telecom.model.InventoryItem item1 = new com.amdocs.telecom.model.InventoryItem("SIM-001", com.amdocs.telecom.model.InventoryItemType.SIM, "");
        item1.setInventoryId(1L);
        inventoryDao.saveDirect(item1);

        com.amdocs.telecom.model.InventoryItem item5 = new com.amdocs.telecom.model.InventoryItem("ESIM-002", com.amdocs.telecom.model.InventoryItemType.SIM, "Delhi");
        item5.setInventoryId(5L);
        inventoryDao.saveDirect(item5);

        Set<RoleCode> roles = new HashSet<>();
        roles.add(RoleCode.INVENTORY_ADMINISTRATOR);
        UserSession adminSession = new UserSession(1L, "admin", null, null, roles);

        // Option 4 (Inventory), Choice 2 (Edit), ID 1, Code SIM-001, Warehouse Mumbai, Type 1 (SIM), then exit 11
        String cliInput1 = "4\n2\n1\nSIM-001\nMumbai\n1\n11\n";
        ByteArrayInputStream in1 = new ByteArrayInputStream(cliInput1.getBytes());
        ByteArrayOutputStream outBytes1 = new ByteArrayOutputStream();
        adminController.runMenu(new Scanner(in1), new PrintStream(outBytes1), adminSession);
        require(outBytes1.toString().contains("SUCCESS: Inventory item #1 updated successfully."), "Inventory 1 update output mismatch");
        require("Mumbai".equals(inventoryService.getInventoryItemById(1L).getWarehouse()), "Inventory 1 warehouse must be Mumbai");

        // Option 4 (Inventory), Choice 2 (Edit), ID 5, Code ESIM-002, Warehouse Delhi, Type 2 (ESIM), then exit 11
        UserSession adminSession2 = new UserSession(2L, "admin2", null, null, roles);
        String cliInput5 = "4\n2\n5\nESIM-002\nDelhi\n2\n11\n";
        ByteArrayInputStream in5 = new ByteArrayInputStream(cliInput5.getBytes());
        ByteArrayOutputStream outBytes5 = new ByteArrayOutputStream();
        adminController.runMenu(new Scanner(in5), new PrintStream(outBytes5), adminSession2);
        require(outBytes5.toString().contains("SUCCESS: Inventory item #5 updated successfully."), "Inventory 5 update output mismatch");
        require(inventoryService.getInventoryItemById(5L).getItemType() == com.amdocs.telecom.model.InventoryItemType.ESIM, "Inventory 5 type must be ESIM");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }

    private static class MockTelecomProductDao implements TelecomProductDao {
        private final Map<Long, TelecomProduct> map = new HashMap<>();
        private long idSequence = 1;

        @Override
        public long save(TelecomProduct p) {
            long id = idSequence++;
            p.setProductId(id);
            map.put(id, p);
            return id;
        }

        @Override
        public Optional<TelecomProduct> findById(Long id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public Optional<TelecomProduct> findByProductCode(String productCode) {
            return map.values().stream().filter(p -> productCode.equals(p.getProductCode())).findFirst();
        }

        @Override
        public List<TelecomProduct> findActiveProducts() {
            List<TelecomProduct> active = new ArrayList<>();
            for (TelecomProduct p : map.values()) {
                if (p.getStatus() == ProductStatus.ACTIVE) {
                    active.add(p);
                }
            }
            return active;
        }

        @Override
        public List<TelecomProduct> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public boolean update(TelecomProduct p) {
            if (map.containsKey(p.getProductId())) {
                map.put(p.getProductId(), p);
                return true;
            }
            return false;
        }

        @Override
        public boolean delete(Long id) {
            return map.remove(id) != null;
        }
    }

    private static class MockInventoryItemDao implements com.amdocs.telecom.dao.InventoryItemDao {
        private final Map<Long, com.amdocs.telecom.model.InventoryItem> map = new HashMap<>();
        private long idSequence = 1;

        public void saveDirect(com.amdocs.telecom.model.InventoryItem item) {
            map.put(item.getInventoryId(), item);
        }

        @Override
        public long save(com.amdocs.telecom.model.InventoryItem item) {
            if (item.getInventoryId() == null) {
                item.setInventoryId(idSequence++);
            }
            map.put(item.getInventoryId(), item);
            return item.getInventoryId();
        }

        @Override
        public Optional<com.amdocs.telecom.model.InventoryItem> findById(Long id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public Optional<com.amdocs.telecom.model.InventoryItem> findByItemCode(String itemCode) {
            return map.values().stream().filter(i -> itemCode.equalsIgnoreCase(i.getItemCode())).findFirst();
        }

        @Override
        public List<com.amdocs.telecom.model.InventoryItem> findByStatus(String status) {
            List<com.amdocs.telecom.model.InventoryItem> list = new ArrayList<>();
            for (com.amdocs.telecom.model.InventoryItem i : map.values()) {
                if (i.getStatus() != null && i.getStatus().name().equalsIgnoreCase(status)) list.add(i);
            }
            return list;
        }

        @Override
        public List<com.amdocs.telecom.model.InventoryItem> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public boolean update(com.amdocs.telecom.model.InventoryItem item) {
            if (map.containsKey(item.getInventoryId())) {
                map.put(item.getInventoryId(), item);
                return true;
            }
            return false;
        }

        @Override
        public boolean delete(Long id) {
            return map.remove(id) != null;
        }
    }

    private static class MockTelecomOrderDao implements com.amdocs.telecom.dao.TelecomOrderDao {
        private final Map<Long, com.amdocs.telecom.model.TelecomOrder> map = new HashMap<>();
        @Override public long save(com.amdocs.telecom.model.TelecomOrder o) { map.put(o.getOrderId(), o); return o.getOrderId() != null ? o.getOrderId() : 1L; }
        @Override public Optional<com.amdocs.telecom.model.TelecomOrder> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public Optional<com.amdocs.telecom.model.TelecomOrder> findByOrderNumber(String num) { return Optional.empty(); }
        @Override public List<com.amdocs.telecom.model.TelecomOrder> findByCustomerId(Long cid) { return new ArrayList<>(); }
        @Override public List<com.amdocs.telecom.model.TelecomOrder> findAll() { return new ArrayList<>(map.values()); }
        @Override public boolean update(com.amdocs.telecom.model.TelecomOrder o) { return true; }
        @Override public boolean delete(Long id) { return true; }
    }

    private static class MockOrderItemDao implements com.amdocs.telecom.dao.OrderItemDao {
        private final Map<Long, com.amdocs.telecom.model.OrderItem> map = new HashMap<>();
        @Override public long save(com.amdocs.telecom.model.OrderItem item) { return 1L; }
        @Override public Optional<com.amdocs.telecom.model.OrderItem> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public List<com.amdocs.telecom.model.OrderItem> findByOrderId(Long oid) { return new ArrayList<>(); }
        @Override public int[] saveBatch(List<com.amdocs.telecom.model.OrderItem> items) { return new int[]{items.size()}; }
        @Override public List<com.amdocs.telecom.model.OrderItem> findAll() { return new ArrayList<>(map.values()); }
        @Override public boolean update(com.amdocs.telecom.model.OrderItem item) { return true; }
        @Override public boolean delete(Long id) { return true; }
    }
}
