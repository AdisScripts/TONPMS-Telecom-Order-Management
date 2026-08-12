package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.InvalidOrderException;
import com.amdocs.telecom.exception.InventoryUnavailableException;
import com.amdocs.telecom.model.OrderPayment;
import com.amdocs.telecom.model.PaymentMode;
import com.amdocs.telecom.security.UserSession;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {
    OrderPayment processPayment(UserSession session, Long orderId, BigDecimal amount, PaymentMode paymentMode)
            throws AccessDeniedException, InvalidOrderException, InventoryUnavailableException;
    List<OrderPayment> getPaymentsForOrder(UserSession session, Long orderId) throws AccessDeniedException;
}
