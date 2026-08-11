package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class ProvisioningRequestDaoImpl extends AbstractJdbcDao implements ProvisioningRequestDao {
 private static final String C="provisioning_id,order_id,service_id,provisioning_type,network_element,requested_date,completed_date,status,error_message,engineer_id";
 public long save(final ProvisioningRequest r){long id=insert("INSERT INTO provisioning_request (order_id,service_id,provisioning_type,network_element,requested_date,completed_date,status,error_message,engineer_id) VALUES (?,?,?,?,?,?,?,?,?)",s->bind(s,r));r.setProvisioningId(id);return id;}
 public Optional<ProvisioningRequest> findById(Long id){return queryOne("SELECT "+C+" FROM provisioning_request WHERE provisioning_id=?",s->s.setLong(1,id),this::map);}
 public List<ProvisioningRequest> findByOrderId(Long id){return queryList("SELECT "+C+" FROM provisioning_request WHERE order_id=? ORDER BY provisioning_id",s->s.setLong(1,id),this::map);}
 public List<ProvisioningRequest> findAll(){return queryList("SELECT "+C+" FROM provisioning_request ORDER BY provisioning_id",s->{},this::map);}
 public boolean update(final ProvisioningRequest r){return executeUpdate("UPDATE provisioning_request SET order_id=?,service_id=?,provisioning_type=?,network_element=?,requested_date=?,completed_date=?,status=?,error_message=?,engineer_id=? WHERE provisioning_id=?",s->{bind(s,r);s.setLong(10,r.getProvisioningId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM provisioning_request WHERE provisioning_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,ProvisioningRequest r)throws SQLException{s.setLong(1,r.getOrderId());s.setString(2,r.getServiceId());s.setString(3,r.getProvisioningType().getDatabaseValue());s.setString(4,r.getNetworkElement());setTimestamp(s,5,r.getRequestedDate());setTimestamp(s,6,r.getCompletedDate());s.setString(7,r.getStatus().name());s.setString(8,r.getErrorMessage());if(r.getEngineerId()==null)s.setNull(9,Types.BIGINT);else s.setLong(9,r.getEngineerId());}
 private void setTimestamp(PreparedStatement s,int i,java.time.LocalDateTime v)throws SQLException{if(v==null)s.setNull(i,Types.TIMESTAMP);else s.setTimestamp(i,Timestamp.valueOf(v));}
 private ProvisioningRequest map(ResultSet rs)throws SQLException{ProvisioningRequest r=new ProvisioningRequest();r.setProvisioningId(rs.getLong("provisioning_id"));r.setOrderId(rs.getLong("order_id"));r.setServiceId(rs.getString("service_id"));r.setProvisioningType(ProvisioningType.fromDatabaseValue(rs.getString("provisioning_type")));r.setNetworkElement(rs.getString("network_element"));r.setRequestedDate(localDateTime(rs,"requested_date"));r.setCompletedDate(localDateTime(rs,"completed_date"));r.setStatus(ProvisioningStatus.valueOf(rs.getString("status")));r.setErrorMessage(rs.getString("error_message"));long e=rs.getLong("engineer_id");r.setEngineerId(rs.wasNull()?null:e);return r;}
}
