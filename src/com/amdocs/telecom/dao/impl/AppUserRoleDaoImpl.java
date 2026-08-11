package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.AppUserRoleDao;
import com.amdocs.telecom.model.AppUserRole;
import java.sql.*;
import java.util.*;
public class AppUserRoleDaoImpl extends AbstractJdbcDao implements AppUserRoleDao {
 private static final String C="user_id,role_id,assigned_at";
 public boolean save(final AppUserRole r){return executeUpdate("INSERT INTO app_user_role (user_id,role_id,assigned_at) VALUES (?,?,?)",s->{s.setLong(1,r.getUserId());s.setShort(2,r.getRoleId());if(r.getAssignedAt()==null)s.setNull(3,Types.TIMESTAMP);else s.setTimestamp(3,Timestamp.valueOf(r.getAssignedAt()));});}
 public Optional<AppUserRole> findByUserIdAndRoleId(Long u,Short r){return queryOne("SELECT "+C+" FROM app_user_role WHERE user_id=? AND role_id=?",s->{s.setLong(1,u);s.setShort(2,r);},this::map);}
 public List<AppUserRole> findByUserId(Long id){return queryList("SELECT "+C+" FROM app_user_role WHERE user_id=? ORDER BY role_id",s->s.setLong(1,id),this::map);}
 public boolean delete(Long u,Short r){return executeUpdate("DELETE FROM app_user_role WHERE user_id=? AND role_id=?",s->{s.setLong(1,u);s.setShort(2,r);});}
 private AppUserRole map(ResultSet rs)throws SQLException{AppUserRole r=new AppUserRole();r.setUserId(rs.getLong("user_id"));r.setRoleId(rs.getShort("role_id"));r.setAssignedAt(localDateTime(rs,"assigned_at"));return r;}
}
