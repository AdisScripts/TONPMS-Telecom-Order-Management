package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.OrderPaymentDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.InvalidOrderException;
import com.amdocs.telecom.exception.InventoryUnavailableException;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.PaymentTransactionStatus;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.InventoryService;
import com.amdocs.telecom.service.OrderService;
import com.amdocs.telecom.service.PaymentService;
import com.amdocs.telecom.util.DatabaseConnection;
import com.amdocs.telecom.util.JdbcTransactionManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class PaymentServiceImpl implements PaymentService {
    private final OrderPaymentDao orderPaymentDao;
    private final TelecomOrderDao telecomOrderDao;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public PaymentServiceImpl(OrderPaymentDao orderPaymentDao, TelecomOrderDao telecomOrderDao,
                              OrderService orderService, InventoryService inventoryService,
                              AuditService auditService) {
        this.orderPaymentDao = Objects.requireNonNull(orderPaymentDao, "orderPaymentDao must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderService = Objects.requireNonNull(orderService, "orderService must not be null");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    @Override
    public OrderPayment processPayment(UserSession session, Long orderId, BigDecimal amount, PaymentMode paymentMode)
            throws AccessDeniedException, InvalidOrderException, InventoryUnavailableException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (orderId == null || amount == null || paymentMode == null) {
            throw new IllegalArgumentException("orderId, amount, and paymentMode must not be null.");
        }

        TelecomOrder order = orderService.getOrderById(session, orderId); // verifies view access

        // Verify payment status (ONLY PENDING or FAILED allowed, reject SUCCESS, REFUNDED, etc.)
        PaymentStatus payStatus = order.getPaymentStatus();
        if (payStatus != PaymentStatus.PENDING && payStatus != PaymentStatus.FAILED) {
            throw new InvalidOrderException("Cannot process payment for order with payment status: " + payStatus);
        }

        // Verify order status
        OrderStatus status = order.getOrderStatus();
        if (status != OrderStatus.CREATED && status != OrderStatus.VALIDATED && status != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderException("Cannot process payment for order in " + status + " state.");
        }

        // Verify amount
        if (amount.compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount (" + amount + ") does not match order total amount (" + order.getTotalAmount() + ").");
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            DatabaseConnection.setThreadConnection(conn);
            JdbcTransactionManager.begin(conn);

            // 1. Save OrderPayment
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(orderId);
            payment.setTransactionReference("TXN-" + System.currentTimeMillis() + "-" + (100 + new Random().nextInt(900)));
            payment.setAmount(amount);
            payment.setPaymentMode(paymentMode);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setStatus(PaymentTransactionStatus.SUCCESS);
            long paymentId = orderPaymentDao.save(payment);
            payment.setPaymentId(paymentId);

            // 2. Update Order paymentStatus
            order.setPaymentStatus(PaymentStatus.SUCCESS);
            telecomOrderDao.update(order);

            // 3. Reserve Inventory
            inventoryService.reserveInventoryForOrder(orderId);

            // 4. Update Order orderStatus -> INVENTORY_RESERVED
            order.setOrderStatus(OrderStatus.INVENTORY_RESERVED);
            telecomOrderDao.update(order);

            // 5. Audit Log
            auditService.logAction(session.getUserId(), "PAYMENT_AND_RESERVE", "Payment of " + amount + " processed for order ID " + orderId);

            JdbcTransactionManager.commit(conn);
            return payment;

        } catch (Exception ex) {
            if (conn != null) {
                try {
                    JdbcTransactionManager.rollback(conn);
                } catch (Exception ignored) { }
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException("Failed to complete payment transaction.", ex);
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
    public List<OrderPayment> getPaymentsForOrder(UserSession session, Long orderId) throws AccessDeniedException {
        orderService.getOrderById(session, orderId); // verifies view access
        return orderPaymentDao.findByOrderId(orderId);
    }
}
