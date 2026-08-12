package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.CustomerSubscriptionDao;
import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProvisioningException;
import com.amdocs.telecom.model.CustomerSubscription;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.SubscriptionStatus;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ActivationService;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.NotificationService;
import com.amdocs.telecom.util.DatabaseConnection;
import com.amdocs.telecom.util.JdbcTransactionManager;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ActivationServiceImpl implements ActivationService {
    private final CustomerSubscriptionDao customerSubscriptionDao;
    private final TelecomOrderDao telecomOrderDao;
    private final OrderItemDao orderItemDao;
    private final TelecomProductDao telecomProductDao;
    private final InventoryItemDao inventoryItemDao;
    private final ProvisioningRequestDao provisioningRequestDao;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public ActivationServiceImpl(CustomerSubscriptionDao customerSubscriptionDao,
                                 TelecomOrderDao telecomOrderDao, OrderItemDao orderItemDao,
                                 TelecomProductDao telecomProductDao, InventoryItemDao inventoryItemDao,
                                 ProvisioningRequestDao provisioningRequestDao,
                                 NotificationService notificationService, AuditService auditService) {
        this.customerSubscriptionDao = Objects.requireNonNull(customerSubscriptionDao, "customerSubscriptionDao must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.telecomProductDao = Objects.requireNonNull(telecomProductDao, "telecomProductDao must not be null");
        this.inventoryItemDao = Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        this.provisioningRequestDao = Objects.requireNonNull(provisioningRequestDao, "provisioningRequestDao must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    @Override
    public void activateService(UserSession session, Long orderId) throws AccessDeniedException, ProvisioningException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        boolean isEng = session.hasRole(RoleCode.PROVISIONING_ENGINEER);
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        if (!isEng && !isAdmin) {
            throw new AccessDeniedException("Service activation requires PROVISIONING_ENGINEER or ORDER_ADMINISTRATOR role.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null.");
        }

        Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
        TelecomOrder order = orderOpt.orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PROVISIONING) {
            throw new InvalidOrderStatusException("Order ID " + orderId + " must be in PROVISIONING state to activate.");
        }

        List<ProvisioningRequest> reqs = provisioningRequestDao.findByOrderId(orderId);
        boolean hasSuccessReq = false;
        for (ProvisioningRequest req : reqs) {
            if (req.getStatus() == ProvisioningStatus.SUCCESS) {
                hasSuccessReq = true;
                break;
            }
        }
        if (!hasSuccessReq) {
            throw new ProvisioningException("Provisioning request for order ID " + orderId + " is not in SUCCESS state.");
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            DatabaseConnection.setThreadConnection(conn);
            JdbcTransactionManager.begin(conn);

            // 1. Create CustomerSubscription for each OrderItem using exact product.contractPeriod
            List<OrderItem> orderItems = orderItemDao.findByOrderId(orderId);
            LocalDateTime now = LocalDateTime.now();
            for (OrderItem item : orderItems) {
                Optional<TelecomProduct> prodOpt = telecomProductDao.findById(item.getProductId());
                if (!prodOpt.isPresent()) {
                    throw new ProvisioningException("TelecomProduct not found for product ID: " + item.getProductId());
                }
                TelecomProduct product = prodOpt.get();
                if (product.getContractPeriod() == null || product.getContractPeriod() <= 0) {
                    throw new ProvisioningException("Invalid contract period for product ID: " + item.getProductId());
                }
                int contractMonths = product.getContractPeriod();

                CustomerSubscription sub = new CustomerSubscription();
                sub.setCustomerId(order.getCustomerId());
                sub.setOrderId(orderId);
                sub.setServiceId("SUB-" + orderId + "-" + item.getProductId());
                sub.setServiceType(product.getProductType() != null ? product.getProductType() : "TELECOM_SERVICE");
                sub.setActivationDate(now);
                sub.setTerminationDate(now.plusMonths(contractMonths));
                sub.setStatus(SubscriptionStatus.ACTIVE);
                customerSubscriptionDao.save(sub);
            }

            // 2. Update InventoryItems assigned to orderId from RESERVED -> INSTALLED
            List<InventoryItem> allInventory = inventoryItemDao.findAll();
            for (InventoryItem inv : allInventory) {
                if (orderId.equals(inv.getAssignedOrderId()) && inv.getStatus() == InventoryStatus.RESERVED) {
                    inv.setStatus(InventoryStatus.INSTALLED);
                    inventoryItemDao.update(inv);
                }
            }

            // 3. Update Order status -> ACTIVATED
            order.setOrderStatus(OrderStatus.ACTIVATED);
            telecomOrderDao.update(order);

            // 4. Send Notification to customer
            notificationService.sendNotification(order.getCustomerId(),
                    "Your telecom service for order " + order.getOrderNumber() + " has been successfully activated!");

            // 5. Audit Log
            auditService.logAction(session.getUserId(), "SERVICE_ACTIVATION",
                    "Service activated for order ID " + orderId + " (" + order.getOrderNumber() + ")");

            JdbcTransactionManager.commit(conn);

        } catch (Exception ex) {
            if (conn != null) {
                try {
                    JdbcTransactionManager.rollback(conn);
                } catch (Exception ignored) { }
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException("Failed to complete service activation transaction.", ex);
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
    public void completeOrderLifecycle(UserSession session, Long orderId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        boolean isEng = session.hasRole(RoleCode.PROVISIONING_ENGINEER);
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        if (!isEng && !isAdmin) {
            throw new AccessDeniedException("Completing order lifecycle requires PROVISIONING_ENGINEER or ORDER_ADMINISTRATOR role.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null.");
        }

        Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
        TelecomOrder order = orderOpt.orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        if (order.getOrderStatus() != OrderStatus.ACTIVATED) {
            throw new IllegalStateException("Order ID " + orderId + " must be in ACTIVATED state to complete lifecycle.");
        }

        order.setOrderStatus(OrderStatus.COMPLETED);
        boolean updated = telecomOrderDao.update(order);
        if (!updated) {
            throw new IllegalStateException("Failed to update order status to COMPLETED.");
        }

        auditService.logAction(session.getUserId(), "ORDER_LIFECYCLE_COMPLETED",
                "Order lifecycle completed for order ID " + orderId + " (" + order.getOrderNumber() + ")");
    }

    @Override
    public List<CustomerSubscription> getCustomerSubscriptions(UserSession session, Long customerId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null.");
        }
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        boolean isSelf = session.getCustomer() != null && session.getCustomer().getCustomerId() != null && session.getCustomer().getCustomerId().equals(customerId);
        if (!isAdmin && !isSelf) {
            throw new AccessDeniedException("Customers can only view their own subscriptions.");
        }
        return customerSubscriptionDao.findByCustomerId(customerId);
    }

    private static class InvalidOrderStatusException extends RuntimeException {
        public InvalidOrderStatusException(String message) {
            super(message);
        }
    }
}
