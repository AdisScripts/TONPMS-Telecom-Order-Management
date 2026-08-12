package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.InventoryUnavailableException;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.AuthorizationService;
import com.amdocs.telecom.security.AuthorizationServiceImpl;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.InventoryService;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class InventoryServiceImpl implements InventoryService {
    private final InventoryItemDao inventoryItemDao;
    private final TelecomOrderDao telecomOrderDao;
    private final OrderItemDao orderItemDao;
    private final TelecomProductDao telecomProductDao;
    private final AuthorizationService authorizationService;

    public InventoryServiceImpl(InventoryItemDao inventoryItemDao, TelecomOrderDao telecomOrderDao,
                                OrderItemDao orderItemDao, TelecomProductDao telecomProductDao) {
        this.inventoryItemDao = Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.telecomProductDao = Objects.requireNonNull(telecomProductDao, "telecomProductDao must not be null");
        this.authorizationService = new AuthorizationServiceImpl();
    }

    public InventoryServiceImpl(InventoryItemDao inventoryItemDao, TelecomOrderDao telecomOrderDao,
                                OrderItemDao orderItemDao, TelecomProductDao telecomProductDao,
                                AuthorizationService authorizationService) {
        this.inventoryItemDao = Objects.requireNonNull(inventoryItemDao, "inventoryItemDao must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.orderItemDao = Objects.requireNonNull(orderItemDao, "orderItemDao must not be null");
        this.telecomProductDao = Objects.requireNonNull(telecomProductDao, "telecomProductDao must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
    }

    @Override
    public InventoryItem addInventoryItem(UserSession session, InventoryItem item) throws AccessDeniedException {
        checkInventoryAdminAccess(session);
        if (item == null || item.getItemCode() == null || item.getItemType() == null) {
            throw new IllegalArgumentException("InventoryItem, itemCode, and itemType must not be null.");
        }
        if (item.getStatus() == null) {
            item.setStatus(InventoryStatus.AVAILABLE);
        }
        long id = inventoryItemDao.save(item);
        item.setInventoryId(id);
        return item;
    }

    @Override
    public void updateInventoryStatus(UserSession session, Long inventoryId, InventoryStatus newStatus) throws AccessDeniedException {
        checkInventoryAdminAccess(session);
        if (inventoryId == null || newStatus == null) {
            throw new IllegalArgumentException("inventoryId and newStatus must not be null.");
        }
        InventoryItem item = getInventoryItemById(inventoryId);
        item.setStatus(newStatus);
        boolean updated = inventoryItemDao.update(item);
        if (!updated) {
            throw new IllegalStateException("Failed to update inventory status.");
        }
    }

    @Override
    public InventoryItem getInventoryItemById(Long inventoryId) {
        if (inventoryId == null) {
            throw new IllegalArgumentException("inventoryId must not be null.");
        }
        Optional<InventoryItem> optional = inventoryItemDao.findById(inventoryId);
        return optional.orElseThrow(() -> new IllegalArgumentException("Inventory item not found with ID: " + inventoryId));
    }

    @Override
    public InventoryItem getInventoryItemByCode(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("itemCode must not be null or empty.");
        }
        Optional<InventoryItem> optional = inventoryItemDao.findByItemCode(itemCode);
        return optional.orElseThrow(() -> new IllegalArgumentException("Inventory item not found with code: " + itemCode));
    }

    @Override
    public List<InventoryItem> getInventoryItemsByStatus(InventoryStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null.");
        }
        return inventoryItemDao.findByStatus(status.name());
    }

    @Override
    public List<InventoryItem> getAllInventoryItems(UserSession session) throws AccessDeniedException {
        checkInventoryAdminAccess(session);
        return inventoryItemDao.findAll();
    }

    @Override
    public synchronized void reserveInventoryForOrder(Long orderId) throws InventoryUnavailableException {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null.");
        }
        Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
        TelecomOrder order = orderOpt.orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        List<OrderItem> orderItems = orderItemDao.findByOrderId(orderId);
        List<InventoryItem> availableItems = inventoryItemDao.findByStatus(InventoryStatus.AVAILABLE.name());
        Set<Long> reservedItemIdsInThisTxn = new HashSet<>();

        for (OrderItem orderItem : orderItems) {
            Optional<TelecomProduct> prodOpt = telecomProductDao.findById(orderItem.getProductId());
            if (!prodOpt.isPresent()) {
                continue;
            }
            TelecomProduct product = prodOpt.get();
            InventoryItemType requiredType = determineRequiredInventoryType(
                    product.getProductType(), product.getProductName(), order.getOrderType().name());

            if (requiredType == null) {
                continue; // Pure digital add-on requiring no hardware
            }

            int requiredQty = orderItem.getQuantity();
            int matchedCount = 0;

            for (InventoryItem availableItem : availableItems) {
                if (reservedItemIdsInThisTxn.contains(availableItem.getInventoryId())) {
                    continue;
                }
                if (availableItem.getItemType() == requiredType && availableItem.getStatus() == InventoryStatus.AVAILABLE) {
                    availableItem.setStatus(InventoryStatus.RESERVED);
                    availableItem.setAssignedOrderId(orderId);
                    boolean updated = inventoryItemDao.update(availableItem);
                    if (!updated) {
                        throw new InventoryUnavailableException("Failed to update inventory item " + availableItem.getItemCode() + " status.");
                    }
                    reservedItemIdsInThisTxn.add(availableItem.getInventoryId());
                    matchedCount++;
                    if (matchedCount == requiredQty) {
                        break;
                    }
                }
            }

            if (matchedCount < requiredQty) {
                throw new InventoryUnavailableException("Insufficient inventory available for type: " + requiredType + " (Required: " + requiredQty + ", Found: " + matchedCount + ").");
            }
        }
    }

    @Override
    public InventoryItemType determineRequiredInventoryType(String productType, String productName, String orderType) {
        if ("ESIM_ACTIVATION".equalsIgnoreCase(orderType) || (productName != null && productName.toLowerCase().contains("esim"))) {
            return InventoryItemType.ESIM;
        }
        if ("SIM_REPLACEMENT".equalsIgnoreCase(orderType)) {
            return InventoryItemType.SIM;
        }
        if ("ENTERPRISE".equalsIgnoreCase(productType)) {
            return InventoryItemType.NETWORK_DEVICE;
        }
        if ("BROADBAND".equalsIgnoreCase(productType) || "BROADBAND".equalsIgnoreCase(orderType)) {
            return InventoryItemType.ONT;
        }
        if ("MOBILE_PLAN".equalsIgnoreCase(productType) || "NEW_CONNECTION".equalsIgnoreCase(orderType)) {
            return InventoryItemType.SIM;
        }
        return null;
    }

    private void checkInventoryAdminAccess(UserSession session) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        boolean isInvAdmin = session.hasRole(RoleCode.INVENTORY_ADMINISTRATOR);
        boolean isOrderAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        if (!isInvAdmin && !isOrderAdmin) {
            throw new AccessDeniedException("Inventory administration requires INVENTORY_ADMINISTRATOR or ORDER_ADMINISTRATOR role.");
        }
    }
}
