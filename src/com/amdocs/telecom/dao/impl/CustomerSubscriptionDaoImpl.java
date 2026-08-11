package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.CustomerSubscriptionDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class CustomerSubscriptionDaoImpl extends AbstractJdbcDao implements CustomerSubscriptionDao {
 private static final String C="subscription_id,customer_id,order_id,service_id,service_type,activation_date,termination_date,status";
 public long save(final CustomerSubscription v){long id=insert("INSERT INTO customer_subscription (customer_id,order_id,service_id,service_type,activation_date,termination_date,status) VALUES (?,?,?,?,?,?,?)",s->bind(s,v));v.setSubscriptionId(id);return id;}
 public Optional<CustomerSubscription> findById(Long id){return queryOne("SELECT "+C+" FROM customer_subscription WHERE subscription_id=?",s->s.setLong(1,id),this::map);}
 public Optional<CustomerSubscription> findByServiceId(String id){return queryOne("SELECT "+C+" FROM customer_subscription WHERE service_id=?",s->s.setString(1,id),this::map);}
 public List<CustomerSubscription> findByCustomerId(Long id){return queryList("SELECT "+C+" FROM customer_subscription WHERE customer_id=? ORDER BY subscription_id",s->s.setLong(1,id),this::map);}
 public List<CustomerSubscription> findAll(){return queryList("SELECT "+C+" FROM customer_subscription ORDER BY subscription_id",s->{},this::map);}
 public boolean update(final CustomerSubscription v){return executeUpdate("UPDATE customer_subscription SET customer_id=?,order_id=?,service_id=?,service_type=?,activation_date=?,termination_date=?,status=? WHERE subscription_id=?",s->{bind(s,v);s.setLong(8,v.getSubscriptionId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM customer_subscription WHERE subscription_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,CustomerSubscription v)throws SQLException{s.setLong(1,v.getCustomerId());s.setLong(2,v.getOrderId());s.setString(3,v.getServiceId());s.setString(4,v.getServiceType());if(v.getActivationDate()==null)s.setNull(5,Types.TIMESTAMP);else s.setTimestamp(5,Timestamp.valueOf(v.getActivationDate()));if(v.getTerminationDate()==null)s.setNull(6,Types.TIMESTAMP);else s.setTimestamp(6,Timestamp.valueOf(v.getTerminationDate()));s.setString(7,v.getStatus().name());}
 private CustomerSubscription map(ResultSet rs)throws SQLException{CustomerSubscription v=new CustomerSubscription();v.setSubscriptionId(rs.getLong("subscription_id"));v.setCustomerId(rs.getLong("customer_id"));v.setOrderId(rs.getLong("order_id"));v.setServiceId(rs.getString("service_id"));v.setServiceType(rs.getString("service_type"));v.setActivationDate(localDateTime(rs,"activation_date"));v.setTerminationDate(localDateTime(rs,"termination_date"));v.setStatus(SubscriptionStatus.valueOf(rs.getString("status")));return v;}
}
