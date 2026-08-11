package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.OrderItem;
import java.util.List;
public interface OrderItemDao extends CrudDao<OrderItem> {
    List<OrderItem> findByOrderId(Long orderId);
    int[] saveBatch(List<OrderItem> items);
}
