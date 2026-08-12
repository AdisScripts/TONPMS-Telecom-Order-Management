package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.CustomerNotEligibleException;
import com.amdocs.telecom.exception.DuplicateOrderException;
import com.amdocs.telecom.exception.InvalidOrderException;
import com.amdocs.telecom.exception.ProductUnavailableException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.AuthorizationService;
import com.amdocs.telecom.security.AuthorizationServiceImpl;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.CustomerService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.ProductService;
import com.amdocs.telecom.service.pricing.PricingStrategy;
import com.amdocs.telecom.service.pricing.PricingStrategyFactory;
import com.amdocs.telecom.util.DatabaseConnection;
import com.amdocs.telecom.util.JdbcTransactionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class OrderServiceImpl implements OrderService {
    private final TelecomOrderDao telecomOrderDao;
    private final OrderItemDao orderItemDao;
    private final CustomerService customerService;
    private final ProductService productService;
    private final AuthorizationService authorizationService;

    public OrderServiceImpl(TelecomOrderDao telecomOrderDao, OrderItemDao orderItemDao,
                            CustomerService customerService, ProductService productService) {
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.customerService = Objects.requireNonNull(customerService, "customerService must not be null");
        this.productService = Objects.requireNonNull(productService, "productService must not be null");
        this.authorizationService = new AuthorizationServiceImpl();
    }

    public OrderServiceImpl(TelecomOrderDao telecomOrderDao, OrderItemDao orderItemDao,
                            CustomerService customerService, ProductService productService,
                            AuthorizationService authorizationService) {
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.customerService = Objects.requireNonNull(customerService, "customerService must not be null");
        this.productService = Objects.requireNonNull(productService, "productService must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
    }

    @Override
    public TelecomOrder createOrder(UserSession session, Long customerId, OrderType orderType,
                                     LocalDate requestedActivationDate, List<OrderItemRequest> itemRequests)
            throws AccessDeniedException, CustomerNotEligibleException, ProductUnavailableException, DuplicateOrderException, InvalidOrderException {

        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (customerId == null || orderType == null) {
            throw new IllegalArgumentException("customerId and orderType must not be null.");
        }

        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(customerId);
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Customers can only create orders for themselves.");
        }

        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item request.");
        }

        // 1. Eligibility Check
        Customer customer = customerService.getCustomerProfile(customerId);
        customerService.checkCustomerEligibility(customerId);

        // 2. Product Availability & Pricing Calculation
        PricingStrategy pricingStrategy = PricingStrategyFactory.getStrategy(customer.getCustomerType());
        List<OrderItem> calculatedItems = new ArrayList<>();
        BigDecimal orderTotal = BigDecimal.ZERO;
        Map<Long, Integer> requestedItemMap = new HashMap<>();
        Set<Long> seenProductIds = new HashSet<>();

        for (OrderItemRequest request : itemRequests) {
            if (request == null || request.getProductId() == null || request.getQuantity() <= 0) {
                throw new InvalidOrderException("Invalid item request: quantity must be positive.");
            }
            if (!seenProductIds.add(request.getProductId())) {
                throw new InvalidOrderException("Duplicate product ID " + request.getProductId() + " in single order request.");
            }
            productService.checkProductAvailability(request.getProductId());
            TelecomProduct product = productService.getProductById(request.getProductId());

            OrderItem calculatedItem = pricingStrategy.calculateItemTotal(product, request.getQuantity());
            calculatedItems.add(calculatedItem);
            orderTotal = orderTotal.add(calculatedItem.getTotalAmount());

            requestedItemMap.put(request.getProductId(), request.getQuantity());
        }

        // 3. Duplicate Order Check (within 2 minutes)
        checkForDuplicateOrder(customerId, requestedItemMap);

        // 4. Create Master TelecomOrder Object
        TelecomOrder order = new TelecomOrder();
        order.setOrderNumber("TEMP-" + System.nanoTime()); // Temporary unique placeholder
        order.setCustomerId(customerId);
        order.setOrderDate(LocalDateTime.now());
        order.setOrderType(orderType);
        order.setTotalAmount(orderTotal);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderStatus(OrderStatus.CREATED);
        order.setRequestedActivationDate(requestedActivationDate);

        // 5. Atomic JDBC Transaction Execution
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            DatabaseConnection.setThreadConnection(conn);
            JdbcTransactionManager.begin(conn);

            long orderId = telecomOrderDao.save(order);
            order.setOrderId(orderId);

            for (OrderItem item : calculatedItems) {
                item.setOrderId(orderId);
            }
            orderItemDao.saveBatch(calculatedItems);

            // Format final order number: ORD-YYYY-NNNNNN
            int year = order.getOrderDate().getYear();
            String finalOrderNumber = String.format("ORD-%d-%06d", year, orderId);
            order.setOrderNumber(finalOrderNumber);
            telecomOrderDao.update(order);

            JdbcTransactionManager.commit(conn);
            order.setOrderItems(calculatedItems);
            return order;

        } catch (Exception ex) {
            if (conn != null) {
                try {
                    JdbcTransactionManager.rollback(conn);
                } catch (Exception ignored) { }
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException("Failed to complete order creation transaction.", ex);
        } finally {
            DatabaseConnection.clearThreadConnection();
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public TelecomOrder getOrderById(UserSession session, Long orderId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null.");
        }
        Optional<TelecomOrder> optional = telecomOrderDao.findById(orderId);
        TelecomOrder order = optional.orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
        checkOrderViewAccess(session, order);
        order.setOrderItems(orderItemDao.findByOrderId(orderId));
        return order;
    }

    @Override
    public TelecomOrder getOrderByNumber(UserSession session, String orderNumber) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("orderNumber must not be null or empty.");
        }
        Optional<TelecomOrder> optional = telecomOrderDao.findByOrderNumber(orderNumber);
        TelecomOrder order = optional.orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));
        checkOrderViewAccess(session, order);
        order.setOrderItems(orderItemDao.findByOrderId(order.getOrderId()));
        return order;
    }

    @Override
    public List<TelecomOrder> getOrdersByCustomer(UserSession session, Long customerId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(customerId);
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Customers can only view their own orders.");
        }
        return telecomOrderDao.findByCustomerId(customerId);
    }

    @Override
    public List<TelecomOrder> getOrdersByStatus(UserSession session, OrderStatus status) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null.");
        }
        List<TelecomOrder> all = telecomOrderDao.findAll();
        List<TelecomOrder> result = new ArrayList<>();
        for (TelecomOrder order : all) {
            if (order.getOrderStatus() == status) {
                result.add(order);
            }
        }
        return result;
    }

    @Override
    public List<OrderItem> getOrderItems(UserSession session, Long orderId) throws AccessDeniedException {
        getOrderById(session, orderId); // verifies view authorization
        return orderItemDao.findByOrderId(orderId);
    }

    @Override
    public void updateOrderStatus(UserSession session, Long orderId, OrderStatus newStatus) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (orderId == null || newStatus == null) {
            throw new IllegalArgumentException("orderId and newStatus must not be null.");
        }
        TelecomOrder order = getOrderById(session, orderId);
        order.setOrderStatus(newStatus);
        boolean updated = telecomOrderDao.update(order);
        if (!updated) {
            throw new IllegalStateException("Failed to update order status.");
        }
    }

    @Override
    public void cancelOrder(UserSession session, Long orderId) throws AccessDeniedException, InvalidOrderException {
        TelecomOrder order = getOrderById(session, orderId); // verifies view access
        OrderStatus currentStatus = order.getOrderStatus();
        if (currentStatus != OrderStatus.CREATED && currentStatus != OrderStatus.VALIDATED && currentStatus != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderException("Cannot cancel an order in " + currentStatus + " state.");
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        boolean updated = telecomOrderDao.update(order);
        if (!updated) {
            throw new IllegalStateException("Failed to cancel order.");
        }
    }

    private void checkOrderViewAccess(UserSession session, TelecomOrder order) throws AccessDeniedException {
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(order.getCustomerId());
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Access denied to requested order.");
        }
    }

    private void checkForDuplicateOrder(Long customerId, Map<Long, Integer> requestedItemMap) throws DuplicateOrderException {
        List<TelecomOrder> customerOrders = telecomOrderDao.findByCustomerId(customerId);
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(2);

        for (TelecomOrder order : customerOrders) {
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            if (order.getOrderDate() != null && order.getOrderDate().isAfter(thresholdTime)) {
                List<OrderItem> existingItems = orderItemDao.findByOrderId(order.getOrderId());
                Map<Long, Integer> existingItemMap = new HashMap<>();
                for (OrderItem item : existingItems) {
                    existingItemMap.put(item.getProductId(), item.getQuantity());
                }
                if (existingItemMap.equals(requestedItemMap)) {
                    throw new DuplicateOrderException("A duplicate order with identical items was placed within the last 2 minutes.");
                }
            }
        }
    }
}
