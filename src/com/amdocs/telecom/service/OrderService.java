package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.CustomerNotEligibleException;
import com.amdocs.telecom.exception.DuplicateOrderException;
import com.amdocs.telecom.exception.InvalidOrderException;
import com.amdocs.telecom.exception.ProductUnavailableException;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.security.UserSession;
import java.time.LocalDate;
import java.util.List;

public interface OrderService {
    TelecomOrder createOrder(UserSession session, Long customerId, OrderType orderType, LocalDate requestedActivationDate, List<OrderItemRequest> itemRequests)
            throws AccessDeniedException, CustomerNotEligibleException, ProductUnavailableException, DuplicateOrderException, InvalidOrderException;

    TelecomOrder getOrderById(UserSession session, Long orderId) throws AccessDeniedException;
    TelecomOrder getOrderByNumber(UserSession session, String orderNumber) throws AccessDeniedException;
    List<TelecomOrder> getOrdersByCustomer(UserSession session, Long customerId) throws AccessDeniedException;
    List<TelecomOrder> getOrdersByStatus(UserSession session, OrderStatus status) throws AccessDeniedException;
    List<OrderItem> getOrderItems(UserSession session, Long orderId) throws AccessDeniedException;
    void updateOrderStatus(UserSession session, Long orderId, OrderStatus newStatus) throws AccessDeniedException;
    void cancelOrder(UserSession session, Long orderId) throws AccessDeniedException, InvalidOrderException;

    public static class OrderItemRequest {
        private final Long productId;
        private final int quantity;

        public OrderItemRequest(Long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public Long getProductId() { return productId; }
        public int getQuantity() { return quantity; }
    }
}
