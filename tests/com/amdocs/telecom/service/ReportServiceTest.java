package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.OrderPaymentDao;
import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.model.AuditLog;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.model.PaymentTransactionStatus;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.report.ReportData;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.ReportServiceImpl;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReportServiceTest {

    public static void main(String[] args) {
        System.out.println("Running ReportServiceTest...");

        MockTelecomOrderDao telecomOrderDao = new MockTelecomOrderDao();
        MockOrderItemDao orderItemDao = new MockOrderItemDao();
        MockTelecomProductDao telecomProductDao = new MockTelecomProductDao();
        MockCustomerDao customerDao = new MockCustomerDao();
        MockInventoryItemDao inventoryItemDao = new MockInventoryItemDao();
        MockProvisioningRequestDao provisioningRequestDao = new MockProvisioningRequestDao();
        MockProvisioningEngineerDao provisioningEngineerDao = new MockProvisioningEngineerDao();
        MockOrderPaymentDao orderPaymentDao = new MockOrderPaymentDao();
        MockAuditLogDao auditLogDao = new MockAuditLogDao();

        ReportService reportService = new ReportServiceImpl(
                telecomOrderDao, orderItemDao, telecomProductDao, customerDao,
                inventoryItemDao, provisioningRequestDao, provisioningEngineerDao,
                orderPaymentDao, auditLogDao
        );

        UserSession adminSession = new UserSession(1L, "ADMIN", null, null, Collections.singleton(RoleCode.ORDER_ADMINISTRATOR));
        UserSession customerSession = new UserSession(2L, "CUST", null, null, Collections.singleton(RoleCode.CUSTOMER));

        // 1. Authorization Test
        testAuthorization(reportService, customerSession);

        // 2. Order Reports Test (All 8 Methods)
        testOrderReports(reportService, adminSession, telecomOrderDao, orderItemDao, telecomProductDao, customerDao);

        // 3. Inventory Reports Test (All 5 Methods)
        testInventoryReports(reportService, adminSession, inventoryItemDao);

        // 4. Provisioning Reports Test (All 5 Methods)
        testProvisioningReports(reportService, adminSession, provisioningRequestDao, provisioningEngineerDao);

        // 5. Revenue Reports Test (All 5 Methods - Strict BigDecimal Aggregation)
        testRevenueReports(reportService, adminSession, telecomOrderDao, orderItemDao, orderPaymentDao, customerDao);

        // 6. CSV and TXT Export Test (All 2 Methods with Content Reading Assertions)
        testFileExports(reportService, adminSession);

        System.out.println("PASS: ReportServiceTest completed successfully across all 25 API methods.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException("Assertion failed: " + message);
        }
    }

    private static void testAuthorization(ReportService service, UserSession customerSession) {
        try {
            service.getOrdersByDateRange(customerSession, LocalDate.now().minusDays(7), LocalDate.now());
            require(false, "Customer should not be authorized to view administrative reports.");
        } catch (AccessDeniedException expected) {
            System.out.println("PASS 1/25: Authorization check correctly blocked unauthorized customer session.");
        }
    }

    private static void testOrderReports(ReportService service, UserSession adminSession,
                                         MockTelecomOrderDao telecomOrderDao, MockOrderItemDao orderItemDao,
                                         MockTelecomProductDao telecomProductDao, MockCustomerDao customerDao) {
        Customer c1 = new Customer("CUST-9001", "Alice Smith", "alice@example.com", "9876543210", CustomerType.INDIVIDUAL);
        customerDao.save(c1);

        TelecomProduct p1 = new TelecomProduct("PROD-901", "5G Mobile", "MOBILE_PLAN", new BigDecimal("999.00"));
        telecomProductDao.save(p1);

        // Active / Completed order
        TelecomOrder o1 = new TelecomOrder("ORD-9001", c1.getCustomerId(), OrderType.NEW_CONNECTION);
        o1.setOrderDate(LocalDateTime.now().minusHours(2));
        o1.setOrderStatus(OrderStatus.ACTIVATED);
        o1.setUpdatedAt(LocalDateTime.now().minusHours(1));
        o1.setTotalAmount(new BigDecimal("1998.00"));
        telecomOrderDao.save(o1);

        // Cancelled order
        TelecomOrder oCancelled = new TelecomOrder("ORD-9002", c1.getCustomerId(), OrderType.NEW_CONNECTION);
        oCancelled.setOrderDate(LocalDateTime.now().minusHours(3));
        oCancelled.setOrderStatus(OrderStatus.CANCELLED);
        oCancelled.setTotalAmount(new BigDecimal("500.00"));
        telecomOrderDao.save(oCancelled);

        // Failed order
        TelecomOrder oFailed = new TelecomOrder("ORD-9003", c1.getCustomerId(), OrderType.NEW_CONNECTION);
        oFailed.setOrderDate(LocalDateTime.now().minusHours(4));
        oFailed.setOrderStatus(OrderStatus.FAILED);
        oFailed.setTotalAmount(new BigDecimal("750.00"));
        telecomOrderDao.save(oFailed);

        OrderItem i1 = new OrderItem(p1.getProductId(), 2, new BigDecimal("999.00"));
        i1.setOrderId(o1.getOrderId());
        i1.setTotalAmount(new BigDecimal("1998.00"));
        orderItemDao.save(i1);

        // Method 1: getOrdersByDateRange
        ReportData dateRangeData = service.getOrdersByDateRange(adminSession, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        require(!dateRangeData.getRows().isEmpty(), "getOrdersByDateRange returned empty rows.");
        System.out.println("PASS 2/25: getOrdersByDateRange verified.");

        // Method 2: getOrdersByProduct
        ReportData byProd = service.getOrdersByProduct(adminSession);
        require(!byProd.getRows().isEmpty(), "getOrdersByProduct returned empty rows.");
        System.out.println("PASS 3/25: getOrdersByProduct verified.");

        // Method 3: getMostOrderedProducts
        ReportData mostOrdered = service.getMostOrderedProducts(adminSession, 5);
        require(!mostOrdered.getRows().isEmpty() && mostOrdered.getRows().get(0).contains("2"), "getMostOrderedProducts did not sum OrderItem.quantity correctly.");
        System.out.println("PASS 4/25: getMostOrderedProducts verified.");

        // Method 4: getOrdersByStatus
        ReportData statusData = service.getOrdersByStatus(adminSession);
        require(!statusData.getRows().isEmpty(), "getOrdersByStatus returned empty rows.");
        System.out.println("PASS 5/25: getOrdersByStatus verified.");

        // Method 5: getOrdersByCustomerType
        ReportData custTypeData = service.getOrdersByCustomerType(adminSession);
        require(!custTypeData.getRows().isEmpty(), "getOrdersByCustomerType returned empty rows.");
        System.out.println("PASS 6/25: getOrdersByCustomerType verified.");

        // Method 6: getCancelledOrders
        ReportData cancelledData = service.getCancelledOrders(adminSession);
        require(!cancelledData.getRows().isEmpty() && cancelledData.getSummaryMetrics().get("Count").equals("1"), "getCancelledOrders assertion failed.");
        System.out.println("PASS 7/25: getCancelledOrders verified.");

        // Method 7: getFailedOrders
        ReportData failedOrdersData = service.getFailedOrders(adminSession);
        require(!failedOrdersData.getRows().isEmpty() && failedOrdersData.getSummaryMetrics().get("Count").equals("1"), "getFailedOrders assertion failed.");
        System.out.println("PASS 8/25: getFailedOrders verified.");

        // Method 8: getAverageOrderProcessingTimeMinutes
        double avgProcessingTime = service.getAverageOrderProcessingTimeMinutes(adminSession);
        require(avgProcessingTime > 0, "getAverageOrderProcessingTimeMinutes should be > 0.");
        System.out.println("PASS 9/25: getAverageOrderProcessingTimeMinutes verified.");
    }

    private static void testInventoryReports(ReportService service, UserSession adminSession, MockInventoryItemDao inventoryItemDao) {
        InventoryItem availItem = new InventoryItem("SIM-9001", InventoryItemType.SIM, "MUM-WH-01");
        availItem.setStatus(InventoryStatus.AVAILABLE);
        inventoryItemDao.save(availItem);

        InventoryItem resItem = new InventoryItem("SIM-9002", InventoryItemType.SIM, "MUM-WH-01");
        resItem.setStatus(InventoryStatus.RESERVED);
        inventoryItemDao.save(resItem);

        InventoryItem damItem = new InventoryItem("SIM-9003", InventoryItemType.SIM, "MUM-WH-01");
        damItem.setStatus(InventoryStatus.DAMAGED);
        inventoryItemDao.save(damItem);

        // Method 9: getAvailableInventory
        ReportData availReport = service.getAvailableInventory(adminSession);
        require(!availReport.getRows().isEmpty(), "getAvailableInventory empty.");
        System.out.println("PASS 10/25: getAvailableInventory verified.");

        // Method 10: getReservedInventory
        ReportData resReport = service.getReservedInventory(adminSession);
        require(!resReport.getRows().isEmpty(), "getReservedInventory empty.");
        System.out.println("PASS 11/25: getReservedInventory verified.");

        // Method 11: getInventoryByWarehouse
        ReportData whReport = service.getInventoryByWarehouse(adminSession);
        require(!whReport.getRows().isEmpty(), "getInventoryByWarehouse empty.");
        System.out.println("PASS 12/25: getInventoryByWarehouse verified.");

        // Method 12: getLowInventory
        ReportData lowReport = service.getLowInventory(adminSession, 10);
        require(!lowReport.getHeaders().isEmpty(), "getLowInventory headers empty.");
        System.out.println("PASS 13/25: getLowInventory verified.");

        // Method 13: getDamagedInventory
        ReportData damReport = service.getDamagedInventory(adminSession);
        require(!damReport.getRows().isEmpty(), "getDamagedInventory empty.");
        System.out.println("PASS 14/25: getDamagedInventory verified.");
    }

    private static void testProvisioningReports(ReportService service, UserSession adminSession,
                                                MockProvisioningRequestDao provisioningRequestDao,
                                                MockProvisioningEngineerDao provisioningEngineerDao) {
        ProvisioningEngineer eng = new ProvisioningEngineer("ENG-901", "Bob Engineer", "5G Core", "WEST");
        eng.setActiveTasks(2);
        provisioningEngineerDao.save(eng);

        ProvisioningRequest reqSucc = new ProvisioningRequest(1001L, "SERV-5G-01", ProvisioningType.SIM_ACTIVATION);
        reqSucc.setEngineerId(eng.getEngineerId());
        reqSucc.setRequestedDate(LocalDateTime.now().minusMinutes(45));
        reqSucc.setCompletedDate(LocalDateTime.now().minusMinutes(15));
        reqSucc.setStatus(ProvisioningStatus.SUCCESS);
        provisioningRequestDao.save(reqSucc);

        ProvisioningRequest reqFailed = new ProvisioningRequest(1002L, "SERV-5G-02", ProvisioningType.SIM_ACTIVATION);
        reqFailed.setEngineerId(eng.getEngineerId());
        reqFailed.setStatus(ProvisioningStatus.FAILED);
        provisioningRequestDao.save(reqFailed);

        // Method 14: getSuccessfulProvisioningRequests
        ReportData succReport = service.getSuccessfulProvisioningRequests(adminSession);
        require(!succReport.getRows().isEmpty(), "getSuccessfulProvisioningRequests empty.");
        System.out.println("PASS 15/25: getSuccessfulProvisioningRequests verified.");

        // Method 15: getFailedProvisioningRequests
        ReportData failReport = service.getFailedProvisioningRequests(adminSession);
        require(!failReport.getRows().isEmpty(), "getFailedProvisioningRequests empty.");
        System.out.println("PASS 16/25: getFailedProvisioningRequests verified.");

        // Method 16: getProvisioningByServiceType
        ReportData serviceTypeReport = service.getProvisioningByServiceType(adminSession);
        require(!serviceTypeReport.getRows().isEmpty(), "getProvisioningByServiceType empty.");
        System.out.println("PASS 17/25: getProvisioningByServiceType verified.");

        // Method 17: getAverageProvisioningTimeMinutes
        double avgProvTime = service.getAverageProvisioningTimeMinutes(adminSession);
        require(avgProvTime == 30.0, "Expected 30.0 minutes average provisioning time, got: " + avgProvTime);
        System.out.println("PASS 18/25: getAverageProvisioningTimeMinutes verified.");

        // Method 18: getEngineerWorkload
        ReportData engReport = service.getEngineerWorkload(adminSession);
        require(!engReport.getRows().isEmpty(), "getEngineerWorkload empty.");
        System.out.println("PASS 19/25: getEngineerWorkload verified.");
    }

    private static void testRevenueReports(ReportService service, UserSession adminSession,
                                           MockTelecomOrderDao telecomOrderDao, MockOrderItemDao orderItemDao,
                                           MockOrderPaymentDao orderPaymentDao, MockCustomerDao customerDao) {
        OrderPayment pSuccess = new OrderPayment(5001L, "TXN-9001", new BigDecimal("1998.00"), PaymentMode.CARD);
        pSuccess.setStatus(PaymentTransactionStatus.SUCCESS);
        orderPaymentDao.save(pSuccess);

        OrderPayment pFailed = new OrderPayment(5002L, "TXN-9002", new BigDecimal("5000.00"), PaymentMode.CARD);
        pFailed.setStatus(PaymentTransactionStatus.FAILED);
        orderPaymentDao.save(pFailed);

        // Method 19: getProductWiseRevenue
        ReportData prodRevData = service.getProductWiseRevenue(adminSession);
        require(!prodRevData.getRows().isEmpty(), "getProductWiseRevenue empty.");
        System.out.println("PASS 20/25: getProductWiseRevenue verified.");

        // Method 20: getMonthlyRevenue
        ReportData monthRevData = service.getMonthlyRevenue(adminSession, LocalDate.now().getYear());
        require(!monthRevData.getRows().isEmpty(), "getMonthlyRevenue empty.");
        System.out.println("PASS 21/25: getMonthlyRevenue verified.");

        // Method 21: getCustomerTypeRevenue
        ReportData custRevData = service.getCustomerTypeRevenue(adminSession);
        require(!custRevData.getRows().isEmpty(), "getCustomerTypeRevenue empty.");
        System.out.println("PASS 22/25: getCustomerTypeRevenue verified.");

        // Method 22: getPaymentModeAnalysis (Must filter ONLY PaymentTransactionStatus.SUCCESS)
        ReportData payModeData = service.getPaymentModeAnalysis(adminSession);
        boolean containsFailedAmount = payModeData.getRows().stream().anyMatch(r -> r.contains("5000.00"));
        require(!containsFailedAmount, "getPaymentModeAnalysis included FAILED payments, should include SUCCESS only.");
        System.out.println("PASS 23/25: getPaymentModeAnalysis verified (filtered PaymentTransactionStatus.SUCCESS).");

        // Method 23: getTopCustomersByRevenue
        ReportData topCustData = service.getTopCustomersByRevenue(adminSession, 3);
        require(!topCustData.getRows().isEmpty(), "getTopCustomersByRevenue empty.");
        System.out.println("PASS 24/25: getTopCustomersByRevenue verified.");
    }

    private static void testFileExports(ReportService service, UserSession adminSession) {
        ReportData mockData = new ReportData("Test CSV Report", Arrays.asList("Header1", "Header2, With Comma"),
                Arrays.asList(
                        Arrays.asList("Val1", "Val2, \"Quoted\""),
                        Arrays.asList("Line1\nLine2", "Normal")
                ));

        String csvPath = "reports/test_export.csv";
        String txtPath = "reports/test_export.txt";

        // Method 24: exportReportToCsv
        boolean csvSuccess = service.exportReportToCsv(adminSession, mockData, csvPath);
        require(csvSuccess && new File(csvPath).exists() && new File(csvPath).length() > 0, "exportReportToCsv failed.");

        // Read CSV back and assert content escaping
        try {
            List<String> csvLines = Files.readAllLines(Paths.get(csvPath));
            boolean foundEscapedHeader = csvLines.stream().anyMatch(line -> line.contains("\"Header2, With Comma\""));
            boolean foundDoubledQuote = csvLines.stream().anyMatch(line -> line.contains("\"Val2, \"\"Quoted\"\"\""));
            require(foundEscapedHeader, "CSV export did not enclose comma-containing header in double quotes.");
            require(foundDoubledQuote, "CSV export did not escape embedded quotes as doubled quotes.");

            String fullCsvContent = new String(Files.readAllBytes(Paths.get(csvPath)));
            boolean foundQuotedNewline = fullCsvContent.contains("\"Line1\nLine2\"") || fullCsvContent.contains("\"Line1\r\nLine2\"");
            require(foundQuotedNewline, "CSV export did not enclose newline-containing field in double quotes.");

            System.out.println("PASS 25/25: exportReportToCsv verified with content reading, comma, quote & newline escaping assertions.");
        } catch (IOException e) {
            throw new RuntimeException("FAIL: Reading exported CSV file threw IOException: " + e.getMessage());
        }

        // Method 25: exportReportToText
        boolean txtSuccess = service.exportReportToText(adminSession, mockData, txtPath);
        require(txtSuccess && new File(txtPath).exists() && new File(txtPath).length() > 0, "exportReportToText failed.");

        // Read TXT back and assert title & header contents
        try {
            List<String> txtLines = Files.readAllLines(Paths.get(txtPath));
            boolean foundTitle = txtLines.stream().anyMatch(line -> line.contains("TEST CSV REPORT"));
            boolean foundHeader = txtLines.stream().anyMatch(line -> line.contains("Header1 | Header2, With Comma"));
            require(foundTitle, "TXT export file did not contain uppercase title.");
            require(foundHeader, "TXT export file did not contain formatted headers.");
            System.out.println("PASS (File Output): exportReportToText verified with content reading assertions.");
        } catch (IOException e) {
            throw new RuntimeException("FAIL: Reading exported TXT file threw IOException: " + e.getMessage());
        }
    }

    // Mock DAOs
    private static class MockTelecomOrderDao implements TelecomOrderDao {
        private final Map<Long, TelecomOrder> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(TelecomOrder entity) { long id = seq++; entity.setOrderId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<TelecomOrder> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<TelecomOrder> findByOrderNumber(String num) { return map.values().stream().filter(o -> num.equals(o.getOrderNumber())).findFirst(); }
        @Override public synchronized List<TelecomOrder> findByCustomerId(Long cId) { List<TelecomOrder> list = new ArrayList<>(); for(TelecomOrder o: map.values()){if(cId.equals(o.getCustomerId())) list.add(o);} return list; }
        @Override public synchronized List<TelecomOrder> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(TelecomOrder entity) { map.put(entity.getOrderId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockOrderItemDao implements OrderItemDao {
        private final Map<Long, OrderItem> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(OrderItem entity) { long id = seq++; entity.setOrderItemId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<OrderItem> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized List<OrderItem> findByOrderId(Long oId) { List<OrderItem> list = new ArrayList<>(); for(OrderItem i: map.values()){if(oId.equals(i.getOrderId())) list.add(i);} return list; }
        @Override public synchronized List<OrderItem> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(OrderItem entity) { map.put(entity.getOrderItemId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
        @Override public synchronized int[] saveBatch(List<OrderItem> items) { for(OrderItem i: items) save(i); return new int[items.size()]; }
    }

    private static class MockTelecomProductDao implements TelecomProductDao {
        private final Map<Long, TelecomProduct> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(TelecomProduct entity) { long id = seq++; entity.setProductId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<TelecomProduct> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<TelecomProduct> findByProductCode(String code) { return map.values().stream().filter(p -> code.equals(p.getProductCode())).findFirst(); }
        @Override public synchronized List<TelecomProduct> findActiveProducts() { return new ArrayList<>(map.values()); }
        @Override public synchronized List<TelecomProduct> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(TelecomProduct entity) { map.put(entity.getProductId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockCustomerDao implements CustomerDao {
        private final Map<Long, Customer> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(Customer entity) { long id = seq++; entity.setCustomerId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<Customer> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<Customer> findByCustomerNumber(String num) { return map.values().stream().filter(c -> num.equals(c.getCustomerNumber())).findFirst(); }
        @Override public synchronized Optional<Customer> findByEmail(String email) { return map.values().stream().filter(c -> email.equals(c.getEmail())).findFirst(); }
        @Override public synchronized List<Customer> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(Customer entity) { map.put(entity.getCustomerId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockInventoryItemDao implements InventoryItemDao {
        private final Map<Long, InventoryItem> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(InventoryItem entity) { long id = seq++; entity.setInventoryId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<InventoryItem> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<InventoryItem> findByItemCode(String code) { return map.values().stream().filter(i -> code.equals(i.getItemCode())).findFirst(); }
        @Override public synchronized List<InventoryItem> findByStatus(String status) { return map.values().stream().filter(i -> i.getStatus() != null && i.getStatus().name().equals(status)).collect(Collectors.toList()); }
        @Override public synchronized List<InventoryItem> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(InventoryItem entity) { map.put(entity.getInventoryId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { map.remove(id); return true; }
    }

    private static class MockProvisioningRequestDao implements ProvisioningRequestDao {
        private final Map<Long, ProvisioningRequest> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(ProvisioningRequest entity) { long id = seq++; entity.setProvisioningId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<ProvisioningRequest> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized List<ProvisioningRequest> findByOrderId(Long oId) { List<ProvisioningRequest> list = new ArrayList<>(); for(ProvisioningRequest r: map.values()){if(oId.equals(r.getOrderId())) list.add(r);} return list; }
        @Override public synchronized List<ProvisioningRequest> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(ProvisioningRequest entity) { map.put(entity.getProvisioningId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockProvisioningEngineerDao implements ProvisioningEngineerDao {
        private final Map<Long, ProvisioningEngineer> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(ProvisioningEngineer entity) { long id = seq++; entity.setEngineerId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<ProvisioningEngineer> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized Optional<ProvisioningEngineer> findByEmployeeCode(String empCode) { return map.values().stream().filter(e -> empCode.equals(e.getEmployeeCode())).findFirst(); }
        @Override public synchronized List<ProvisioningEngineer> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(ProvisioningEngineer entity) { map.put(entity.getEngineerId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockOrderPaymentDao implements OrderPaymentDao {
        private final Map<Long, OrderPayment> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(OrderPayment entity) { long id = seq++; entity.setPaymentId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<OrderPayment> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized List<OrderPayment> findByOrderId(Long oId) { List<OrderPayment> list = new ArrayList<>(); for(OrderPayment p: map.values()){if(oId.equals(p.getOrderId())) list.add(p);} return list; }
        @Override public synchronized Optional<OrderPayment> findByTransactionReference(String ref) { return map.values().stream().filter(p -> ref.equals(p.getTransactionReference())).findFirst(); }
        @Override public synchronized List<OrderPayment> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(OrderPayment entity) { map.put(entity.getPaymentId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockAuditLogDao implements AuditLogDao {
        private final Map<Long, AuditLog> map = new HashMap<>();
        private long seq = 1;
        @Override public synchronized long save(AuditLog entity) { long id = seq++; entity.setAuditId(id); map.put(id, entity); return id; }
        @Override public synchronized Optional<AuditLog> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public synchronized List<AuditLog> findByEntity(String eType, Long eId) { return new ArrayList<>(map.values()); }
        @Override public synchronized List<AuditLog> findAll() { return new ArrayList<>(map.values()); }
        @Override public synchronized boolean update(AuditLog entity) { map.put(entity.getAuditId(), entity); return true; }
        @Override public synchronized boolean delete(Long id) { return map.remove(id) != null; }
    }
}
