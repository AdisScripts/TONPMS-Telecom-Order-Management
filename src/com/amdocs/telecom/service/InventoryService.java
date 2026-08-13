package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.InventoryUnavailableException;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.security.UserSession;
import java.util.List;

public interface InventoryService {
    InventoryItem addInventoryItem(UserSession session, InventoryItem item) throws AccessDeniedException;
    InventoryItem updateInventoryItem(UserSession session, Long inventoryId, String itemCode, InventoryItemType itemType, String warehouse) throws AccessDeniedException;
    void updateInventoryStatus(UserSession session, Long inventoryId, InventoryStatus newStatus) throws AccessDeniedException;
    InventoryItem getInventoryItemById(Long inventoryId);
    InventoryItem getInventoryItemByCode(String itemCode);
    List<InventoryItem> getInventoryItemsByStatus(InventoryStatus status);
    List<InventoryItem> getAllInventoryItems(UserSession session) throws AccessDeniedException;
    void reserveInventoryForOrder(Long orderId) throws InventoryUnavailableException;
    InventoryItemType determineRequiredInventoryType(String productType, String productName, String orderType);
}
