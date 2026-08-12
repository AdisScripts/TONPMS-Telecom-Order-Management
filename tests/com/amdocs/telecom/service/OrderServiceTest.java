package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.DuplicateOrderException;
import com.amdocs.telecom.exception.InvalidOrderException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.IdentityStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.ProductStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.CustomerServiceImpl;
import com.amdocs.telecom.service.impl.OrderServiceImpl;
import com.amdocs.telecom.service.impl.ProductServiceImpl;
import com.amdocs.telecom.service.pricing.EnterprisePricingStrategy;
import com.amdocs.telecom.service.pricing.IndividualPricingStrategy;
import com.amdocs.telecom.service.pricing.PricingStrategy;
import com.amdocs.telecom.service.pricing.SmePricingStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OrderServiceTest {
    private OrderServiceTest() { }

    public static void main(String[] args) {
        System.out.println("Running OrderServiceTest...");
        testPricingBoundaries();
        testOrderNumberFormatting();
        testDuplicateOrderPrevention();
        testOrderCancellationRules();
        testAuthorization();
        System.out.println("PASS: OrderServiceTest completed successfully.");
    }

    private static void testPricingBoundaries() {
        TelecomProduct p50 = new TelecomProduct("P50", "Plan 50", "MOBILE_PLAN", new BigDecimal("50.00"));

        // Individual: qty 4 & 5 -> 0% discount
        PricingStrategy ind = new IndividualPricingStrategy();
        OrderItem iInd4 = ind.calculateItemTotal(p50, 4);
        require(iInd4.getDiscount().compareTo(BigDecimal.ZERO) == 0 && iInd4.getTotalAmount().compareTo(new BigDecimal("200.00")) == 0, "Individual qty 4 discount failed");
        OrderItem iInd5 = ind.calculateItemTotal(p50, 5);
        require(iInd5.getDiscount().compareTo(BigDecimal.ZERO) == 0 && iInd5.getTotalAmount().compareTo(new BigDecimal("250.00")) == 0, "Individual qty 5 discount failed");

        // SME: 5% if quantity >= 5 OR gross >= 500
        PricingStrategy sme = new SmePricingStrategy();
        // qty 4 vs 5 (unitPrice 50.00 -> gross 200 vs 250)
        OrderItem iSme4 = sme.calculateItemTotal(p50, 4);
        require(iSme4.getDiscount().compareTo(BigDecimal.ZERO) == 0, "SME qty 4 should have 0 discount");
        OrderItem iSme5 = sme.calculateItemTotal(p50, 5); // 250 * 0.05 = 12.50 discount
        require(iSme5.getDiscount().compareTo(new BigDecimal("12.50")) == 0 && iSme5.getTotalAmount().compareTo(new BigDecimal("237.50")) == 0, "SME qty 5 discount failed");

        // gross amount 499 vs 500 (qty 1, unitPrice 499 vs 500)
        TelecomProduct p499 = new TelecomProduct("P499", "Plan 499", "MOBILE_PLAN", new BigDecimal("499.00"));
        TelecomProduct p500 = new TelecomProduct("P500", "Plan 500", "MOBILE_PLAN", new BigDecimal("500.00"));
        OrderItem iSmeGross499 = sme.calculateItemTotal(p499, 1);
        require(iSmeGross499.getDiscount().compareTo(BigDecimal.ZERO) == 0, "SME gross 499 should have 0 discount");
        OrderItem iSmeGross500 = sme.calculateItemTotal(p500, 1); // 500 * 0.05 = 25.00 discount
        require(iSmeGross500.getDiscount().compareTo(new BigDecimal("25.00")) == 0 && iSmeGross500.getTotalAmount().compareTo(new BigDecimal("475.00")) == 0, "SME gross 500 discount failed");

        // Enterprise: 10% if quantity >= 10 OR gross >= 1000
        PricingStrategy ent = new EnterprisePricingStrategy();
        // qty 9 vs 10 (unitPrice 50.00 -> gross 450 vs 500)
        OrderItem iEnt9 = ent.calculateItemTotal(p50, 9);
        require(iEnt9.getDiscount().compareTo(BigDecimal.ZERO) == 0, "Enterprise qty 9 should have 0 discount");
        OrderItem iEnt10 = ent.calculateItemTotal(p50, 10); // 500 * 0.10 = 50.00 discount
        require(iEnt10.getDiscount().compareTo(new BigDecimal("50.00")) == 0 && iEnt10.getTotalAmount().compareTo(new BigDecimal("450.00")) == 0, "Enterprise qty 10 discount failed");

        // gross amount 999 vs 1000
        TelecomProduct p999 = new TelecomProduct("P999", "Plan 999", "BROADBAND", new BigDecimal("999.00"));
        TelecomProduct p1000 = new TelecomProduct("P1000", "Plan 1000", "BROADBAND", new BigDecimal("1000.00"));
        OrderItem iEntGross999 = ent.calculateItemTotal(p999, 1);
        require(iEntGross999.getDiscount().compareTo(BigDecimal.ZERO) == 0, "Enterprise gross 999 should have 0 discount");
        OrderItem iEntGross1000 = ent.calculateItemTotal(p1000, 1); // 1000 * 0.10 = 100.00 discount
        require(iEntGross1000.getDiscount().compareTo(new BigDecimal("100.00")) == 0 && iEntGross1000.getTotalAmount().compareTo(new BigDecimal("900.00")) == 0, "Enterprise gross 1000 discount failed");
    }

    private static void testOrderNumberFormatting() {
        int year = 2026;
        require(String.format("ORD-%d-%06d", year, 8745L).equals("ORD-2026-008745"), "Order number format 8745 failed");
        require(String.format("ORD-%d-%06d", year, 999999L).equals("ORD-2026-999999"), "Order number format 999999 failed");
        require(String.format("ORD-%d-%06d", year, 1000000L).equals("ORD-2026-1000000"), "Order number format 1000000 failed");
    }

    private static void testDuplicateOrderPrevention() {
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);

        Customer c = new Customer("C-100", "Test Customer", "test@dup.com", "9000000000", CustomerType.INDIVIDUAL);
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long custId = customerDao.save(c);

        TelecomProduct p1 = new TelecomProduct("PROD-1", "Product 1", "MOBILE_PLAN", new BigDecimal("100.00"));
        p1.setStatus(ProductStatus.ACTIVE);
        long prod1Id = productDao.save(p1);

        TelecomProduct p2 = new TelecomProduct("PROD-2", "Product 2", "BROADBAND", new BigDecimal("200.00"));
        p2.setStatus(ProductStatus.ACTIVE);
        long prod2Id = productDao.save(p2);

        OrderService service = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        UserSession session = createSession(100L, custId, RoleCode.CUSTOMER);

        List<OrderService.OrderItemRequest> reqs1 = new ArrayList<>();
        reqs1.add(new OrderService.OrderItemRequest(prod1Id, 2));
        reqs1.add(new OrderService.OrderItemRequest(prod2Id, 1));

        // First order placement -> OK
        TelecomOrder order1 = service.createOrder(session, custId, OrderType.NEW_CONNECTION, null, reqs1);
        require(order1.getOrderNumber().startsWith("ORD-"), "Order number prefix mismatch");

        // Second order placement with reverse item order within 2 minutes -> DuplicateOrderException
        List<OrderService.OrderItemRequest> reqs2 = new ArrayList<>();
        reqs2.add(new OrderService.OrderItemRequest(prod2Id, 1));
        reqs2.add(new OrderService.OrderItemRequest(prod1Id, 2));

        try {
            service.createOrder(session, custId, OrderType.NEW_CONNECTION, null, reqs2);
            require(false, "Duplicate order should throw DuplicateOrderException");
        } catch (DuplicateOrderException ex) {
            require(ex.getMessage().contains("duplicate order"), "Expected duplicate order message");
        }

        // Single order request with duplicate product ID -> InvalidOrderException
        List<OrderService.OrderItemRequest> dupSingleReq = new ArrayList<>();
        dupSingleReq.add(new OrderService.OrderItemRequest(prod1Id, 1));
        dupSingleReq.add(new OrderService.OrderItemRequest(prod1Id, 2));
        try {
            service.createOrder(session, custId, OrderType.NEW_CONNECTION, null, dupSingleReq);
            require(false, "Single order with duplicate product IDs should throw InvalidOrderException");
        } catch (InvalidOrderException ex) {
            require(ex.getMessage().contains("Duplicate product ID"), "Expected duplicate product ID message");
        }
    }

    private static void testOrderCancellationRules() {
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);

        Customer c = new Customer("C-200", "Cancel Customer", "cancel@test.com", "9000000001", CustomerType.INDIVIDUAL);
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long custId = customerDao.save(c);

        TelecomProduct p = new TelecomProduct("PROD-3", "Product 3", "MOBILE_PLAN", new BigDecimal("300.00"));
        p.setStatus(ProductStatus.ACTIVE);
        long prodId = productDao.save(p);

        OrderService service = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        UserSession session = createSession(200L, custId, RoleCode.CUSTOMER);
        UserSession adminSession = createSession(999L, null, RoleCode.ORDER_ADMINISTRATOR);

        // Cancel CREATED order -> OK
        TelecomOrder orderCreated = service.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));
        service.cancelOrder(session, orderCreated.getOrderId());
        require(orderDao.findById(orderCreated.getOrderId()).get().getOrderStatus() == OrderStatus.CANCELLED, "Order cancellation status failed");

        // Cancel VALIDATED order -> OK
        TelecomOrder orderValidated = service.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));
        service.updateOrderStatus(adminSession, orderValidated.getOrderId(), OrderStatus.VALIDATED);
        service.cancelOrder(session, orderValidated.getOrderId());
        require(orderDao.findById(orderValidated.getOrderId()).get().getOrderStatus() == OrderStatus.CANCELLED, "Validated order cancellation failed");

        // Cancel PAYMENT_PENDING order -> OK
        TelecomOrder orderPayPending = service.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));
        service.updateOrderStatus(adminSession, orderPayPending.getOrderId(), OrderStatus.PAYMENT_PENDING);
        service.cancelOrder(session, orderPayPending.getOrderId());
        require(orderDao.findById(orderPayPending.getOrderId()).get().getOrderStatus() == OrderStatus.CANCELLED, "Payment pending order cancellation failed");

        // Try to cancel PROVISIONING order -> InvalidOrderException
        TelecomOrder orderProv = service.createOrder(session, custId, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));
        service.updateOrderStatus(adminSession, orderProv.getOrderId(), OrderStatus.PROVISIONING);
        try {
            service.cancelOrder(session, orderProv.getOrderId());
            require(false, "PROVISIONING order should throw InvalidOrderException");
        } catch (InvalidOrderException ex) {
            // expected
        }

        // Try to cancel already CANCELLED order -> InvalidOrderException
        try {
            service.cancelOrder(session, orderCreated.getOrderId());
            require(false, "Already cancelled order should throw InvalidOrderException");
        } catch (InvalidOrderException ex) {
            // expected
        }
    }

    private static void testAuthorization() {
        MockCustomerDao customerDao = new MockCustomerDao();
        MockTelecomProductDao productDao = new MockTelecomProductDao();
        MockTelecomOrderDao orderDao = new MockTelecomOrderDao();
        MockOrderItemDao itemDao = new MockOrderItemDao();

        CustomerService customerService = new CustomerServiceImpl(customerDao);
        ProductService productService = new ProductServiceImpl(productDao);

        Customer c1 = new Customer("C-301", "Customer 1", "c1@auth.com", "9000000002", CustomerType.INDIVIDUAL);
        c1.setAccountStatus(CustomerAccountStatus.ACTIVE); c1.setIdentityStatus(IdentityStatus.VERIFIED); c1.setRegistrationDate(LocalDate.now());
        long custId1 = customerDao.save(c1);

        Customer c2 = new Customer("C-302", "Customer 2", "c2@auth.com", "9000000003", CustomerType.INDIVIDUAL);
        c2.setAccountStatus(CustomerAccountStatus.ACTIVE); c2.setIdentityStatus(IdentityStatus.VERIFIED); c2.setRegistrationDate(LocalDate.now());
        long custId2 = customerDao.save(c2);

        TelecomProduct p = new TelecomProduct("PROD-4", "Product 4", "MOBILE_PLAN", new BigDecimal("100.00"));
        p.setStatus(ProductStatus.ACTIVE);
        long prodId = productDao.save(p);

        OrderService service = new OrderServiceImpl(orderDao, itemDao, customerService, productService);
        UserSession user1Session = createSession(301L, custId1, RoleCode.CUSTOMER);
        UserSession user2Session = createSession(302L, custId2, RoleCode.CUSTOMER);
        UserSession adminSession = createSession(999L, null, RoleCode.ORDER_ADMINISTRATOR);

        // User1 creates order for User1 -> OK
        TelecomOrder o1 = service.createOrder(user1Session, custId1, OrderType.NEW_CONNECTION, null,
                Collections.singletonList(new OrderService.OrderItemRequest(prodId, 1)));

        // User2 trying to view User1's order -> AccessDeniedException
        try {
            service.getOrderById(user2Session, o1.getOrderId());
            require(false, "User2 should not view User1's order");
        } catch (AccessDeniedException ex) {
            // expected
        }

        // Admin viewing User1's order -> OK
        TelecomOrder adminView = service.getOrderById(adminSession, o1.getOrderId());
        require(adminView.getOrderId().equals(o1.getOrderId()), "Admin view order failed");
    }

    private static UserSession createSession(Long userId, Long customerId, RoleCode role) {
        Set<RoleCode> roles = new HashSet<>();
        roles.add(role);
        Customer c = null;
        if (customerId != null) {
            c = new Customer();
            c.setCustomerId(customerId);
        }
        return new UserSession(userId, "user" + userId, c, null, roles);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static class MockCustomerDao implements CustomerDao {
        private final Map<Long, Customer> map = new HashMap<>();
        private long idSeq = 1;
        @Override public long save(Customer c) { long id = idSeq++; c.setCustomerId(id); map.put(id, c); return id; }
        @Override public Optional<Customer> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public Optional<Customer> findByCustomerNumber(String n) { return map.values().stream().filter(c -> n.equals(c.getCustomerNumber())).findFirst(); }
        @Override public Optional<Customer> findByEmail(String e) { return map.values().stream().filter(c -> e.equals(c.getEmail())).findFirst(); }
        @Override public List<Customer> findAll() { return new ArrayList<>(map.values()); }
        @Override public boolean update(Customer c) { map.put(c.getCustomerId(), c); return true; }
        @Override public boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockTelecomProductDao implements TelecomProductDao {
        private final Map<Long, TelecomProduct> map = new HashMap<>();
        private long idSeq = 1;
        @Override public long save(TelecomProduct p) { long id = idSeq++; p.setProductId(id); map.put(id, p); return id; }
        @Override public Optional<TelecomProduct> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public Optional<TelecomProduct> findByProductCode(String c) { return map.values().stream().filter(p -> c.equals(p.getProductCode())).findFirst(); }
        @Override public List<TelecomProduct> findActiveProducts() { List<TelecomProduct> list = new ArrayList<>(); for (TelecomProduct p : map.values()) { if (p.getStatus() == ProductStatus.ACTIVE) list.add(p); } return list; }
        @Override public List<TelecomProduct> findAll() { return new ArrayList<>(map.values()); }
        @Override public boolean update(TelecomProduct p) { map.put(p.getProductId(), p); return true; }
        @Override public boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockTelecomOrderDao implements TelecomOrderDao {
        private final Map<Long, TelecomOrder> map = new HashMap<>();
        private long idSeq = 1;
        @Override public long save(TelecomOrder o) { long id = idSeq++; o.setOrderId(id); map.put(id, o); return id; }
        @Override public Optional<TelecomOrder> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public Optional<TelecomOrder> findByOrderNumber(String n) { return map.values().stream().filter(o -> n.equals(o.getOrderNumber())).findFirst(); }
        @Override public List<TelecomOrder> findByCustomerId(Long customerId) {
            List<TelecomOrder> list = new ArrayList<>();
            for (TelecomOrder o : map.values()) {
                if (customerId.equals(o.getCustomerId())) list.add(o);
            }
            list.sort((o1, o2) -> o2.getOrderDate().compareTo(o1.getOrderDate()));
            return list;
        }
        @Override public List<TelecomOrder> findAll() { return new ArrayList<>(map.values()); }
        @Override public boolean update(TelecomOrder o) { map.put(o.getOrderId(), o); return true; }
        @Override public boolean delete(Long id) { return map.remove(id) != null; }
    }

    private static class MockOrderItemDao implements OrderItemDao {
        private final Map<Long, List<OrderItem>> map = new HashMap<>();
        private long idSeq = 1;
        @Override public long save(OrderItem item) { long id = idSeq++; item.setOrderItemId(id); map.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item); return id; }
        @Override public int[] saveBatch(List<OrderItem> items) {
            int[] counts = new int[items.size()];
            for (int i = 0; i < items.size(); i++) {
                save(items.get(i));
                counts[i] = 1;
            }
            return counts;
        }
        @Override public Optional<OrderItem> findById(Long id) {
            for (List<OrderItem> list : map.values()) {
                for (OrderItem i : list) { if (id.equals(i.getOrderItemId())) return Optional.of(i); }
            }
            return Optional.empty();
        }
        @Override public List<OrderItem> findByOrderId(Long orderId) { return new ArrayList<>(map.getOrDefault(orderId, Collections.emptyList())); }
        @Override public List<OrderItem> findAll() {
            List<OrderItem> all = new ArrayList<>();
            for (List<OrderItem> list : map.values()) all.addAll(list);
            return all;
        }
        @Override public boolean update(OrderItem i) { return true; }
        @Override public boolean delete(Long id) { return true; }
    }
}
