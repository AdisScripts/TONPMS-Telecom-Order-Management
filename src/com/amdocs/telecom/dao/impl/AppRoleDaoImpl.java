package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.AppRoleDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class AppRoleDaoImpl extends AbstractJdbcDao implements AppRoleDao {
 private static final String C="role_id,role_code,role_name";
 public long save(final AppRole r){long id=insert("INSERT INTO app_role (role_code,role_name) VALUES (?,?)",s->{s.setString(1,r.getRoleCode().name());s.setString(2,r.getRoleName());});r.setRoleId((short)id);return id;}
 public Optional<AppRole> findById(Long id){return queryOne("SELECT "+C+" FROM app_role WHERE role_id=?",s->s.setLong(1,id),this::map);}
 public Optional<AppRole> findByRoleCode(String code){return queryOne("SELECT "+C+" FROM app_role WHERE role_code=?",s->s.setString(1,code),this::map);}
 public List<AppRole> findAll(){return queryList("SELECT "+C+" FROM app_role ORDER BY role_id",s->{},this::map);}
 public boolean update(final AppRole r){return executeUpdate("UPDATE app_role SET role_code=?,role_name=? WHERE role_id=?",s->{s.setString(1,r.getRoleCode().name());s.setString(2,r.getRoleName());s.setShort(3,r.getRoleId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM app_role WHERE role_id=?",s->s.setLong(1,id));}
 private AppRole map(ResultSet rs)throws SQLException{AppRole r=new AppRole();r.setRoleId(rs.getShort("role_id"));r.setRoleCode(RoleCode.valueOf(rs.getString("role_code")));r.setRoleName(rs.getString("role_name"));return r;}
}
