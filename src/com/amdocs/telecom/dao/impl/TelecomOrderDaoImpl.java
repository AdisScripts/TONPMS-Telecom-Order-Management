package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.OrderType;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.TelecomOrder;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class TelecomOrderDaoImpl extends AbstractJdbcDao implements TelecomOrderDao {
    private static final String COLUMNS="order_id,order_number,customer_id,order_date,order_type,total_amount,payment_status,order_status,requested_activation_date,created_at,updated_at";
    public long save(final TelecomOrder o){long id=insert("INSERT INTO telecom_order (order_number,customer_id,order_date,order_type,total_amount,payment_status,order_status,requested_activation_date) VALUES (?,?,?,?,?,?,?,?)",s->{s.setString(1,o.getOrderNumber());s.setLong(2,o.getCustomerId());s.setTimestamp(3,Timestamp.valueOf(o.getOrderDate()));s.setString(4,o.getOrderType().name());s.setBigDecimal(5,o.getTotalAmount());s.setString(6,o.getPaymentStatus().name());s.setString(7,o.getOrderStatus().name());if(o.getRequestedActivationDate()==null)s.setNull(8,java.sql.Types.DATE);else s.setDate(8,Date.valueOf(o.getRequestedActivationDate()));});o.setOrderId(id);return id;}
    public Optional<TelecomOrder> findById(Long id){return queryOne("SELECT "+COLUMNS+" FROM telecom_order WHERE order_id=?",s->s.setLong(1,id),this::mapRow);}
    public Optional<TelecomOrder> findByOrderNumber(String n){return queryOne("SELECT "+COLUMNS+" FROM telecom_order WHERE order_number=?",s->s.setString(1,n),this::mapRow);}
    public List<TelecomOrder> findByCustomerId(Long id){return queryList("SELECT "+COLUMNS+" FROM telecom_order WHERE customer_id=? ORDER BY order_date DESC",s->s.setLong(1,id),this::mapRow);}
    public List<TelecomOrder> findAll(){return queryList("SELECT "+COLUMNS+" FROM telecom_order ORDER BY order_id",s->{},this::mapRow);}
    public boolean update(final TelecomOrder o){return executeUpdate("UPDATE telecom_order SET order_number=?,customer_id=?,order_date=?,order_type=?,total_amount=?,payment_status=?,order_status=?,requested_activation_date=? WHERE order_id=?",s->{s.setString(1,o.getOrderNumber());s.setLong(2,o.getCustomerId());s.setTimestamp(3,Timestamp.valueOf(o.getOrderDate()));s.setString(4,o.getOrderType().name());s.setBigDecimal(5,o.getTotalAmount());s.setString(6,o.getPaymentStatus().name());s.setString(7,o.getOrderStatus().name());if(o.getRequestedActivationDate()==null)s.setNull(8,java.sql.Types.DATE);else s.setDate(8,Date.valueOf(o.getRequestedActivationDate()));s.setLong(9,o.getOrderId());});}
    public boolean delete(Long id){return executeUpdate("DELETE FROM telecom_order WHERE order_id=?",s->s.setLong(1,id));}
    private TelecomOrder mapRow(ResultSet rs)throws SQLException{TelecomOrder o=new TelecomOrder();o.setOrderId(rs.getLong("order_id"));o.setOrderNumber(rs.getString("order_number"));o.setCustomerId(rs.getLong("customer_id"));o.setOrderDate(localDateTime(rs,"order_date"));o.setOrderType(OrderType.valueOf(rs.getString("order_type")));o.setTotalAmount(rs.getBigDecimal("total_amount"));o.setPaymentStatus(PaymentStatus.valueOf(rs.getString("payment_status")));o.setOrderStatus(OrderStatus.valueOf(rs.getString("order_status")));o.setRequestedActivationDate(localDate(rs,"requested_activation_date"));o.setCreatedAt(localDateTime(rs,"created_at"));o.setUpdatedAt(localDateTime(rs,"updated_at"));return o;}
}
