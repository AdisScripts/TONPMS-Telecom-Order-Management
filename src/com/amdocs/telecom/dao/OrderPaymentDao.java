package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.OrderPayment;
import java.util.List;
import java.util.Optional;
public interface OrderPaymentDao extends CrudDao<OrderPayment> {
    Optional<OrderPayment> findByTransactionReference(String transactionReference);
    List<OrderPayment> findByOrderId(Long orderId);
}
