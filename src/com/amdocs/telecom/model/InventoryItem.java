package com.amdocs.telecom.model;

import java.time.LocalDateTime;

public class InventoryItem {
    private Long inventoryId;
    private String itemCode;
    private InventoryItemType itemType;
    private String serialNumber;
    private String warehouse;
    private String location;
    private InventoryStatus status;
    private Long assignedOrderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private TelecomOrder assignedOrder;

    public InventoryItem() { }

    public InventoryItem(String itemCode, InventoryItemType itemType, String warehouse) {
        this.itemCode = itemCode;
        this.itemType = itemType;
        this.warehouse = warehouse;
    }

    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public InventoryItemType getItemType() { return itemType; }
    public void setItemType(InventoryItemType itemType) { this.itemType = itemType; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getWarehouse() { return warehouse; }
    public void setWarehouse(String warehouse) { this.warehouse = warehouse; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public InventoryStatus getStatus() { return status; }
    public void setStatus(InventoryStatus status) { this.status = status; }
    public Long getAssignedOrderId() { return assignedOrderId; }
    public void setAssignedOrderId(Long assignedOrderId) { this.assignedOrderId = assignedOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public TelecomOrder getAssignedOrder() { return assignedOrder; }
    public void setAssignedOrder(TelecomOrder assignedOrder) { this.assignedOrder = assignedOrder; }

    @Override
    public String toString() {
        return "InventoryItem{inventoryId=" + inventoryId + ", itemCode='" + itemCode
                + "', itemType=" + itemType + ", status=" + status + "}";
    }
}
