package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.TelecomOrder;
import java.util.List;
import java.util.Optional;
public interface TelecomOrderDao extends CrudDao<TelecomOrder> {
    Optional<TelecomOrder> findByOrderNumber(String orderNumber);
    List<TelecomOrder> findByCustomerId(Long customerId);
}
