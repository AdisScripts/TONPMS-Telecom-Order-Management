package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.OrderPaymentDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class OrderPaymentDaoImpl extends AbstractJdbcDao implements OrderPaymentDao {
 private static final String C="payment_id,order_id,transaction_reference,amount,payment_mode,payment_date,status";
 public long save(final OrderPayment p){long id=insert("INSERT INTO order_payment (order_id,transaction_reference,amount,payment_mode,payment_date,status) VALUES (?,?,?,?,?,?)",s->bind(s,p));p.setPaymentId(id);return id;}
 public Optional<OrderPayment> findById(Long id){return queryOne("SELECT "+C+" FROM order_payment WHERE payment_id=?",s->s.setLong(1,id),this::map);}
 public Optional<OrderPayment> findByTransactionReference(String ref){return queryOne("SELECT "+C+" FROM order_payment WHERE transaction_reference=?",s->s.setString(1,ref),this::map);}
 public List<OrderPayment> findByOrderId(Long id){return queryList("SELECT "+C+" FROM order_payment WHERE order_id=? ORDER BY payment_id",s->s.setLong(1,id),this::map);}
 public List<OrderPayment> findAll(){return queryList("SELECT "+C+" FROM order_payment ORDER BY payment_id",s->{},this::map);}
 public boolean update(final OrderPayment p){return executeUpdate("UPDATE order_payment SET order_id=?,transaction_reference=?,amount=?,payment_mode=?,payment_date=?,status=? WHERE payment_id=?",s->{bind(s,p);s.setLong(7,p.getPaymentId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM order_payment WHERE payment_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,OrderPayment p)throws SQLException{s.setLong(1,p.getOrderId());s.setString(2,p.getTransactionReference());s.setBigDecimal(3,p.getAmount());s.setString(4,p.getPaymentMode().name());if(p.getPaymentDate()==null)s.setNull(5,Types.TIMESTAMP);else s.setTimestamp(5,Timestamp.valueOf(p.getPaymentDate()));s.setString(6,p.getStatus().name());}
 private OrderPayment map(ResultSet rs)throws SQLException{OrderPayment p=new OrderPayment();p.setPaymentId(rs.getLong("payment_id"));p.setOrderId(rs.getLong("order_id"));p.setTransactionReference(rs.getString("transaction_reference"));p.setAmount(rs.getBigDecimal("amount"));p.setPaymentMode(PaymentMode.valueOf(rs.getString("payment_mode")));p.setPaymentDate(localDateTime(rs,"payment_date"));p.setStatus(PaymentTransactionStatus.valueOf(rs.getString("status")));return p;}
}
