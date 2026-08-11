package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.InventoryItem;
import java.util.List;
import java.util.Optional;
public interface InventoryItemDao extends CrudDao<InventoryItem> {
    Optional<InventoryItem> findByItemCode(String itemCode);
    List<InventoryItem> findByStatus(String status);
}
