package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.AuditLogDao;
import com.amdocs.telecom.model.AuditLog;
import java.sql.*;
import java.util.*;
public class AuditLogDaoImpl extends AbstractJdbcDao implements AuditLogDao {
 private static final String C="audit_id,actor_user_id,entity_type,entity_id,action,details,created_at";
 public long save(final AuditLog a){long id=insert("INSERT INTO audit_log (actor_user_id,entity_type,entity_id,action,details) VALUES (?,?,?,?,?)",s->bind(s,a));a.setAuditId(id);return id;}
 public Optional<AuditLog> findById(Long id){return queryOne("SELECT "+C+" FROM audit_log WHERE audit_id=?",s->s.setLong(1,id),this::map);}
 public List<AuditLog> findByEntity(String type,Long id){return queryList("SELECT "+C+" FROM audit_log WHERE entity_type=? AND entity_id=? ORDER BY audit_id",s->{s.setString(1,type);s.setLong(2,id);},this::map);}
 public List<AuditLog> findAll(){return queryList("SELECT "+C+" FROM audit_log ORDER BY audit_id",s->{},this::map);}
 public boolean update(final AuditLog a){return executeUpdate("UPDATE audit_log SET actor_user_id=?,entity_type=?,entity_id=?,action=?,details=? WHERE audit_id=?",s->{bind(s,a);s.setLong(6,a.getAuditId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM audit_log WHERE audit_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,AuditLog a)throws SQLException{if(a.getActorUserId()==null)s.setNull(1,Types.BIGINT);else s.setLong(1,a.getActorUserId());s.setString(2,a.getEntityType());if(a.getEntityId()==null)s.setNull(3,Types.BIGINT);else s.setLong(3,a.getEntityId());s.setString(4,a.getAction());s.setString(5,a.getDetails());}
 private AuditLog map(ResultSet rs)throws SQLException{AuditLog a=new AuditLog();a.setAuditId(rs.getLong("audit_id"));long u=rs.getLong("actor_user_id");a.setActorUserId(rs.wasNull()?null:u);a.setEntityType(rs.getString("entity_type"));long e=rs.getLong("entity_id");a.setEntityId(rs.wasNull()?null:e);a.setAction(rs.getString("action"));a.setDetails(rs.getString("details"));a.setCreatedAt(localDateTime(rs,"created_at"));return a;}
}
