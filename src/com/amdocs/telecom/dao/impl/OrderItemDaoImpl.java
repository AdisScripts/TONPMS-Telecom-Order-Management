package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.OrderItemDao;
import com.amdocs.telecom.exception.DatabaseException;
import com.amdocs.telecom.model.OrderItem;
import com.amdocs.telecom.util.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class OrderItemDaoImpl extends AbstractJdbcDao implements OrderItemDao {
    private static final String COLUMNS="order_item_id,order_id,product_id,quantity,unit_price,discount,tax,total_amount";
    private static final String INSERT_SQL="INSERT INTO order_item (order_id,product_id,quantity,unit_price,discount,tax,total_amount) VALUES (?,?,?,?,?,?,?)";
    public long save(final OrderItem i){long id=insert(INSERT_SQL,s->bind(s,i));i.setOrderItemId(id);return id;}
    public int[] saveBatch(List<OrderItem> items){
        try(Connection c=DatabaseConnection.getConnection();PreparedStatement s=c.prepareStatement(INSERT_SQL,Statement.RETURN_GENERATED_KEYS)){
            for(OrderItem item:items){bind(s,item);s.addBatch();}
            int[] counts=s.executeBatch();
            try(ResultSet keys=s.getGeneratedKeys()){int index=0;while(keys.next()&&index<items.size()){items.get(index++).setOrderItemId(keys.getLong(1));}}
            return counts;
        }catch(SQLException ex){throw new DatabaseException("Batch order-item insert failed.",ex);}
    }
    public Optional<OrderItem> findById(Long id){return queryOne("SELECT "+COLUMNS+" FROM order_item WHERE order_item_id=?",s->s.setLong(1,id),this::mapRow);}
    public List<OrderItem> findByOrderId(Long id){return queryList("SELECT "+COLUMNS+" FROM order_item WHERE order_id=? ORDER BY order_item_id",s->s.setLong(1,id),this::mapRow);}
    public List<OrderItem> findAll(){return queryList("SELECT "+COLUMNS+" FROM order_item ORDER BY order_item_id",s->{},this::mapRow);}
    public boolean update(final OrderItem i){return executeUpdate("UPDATE order_item SET order_id=?,product_id=?,quantity=?,unit_price=?,discount=?,tax=?,total_amount=? WHERE order_item_id=?",s->{bind(s,i);s.setLong(8,i.getOrderItemId());});}
    public boolean delete(Long id){return executeUpdate("DELETE FROM order_item WHERE order_item_id=?",s->s.setLong(1,id));}
    private void bind(PreparedStatement s,OrderItem i)throws SQLException{s.setLong(1,i.getOrderId());s.setLong(2,i.getProductId());s.setInt(3,i.getQuantity());s.setBigDecimal(4,i.getUnitPrice());s.setBigDecimal(5,i.getDiscount());s.setBigDecimal(6,i.getTax());s.setBigDecimal(7,i.getTotalAmount());}
    private OrderItem mapRow(ResultSet rs)throws SQLException{OrderItem i=new OrderItem();i.setOrderItemId(rs.getLong("order_item_id"));i.setOrderId(rs.getLong("order_id"));i.setProductId(rs.getLong("product_id"));i.setQuantity(rs.getInt("quantity"));i.setUnitPrice(rs.getBigDecimal("unit_price"));i.setDiscount(rs.getBigDecimal("discount"));i.setTax(rs.getBigDecimal("tax"));i.setTotalAmount(rs.getBigDecimal("total_amount"));return i;}
}
