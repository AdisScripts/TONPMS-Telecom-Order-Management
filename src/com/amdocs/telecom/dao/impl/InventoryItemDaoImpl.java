package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.InventoryItemDao;
import com.amdocs.telecom.model.InventoryItem;
import com.amdocs.telecom.model.InventoryItemType;
import com.amdocs.telecom.model.InventoryStatus;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class InventoryItemDaoImpl extends AbstractJdbcDao implements InventoryItemDao {
    private static final String COLUMNS="inventory_id,item_code,item_type,serial_number,warehouse,location,status,assigned_order_id,created_at,updated_at";
    public long save(final InventoryItem i){long id=insert("INSERT INTO inventory_item (item_code,item_type,serial_number,warehouse,location,status,assigned_order_id) VALUES (?,?,?,?,?,?,?)",s->bind(s,i));i.setInventoryId(id);return id;}
    public Optional<InventoryItem> findById(Long id){return queryOne("SELECT "+COLUMNS+" FROM inventory_item WHERE inventory_id=?",s->s.setLong(1,id),this::mapRow);}
    public Optional<InventoryItem> findByItemCode(String code){return queryOne("SELECT "+COLUMNS+" FROM inventory_item WHERE item_code=?",s->s.setString(1,code),this::mapRow);}
    public List<InventoryItem> findByStatus(String status){return queryList("SELECT "+COLUMNS+" FROM inventory_item WHERE status=? ORDER BY inventory_id",s->s.setString(1,status),this::mapRow);}
    public List<InventoryItem> findAll(){return queryList("SELECT "+COLUMNS+" FROM inventory_item ORDER BY inventory_id",s->{},this::mapRow);}
    public boolean update(final InventoryItem i){return executeUpdate("UPDATE inventory_item SET item_code=?,item_type=?,serial_number=?,warehouse=?,location=?,status=?,assigned_order_id=? WHERE inventory_id=?",s->{bind(s,i);s.setLong(8,i.getInventoryId());});}
    public boolean delete(Long id){return executeUpdate("DELETE FROM inventory_item WHERE inventory_id=?",s->s.setLong(1,id));}
    private void bind(PreparedStatement s,InventoryItem i)throws SQLException{s.setString(1,i.getItemCode());s.setString(2,i.getItemType().name());s.setString(3,i.getSerialNumber());s.setString(4,i.getWarehouse());s.setString(5,i.getLocation());s.setString(6,i.getStatus().name());if(i.getAssignedOrderId()==null)s.setNull(7,java.sql.Types.BIGINT);else s.setLong(7,i.getAssignedOrderId());}
    private InventoryItem mapRow(ResultSet rs)throws SQLException{InventoryItem i=new InventoryItem();i.setInventoryId(rs.getLong("inventory_id"));i.setItemCode(rs.getString("item_code"));i.setItemType(InventoryItemType.valueOf(rs.getString("item_type")));i.setSerialNumber(rs.getString("serial_number"));i.setWarehouse(rs.getString("warehouse"));i.setLocation(rs.getString("location"));i.setStatus(InventoryStatus.valueOf(rs.getString("status")));long orderId=rs.getLong("assigned_order_id");i.setAssignedOrderId(rs.wasNull()?null:orderId);i.setCreatedAt(localDateTime(rs,"created_at"));i.setUpdatedAt(localDateTime(rs,"updated_at"));return i;}
}
