package com.amdocs.telecom.service.impl;

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
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.OrderStatus;
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
import com.amdocs.telecom.service.ReportService;
import com.amdocs.telecom.util.DatabaseConnection;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReportServiceImpl implements ReportService {

    private final TelecomOrderDao telecomOrderDao;
    private final OrderItemDao orderItemDao;
    private final TelecomProductDao telecomProductDao;
    private final CustomerDao customerDao;
    private final InventoryItemDao inventoryItemDao;
    private final ProvisioningRequestDao provisioningRequestDao;
    private final ProvisioningEngineerDao provisioningEngineerDao;
    private final OrderPaymentDao orderPaymentDao;
    private final AuditLogDao auditLogDao;

    public ReportServiceImpl(TelecomOrderDao telecomOrderDao, OrderItemDao orderItemDao,
                             TelecomProductDao telecomProductDao, CustomerDao customerDao,
                             InventoryItemDao inventoryItemDao, ProvisioningRequestDao provisioningRequestDao,
                             ProvisioningEngineerDao provisioningEngineerDao, OrderPaymentDao orderPaymentDao,
                             AuditLogDao auditLogDao) {
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.telecomProductDao = Objects.requireNonNull(telecomProductDao, "telecomProductDao must not be null");
        this.customerDao = Objects.requireNonNull(customerDao, "customerDao must not be null");
        this.inventoryItemDao = Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        this.provisioningRequestDao = Objects.requireNonNull(provisioningRequestDao, "provisioningRequestDao must not be null");
        this.provisioningEngineerDao = Objects.requireNonNull(provisioningEngineerDao, "provisioningEngineerDao must not be null");
        this.orderPaymentDao = Objects.requireNonNull(orderPaymentDao, "orderPaymentDao must not be null");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao must not be null");
    }

    private void checkAuthorization(UserSession session) {
        if (session == null || (!session.hasRole(RoleCode.ORDER_ADMINISTRATOR) && !session.hasRole(RoleCode.INVENTORY_ADMINISTRATOR))) {
            throw new AccessDeniedException("User is not authorized to generate administrative reports");
        }
    }

    private void bindThreadConnection() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            if (conn != null) {
                DatabaseConnection.setThreadConnection(conn);
            }
        } catch (Exception ignored) { }
    }

    private void clearThreadConnection() {
        DatabaseConnection.clearThreadConnection();
    }

    // ==========================================
    // ORDER REPORTS
    // ==========================================

    @Override
    public ReportData getOrdersByDateRange(UserSession session, LocalDate startDate, LocalDate endDate) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            List<TelecomOrder> filtered = orders.stream()
                    .filter(o -> o.getOrderDate() != null)
                    .filter(o -> {
                        LocalDate d = o.getOrderDate().toLocalDate();
                        return (startDate == null || !d.isBefore(startDate)) && (endDate == null || !d.isAfter(endDate));
                    })
                    .sorted(Comparator.comparing(TelecomOrder::getOrderDate).reversed())
                    .collect(Collectors.toList());

            List<String> headers = Arrays.asList("Order ID", "Order Number", "Customer ID", "Order Date", "Order Type", "Order Status", "Total Amount");
            List<List<String>> rows = new ArrayList<>();
            for (TelecomOrder o : filtered) {
                rows.add(Arrays.asList(
                        String.valueOf(o.getOrderId()),
                        o.getOrderNumber() != null ? o.getOrderNumber() : "",
                        String.valueOf(o.getCustomerId()),
                        o.getOrderDate() != null ? o.getOrderDate().toString() : "",
                        o.getOrderType() != null ? o.getOrderType().name() : "",
                        o.getOrderStatus() != null ? o.getOrderStatus().name() : "",
                        o.getTotalAmount() != null ? o.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toString() : "0.00"
                ));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Orders", String.valueOf(filtered.size()));
            return new ReportData("Orders by Date Range (" + startDate + " to " + endDate + ")", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getOrdersByProduct(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<OrderItem> items = orderItemDao.findAll();
            Map<Long, Long> productCounts = items.stream()
                    .filter(i -> i.getProductId() != null)
                    .collect(Collectors.groupingBy(OrderItem::getProductId, Collectors.counting()));

            List<String> headers = Arrays.asList("Product ID", "Product Code", "Product Name", "Order Count");
            List<List<String>> rows = new ArrayList<>();

            productCounts.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .forEach(e -> {
                        Long prodId = e.getKey();
                        Long count = e.getValue();
                        String code = "PROD_" + prodId;
                        String name = "Product #" + prodId;
                        Optional<TelecomProduct> prodOpt = telecomProductDao.findById(prodId);
                        if (prodOpt.isPresent()) {
                            code = prodOpt.get().getProductCode();
                            name = prodOpt.get().getProductName();
                        }
                        rows.add(Arrays.asList(String.valueOf(prodId), code, name, String.valueOf(count)));
                    });

            Map<String, String> summary = new HashMap<>();
            summary.put("Distinct Products Ordered", String.valueOf(rows.size()));
            return new ReportData("Orders by Product Breakdown", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getMostOrderedProducts(UserSession session, int limit) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<OrderItem> items = orderItemDao.findAll();
            Map<Long, Integer> productQuantities = items.stream()
                    .filter(i -> i.getProductId() != null)
                    .collect(Collectors.groupingBy(
                            OrderItem::getProductId,
                            Collectors.summingInt(i -> i.getQuantity() != null ? i.getQuantity() : 0)
                    ));

            List<String> headers = Arrays.asList("Rank", "Product ID", "Product Code", "Product Name", "Total Quantity Ordered");
            List<List<String>> rows = new ArrayList<>();

            List<Map.Entry<Long, Integer>> sorted = productQuantities.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(limit > 0 ? limit : Long.MAX_VALUE)
                    .collect(Collectors.toList());

            int rank = 1;
            for (Map.Entry<Long, Integer> entry : sorted) {
                Long prodId = entry.getKey();
                Integer totalQty = entry.getValue();
                String code = "PROD_" + prodId;
                String name = "Product #" + prodId;
                Optional<TelecomProduct> prodOpt = telecomProductDao.findById(prodId);
                if (prodOpt.isPresent()) {
                    code = prodOpt.get().getProductCode();
                    name = prodOpt.get().getProductName();
                }
                rows.add(Arrays.asList(String.valueOf(rank++), String.valueOf(prodId), code, name, String.valueOf(totalQty)));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Top N Limit", String.valueOf(limit));
            return new ReportData("Top Most Ordered Products", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getOrdersByStatus(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            Map<OrderStatus, Long> counts = orders.stream()
                    .collect(Collectors.groupingBy(TelecomOrder::getOrderStatus, Collectors.counting()));

            List<String> headers = Arrays.asList("Order Status", "Count");
            List<List<String>> rows = new ArrayList<>();
            for (OrderStatus status : OrderStatus.values()) {
                Long count = counts.getOrDefault(status, 0L);
                rows.add(Arrays.asList(status.name(), String.valueOf(count)));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total System Orders", String.valueOf(orders.size()));
            return new ReportData("Orders by Status Summary", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getOrdersByCustomerType(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            List<Customer> customers = customerDao.findAll();
            Map<Long, CustomerType> custTypeMap = customers.stream()
                    .collect(Collectors.toMap(Customer::getCustomerId, Customer::getCustomerType, (c1, c2) -> c1));

            Map<String, Long> typeCounts = orders.stream()
                    .collect(Collectors.groupingBy(
                            o -> {
                                CustomerType type = custTypeMap.get(o.getCustomerId());
                                return type != null ? type.name() : "UNKNOWN";
                            },
                            Collectors.counting()
                    ));

            List<String> headers = Arrays.asList("Customer Type", "Order Count");
            List<List<String>> rows = new ArrayList<>();
            typeCounts.forEach((type, count) -> rows.add(Arrays.asList(type, String.valueOf(count))));

            Map<String, String> summary = new HashMap<>();
            summary.put("Total System Orders", String.valueOf(orders.size()));
            return new ReportData("Orders by Customer Type Summary", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getCancelledOrders(UserSession session) {
        return getOrdersFilteredByStatus(session, OrderStatus.CANCELLED, "Cancelled Orders Report");
    }

    @Override
    public ReportData getFailedOrders(UserSession session) {
        return getOrdersFilteredByStatus(session, OrderStatus.FAILED, "Failed Orders Report");
    }

    private ReportData getOrdersFilteredByStatus(UserSession session, OrderStatus targetStatus, String title) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            List<TelecomOrder> filtered = orders.stream()
                    .filter(o -> o.getOrderStatus() == targetStatus)
                    .collect(Collectors.toList());

            List<String> headers = Arrays.asList("Order ID", "Order Number", "Customer ID", "Order Date", "Total Amount");
            List<List<String>> rows = new ArrayList<>();
            for (TelecomOrder o : filtered) {
                rows.add(Arrays.asList(
                        String.valueOf(o.getOrderId()),
                        o.getOrderNumber() != null ? o.getOrderNumber() : "",
                        String.valueOf(o.getCustomerId()),
                        o.getOrderDate() != null ? o.getOrderDate().toString() : "",
                        o.getTotalAmount() != null ? o.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toString() : "0.00"
                ));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Count", String.valueOf(filtered.size()));
            return new ReportData(title, headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public double getAverageOrderProcessingTimeMinutes(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            return orders.stream()
                    .filter(o -> o.getOrderStatus() == OrderStatus.ACTIVATED || o.getOrderStatus() == OrderStatus.COMPLETED)
                    .filter(o -> o.getOrderDate() != null)
                    .mapToLong(o -> {
                        LocalDateTime end = o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getOrderDate();
                        return Math.max(0, Duration.between(o.getOrderDate(), end).toMinutes());
                    })
                    .average()
                    .orElse(0.0);
        } finally {
            clearThreadConnection();
        }
    }

    // ==========================================
    // INVENTORY REPORTS
    // ==========================================

    @Override
    public ReportData getAvailableInventory(UserSession session) {
        return getInventoryFilteredByStatus(session, InventoryStatus.AVAILABLE, "Available Inventory Report");
    }

    @Override
    public ReportData getReservedInventory(UserSession session) {
        return getInventoryFilteredByStatus(session, InventoryStatus.RESERVED, "Reserved Inventory Report");
    }

    private ReportData getInventoryFilteredByStatus(UserSession session, InventoryStatus status, String title) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<InventoryItem> items = inventoryItemDao.findAll();
            List<InventoryItem> filtered = items.stream()
                    .filter(i -> i.getStatus() == status)
                    .collect(Collectors.toList());

            List<String> headers = Arrays.asList("Inventory ID", "Item Code", "Item Type", "Warehouse", "Location", "Serial Number");
            List<List<String>> rows = new ArrayList<>();
            for (InventoryItem i : filtered) {
                rows.add(Arrays.asList(
                        String.valueOf(i.getInventoryId()),
                        i.getItemCode() != null ? i.getItemCode() : "",
                        i.getItemType() != null ? i.getItemType().name() : "",
                        i.getWarehouse() != null ? i.getWarehouse() : "",
                        i.getLocation() != null ? i.getLocation() : "",
                        i.getSerialNumber() != null ? i.getSerialNumber() : ""
                ));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Count", String.valueOf(filtered.size()));
            return new ReportData(title, headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getInventoryByWarehouse(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<InventoryItem> items = inventoryItemDao.findAll();
            Map<String, Map<InventoryStatus, Long>> grouped = items.stream()
                    .collect(Collectors.groupingBy(
                            i -> i.getWarehouse() != null ? i.getWarehouse() : "UNASSIGNED",
                            Collectors.groupingBy(InventoryItem::getStatus, Collectors.counting())
                    ));

            List<String> headers = Arrays.asList("Warehouse", "AVAILABLE", "RESERVED", "ALLOCATED", "INSTALLED", "DAMAGED", "RETURNED", "Total Items");
            List<List<String>> rows = new ArrayList<>();

            grouped.forEach((wh, statMap) -> {
                long avail = statMap.getOrDefault(InventoryStatus.AVAILABLE, 0L);
                long res = statMap.getOrDefault(InventoryStatus.RESERVED, 0L);
                long alloc = statMap.getOrDefault(InventoryStatus.ALLOCATED, 0L);
                long inst = statMap.getOrDefault(InventoryStatus.INSTALLED, 0L);
                long dam = statMap.getOrDefault(InventoryStatus.DAMAGED, 0L);
                long ret = statMap.getOrDefault(InventoryStatus.RETURNED, 0L);
                long total = avail + res + alloc + inst + dam + ret;

                rows.add(Arrays.asList(wh, String.valueOf(avail), String.valueOf(res), String.valueOf(alloc),
                        String.valueOf(inst), String.valueOf(dam), String.valueOf(ret), String.valueOf(total)));
            });

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Warehouses", String.valueOf(grouped.size()));
            return new ReportData("Inventory Status by Warehouse", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getLowInventory(UserSession session, int threshold) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<InventoryItem> items = inventoryItemDao.findAll();
            Map<String, Map<String, Long>> warehouseTypeCounts = items.stream()
                    .filter(i -> i.getStatus() == InventoryStatus.AVAILABLE)
                    .collect(Collectors.groupingBy(
                            i -> i.getWarehouse() != null ? i.getWarehouse() : "DEFAULT",
                            Collectors.groupingBy(
                                    i -> i.getItemType() != null ? i.getItemType().name() : "UNKNOWN",
                                    Collectors.counting()
                            )
                    ));

            List<String> headers = Arrays.asList("Warehouse", "Item Type", "Available Count", "Threshold", "Status");
            List<List<String>> rows = new ArrayList<>();

            warehouseTypeCounts.forEach((wh, typeMap) -> {
                typeMap.forEach((type, count) -> {
                    if (count < threshold) {
                        rows.add(Arrays.asList(wh, type, String.valueOf(count), String.valueOf(threshold), "LOW INVENTORY"));
                    }
                });
            });

            Map<String, String> summary = new HashMap<>();
            summary.put("Threshold Used", String.valueOf(threshold));
            summary.put("Low Inventory Alerts Count", String.valueOf(rows.size()));
            return new ReportData("Low Inventory Stock Alerts", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getDamagedInventory(UserSession session) {
        return getInventoryFilteredByStatus(session, InventoryStatus.DAMAGED, "Damaged Inventory Stock Report");
    }

    // ==========================================
    // PROVISIONING REPORTS
    // ==========================================

    @Override
    public ReportData getSuccessfulProvisioningRequests(UserSession session) {
        return getProvisioningFilteredByStatus(session, ProvisioningStatus.SUCCESS, "Successful Provisioning Requests Report");
    }

    @Override
    public ReportData getFailedProvisioningRequests(UserSession session) {
        return getProvisioningFilteredByStatus(session, ProvisioningStatus.FAILED, "Failed Provisioning Requests Report");
    }

    private ReportData getProvisioningFilteredByStatus(UserSession session, ProvisioningStatus targetStatus, String title) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<ProvisioningRequest> reqs = provisioningRequestDao.findAll();
            List<ProvisioningRequest> filtered = reqs.stream()
                    .filter(r -> r.getStatus() == targetStatus)
                    .collect(Collectors.toList());

            List<String> headers = Arrays.asList("Provisioning ID", "Order ID", "Service ID", "Provisioning Type", "Engineer ID", "Requested Date", "Completed Date");
            List<List<String>> rows = new ArrayList<>();
            for (ProvisioningRequest r : filtered) {
                rows.add(Arrays.asList(
                        String.valueOf(r.getProvisioningId()),
                        String.valueOf(r.getOrderId()),
                        r.getServiceId() != null ? r.getServiceId() : "",
                        r.getProvisioningType() != null ? r.getProvisioningType().name() : "",
                        r.getEngineerId() != null ? String.valueOf(r.getEngineerId()) : "UNASSIGNED",
                        r.getRequestedDate() != null ? r.getRequestedDate().toString() : "",
                        r.getCompletedDate() != null ? r.getCompletedDate().toString() : ""
                ));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Count", String.valueOf(filtered.size()));
            return new ReportData(title, headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getProvisioningByServiceType(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<ProvisioningRequest> reqs = provisioningRequestDao.findAll();
            Map<ProvisioningType, Long> counts = reqs.stream()
                    .filter(r -> r.getProvisioningType() != null)
                    .collect(Collectors.groupingBy(ProvisioningRequest::getProvisioningType, Collectors.counting()));

            List<String> headers = Arrays.asList("Provisioning Service Type", "Request Count");
            List<List<String>> rows = new ArrayList<>();
            for (ProvisioningType type : ProvisioningType.values()) {
                Long count = counts.getOrDefault(type, 0L);
                rows.add(Arrays.asList(type.name(), String.valueOf(count)));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Requests", String.valueOf(reqs.size()));
            return new ReportData("Provisioning Requests by Service Type", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public double getAverageProvisioningTimeMinutes(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<ProvisioningRequest> reqs = provisioningRequestDao.findAll();
            return reqs.stream()
                    .filter(r -> r.getStatus() == ProvisioningStatus.SUCCESS)
                    .filter(r -> r.getRequestedDate() != null && r.getCompletedDate() != null)
                    .mapToLong(r -> Math.max(0, Duration.between(r.getRequestedDate(), r.getCompletedDate()).toMinutes()))
                    .average()
                    .orElse(0.0);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getEngineerWorkload(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<ProvisioningEngineer> engineers = provisioningEngineerDao.findAll();
            List<ProvisioningEngineer> sorted = engineers.stream()
                    .sorted(Comparator.comparing(ProvisioningEngineer::getActiveTasks).reversed())
                    .collect(Collectors.toList());

            List<String> headers = Arrays.asList("Engineer ID", "Employee Code", "Name", "Specialization", "Region", "Active Tasks", "Availability");
            List<List<String>> rows = new ArrayList<>();
            for (ProvisioningEngineer e : sorted) {
                rows.add(Arrays.asList(
                        String.valueOf(e.getEngineerId()),
                        e.getEmployeeCode() != null ? e.getEmployeeCode() : "",
                        e.getEngineerName() != null ? e.getEngineerName() : "",
                        e.getSpecialization() != null ? e.getSpecialization() : "",
                        e.getRegion() != null ? e.getRegion() : "",
                        String.valueOf(e.getActiveTasks()),
                        e.getAvailability() != null ? e.getAvailability().name() : ""
                ));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Engineers", String.valueOf(engineers.size()));
            return new ReportData("Provisioning Engineer Workload Summary", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    // ==========================================
    // REVENUE REPORTS (STRICT BIGDECIMAL AGGREGATION)
    // ==========================================

    @Override
    public ReportData getProductWiseRevenue(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<OrderItem> items = orderItemDao.findAll();
            Map<Long, BigDecimal> revenueMap = new HashMap<>();

            for (OrderItem item : items) {
                if (item.getProductId() != null) {
                    BigDecimal itemTotal;
                    if (item.getTotalAmount() != null) {
                        itemTotal = item.getTotalAmount();
                    } else if (item.getUnitPrice() != null && item.getQuantity() != null) {
                        itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    } else {
                        itemTotal = BigDecimal.ZERO;
                    }
                    revenueMap.put(item.getProductId(), revenueMap.getOrDefault(item.getProductId(), BigDecimal.ZERO).add(itemTotal));
                }
            }

            List<String> headers = Arrays.asList("Product ID", "Product Code", "Product Name", "Total Revenue (₹)");
            List<List<String>> rows = new ArrayList<>();

            BigDecimal overallTotal = BigDecimal.ZERO;
            List<Map.Entry<Long, BigDecimal>> sorted = revenueMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .collect(Collectors.toList());

            for (Map.Entry<Long, BigDecimal> entry : sorted) {
                Long prodId = entry.getKey();
                BigDecimal rev = entry.getValue().setScale(2, RoundingMode.HALF_UP);
                overallTotal = overallTotal.add(rev);

                String code = "PROD_" + prodId;
                String name = "Product #" + prodId;
                Optional<TelecomProduct> prodOpt = telecomProductDao.findById(prodId);
                if (prodOpt.isPresent()) {
                    code = prodOpt.get().getProductCode();
                    name = prodOpt.get().getProductName();
                }
                rows.add(Arrays.asList(String.valueOf(prodId), code, name, rev.toString()));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Overall Product Revenue", overallTotal.setScale(2, RoundingMode.HALF_UP).toString());
            return new ReportData("Product-Wise Revenue Report", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getMonthlyRevenue(UserSession session, int year) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            Map<Month, BigDecimal> monthRevenue = new HashMap<>();

            for (TelecomOrder o : orders) {
                if (o.getOrderDate() != null && o.getOrderDate().getYear() == year && o.getTotalAmount() != null) {
                    Month m = o.getOrderDate().getMonth();
                    monthRevenue.put(m, monthRevenue.getOrDefault(m, BigDecimal.ZERO).add(o.getTotalAmount()));
                }
            }

            List<String> headers = Arrays.asList("Month", "Total Orders Count", "Total Revenue (₹)");
            List<List<String>> rows = new ArrayList<>();
            BigDecimal yearlyTotal = BigDecimal.ZERO;

            for (Month month : Month.values()) {
                BigDecimal rev = monthRevenue.getOrDefault(month, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                yearlyTotal = yearlyTotal.add(rev);

                long count = orders.stream()
                        .filter(o -> o.getOrderDate() != null && o.getOrderDate().getYear() == year && o.getOrderDate().getMonth() == month)
                        .count();

                rows.add(Arrays.asList(month.name(), String.valueOf(count), rev.toString()));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Year", String.valueOf(year));
            summary.put("Total Yearly Revenue", yearlyTotal.setScale(2, RoundingMode.HALF_UP).toString());
            return new ReportData("Monthly Revenue Breakdown (" + year + ")", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getCustomerTypeRevenue(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            List<Customer> customers = customerDao.findAll();
            Map<Long, CustomerType> custTypeMap = customers.stream()
                    .collect(Collectors.toMap(Customer::getCustomerId, Customer::getCustomerType, (c1, c2) -> c1));

            Map<String, BigDecimal> typeRevenue = new HashMap<>();
            for (TelecomOrder o : orders) {
                if (o.getTotalAmount() != null) {
                    CustomerType type = custTypeMap.get(o.getCustomerId());
                    String typeName = type != null ? type.name() : "UNKNOWN";
                    typeRevenue.put(typeName, typeRevenue.getOrDefault(typeName, BigDecimal.ZERO).add(o.getTotalAmount()));
                }
            }

            List<String> headers = Arrays.asList("Customer Type", "Total Revenue (₹)");
            List<List<String>> rows = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;

            for (Map.Entry<String, BigDecimal> entry : typeRevenue.entrySet()) {
                BigDecimal rev = entry.getValue().setScale(2, RoundingMode.HALF_UP);
                total = total.add(rev);
                rows.add(Arrays.asList(entry.getKey(), rev.toString()));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Revenue", total.setScale(2, RoundingMode.HALF_UP).toString());
            return new ReportData("Customer-Type Revenue Report", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getPaymentModeAnalysis(UserSession session) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<OrderPayment> payments = orderPaymentDao.findAll();
            List<OrderPayment> successfulPayments = payments.stream()
                    .filter(p -> p.getStatus() == PaymentTransactionStatus.SUCCESS)
                    .collect(Collectors.toList());

            Map<PaymentMode, Long> counts = successfulPayments.stream()
                    .filter(p -> p.getPaymentMode() != null)
                    .collect(Collectors.groupingBy(OrderPayment::getPaymentMode, Collectors.counting()));

            Map<PaymentMode, BigDecimal> totals = new HashMap<>();
            for (OrderPayment p : successfulPayments) {
                if (p.getPaymentMode() != null && p.getAmount() != null) {
                    totals.put(p.getPaymentMode(), totals.getOrDefault(p.getPaymentMode(), BigDecimal.ZERO).add(p.getAmount()));
                }
            }

            List<String> headers = Arrays.asList("Payment Mode", "Successful Payments Count", "Total Amount Collected (₹)");
            List<List<String>> rows = new ArrayList<>();
            BigDecimal overallCollected = BigDecimal.ZERO;

            for (PaymentMode mode : PaymentMode.values()) {
                Long count = counts.getOrDefault(mode, 0L);
                BigDecimal amount = totals.getOrDefault(mode, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                overallCollected = overallCollected.add(amount);
                rows.add(Arrays.asList(mode.name(), String.valueOf(count), amount.toString()));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Total Successful Payments Collected", overallCollected.setScale(2, RoundingMode.HALF_UP).toString());
            return new ReportData("Payment Mode Analysis (Successful Payments)", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    @Override
    public ReportData getTopCustomersByRevenue(UserSession session, int limit) {
        checkAuthorization(session);
        bindThreadConnection();
        try {
            List<TelecomOrder> orders = telecomOrderDao.findAll();
            Map<Long, BigDecimal> custSpending = new HashMap<>();

            for (TelecomOrder o : orders) {
                if (o.getCustomerId() != null && o.getTotalAmount() != null) {
                    custSpending.put(o.getCustomerId(), custSpending.getOrDefault(o.getCustomerId(), BigDecimal.ZERO).add(o.getTotalAmount()));
                }
            }

            List<Map.Entry<Long, BigDecimal>> sorted = custSpending.entrySet().stream()
                    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                    .limit(limit > 0 ? limit : Long.MAX_VALUE)
                    .collect(Collectors.toList());

            List<String> headers = Arrays.asList("Rank", "Customer ID", "Customer Number", "Customer Name", "Customer Type", "Total Spent (₹)");
            List<List<String>> rows = new ArrayList<>();
            int rank = 1;

            for (Map.Entry<Long, BigDecimal> entry : sorted) {
                Long custId = entry.getKey();
                BigDecimal spent = entry.getValue().setScale(2, RoundingMode.HALF_UP);

                String num = "CUST_" + custId;
                String name = "Customer #" + custId;
                String type = "UNKNOWN";

                Optional<Customer> cOpt = customerDao.findById(custId);
                if (cOpt.isPresent()) {
                    Customer c = cOpt.get();
                    num = c.getCustomerNumber();
                    name = c.getCustomerName();
                    if (c.getCustomerType() != null) {
                        type = c.getCustomerType().name();
                    }
                }
                rows.add(Arrays.asList(String.valueOf(rank++), String.valueOf(custId), num, name, type, spent.toString()));
            }

            Map<String, String> summary = new HashMap<>();
            summary.put("Limit", String.valueOf(limit));
            return new ReportData("Top Customers by Monetary Revenue", headers, rows, summary);
        } finally {
            clearThreadConnection();
        }
    }

    // ==========================================
    // FILE EXPORT (CSV / TXT)
    // ==========================================

    @Override
    public boolean exportReportToCsv(UserSession session, ReportData reportData, String filePath) {
        checkAuthorization(session);
        Objects.requireNonNull(reportData, "reportData must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("# " + reportData.getTitle());
            writer.newLine();
            writer.write("# Generated At: " + reportData.getGeneratedAt());
            writer.newLine();

            if (!reportData.getSummaryMetrics().isEmpty()) {
                writer.write("# Summary Metrics: " + reportData.getSummaryMetrics());
                writer.newLine();
            }

            // Write Headers
            List<String> headers = reportData.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                writer.write(escapeCsvValue(headers.get(i)));
                if (i < headers.size() - 1) {
                    writer.write(",");
                }
            }
            writer.newLine();

            // Write Rows
            for (List<String> row : reportData.getRows()) {
                for (int i = 0; i < row.size(); i++) {
                    writer.write(escapeCsvValue(row.get(i)));
                    if (i < row.size() - 1) {
                        writer.write(",");
                    }
                }
                writer.newLine();
            }
            writer.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        boolean containsSpecial = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (containsSpecial) {
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        return value;
    }

    @Override
    public boolean exportReportToText(UserSession session, ReportData reportData, String filePath) {
        checkAuthorization(session);
        Objects.requireNonNull(reportData, "reportData must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("==========================================================================");
            writer.newLine();
            writer.write("  " + reportData.getTitle().toUpperCase());
            writer.newLine();
            writer.write("==========================================================================");
            writer.newLine();
            writer.write("Generated At: " + reportData.getGeneratedAt());
            writer.newLine();

            if (!reportData.getSummaryMetrics().isEmpty()) {
                writer.write("Summary Metrics:");
                writer.newLine();
                reportData.getSummaryMetrics().forEach((k, v) -> {
                    try {
                        writer.write("  - " + k + ": " + v);
                        writer.newLine();
                    } catch (IOException ignored) { }
                });
            }
            writer.write("--------------------------------------------------------------------------");
            writer.newLine();

            // Write Table Headers
            List<String> headers = reportData.getHeaders();
            writer.write(String.join(" | ", headers));
            writer.newLine();
            writer.write("--------------------------------------------------------------------------");
            writer.newLine();

            // Write Table Rows
            for (List<String> row : reportData.getRows()) {
                writer.write(String.join(" | ", row));
                writer.newLine();
            }

            writer.write("==========================================================================");
            writer.newLine();
            writer.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
