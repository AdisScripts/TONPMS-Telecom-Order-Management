package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.AppUserDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class AppUserDaoImpl extends AbstractJdbcDao implements AppUserDao {
 private static final String C="user_id,username,password_hash,password_salt,customer_id,account_status,failed_attempts,locked_until,created_at,updated_at";
 public long save(final AppUser u){long id=insert("INSERT INTO app_user (username,password_hash,password_salt,customer_id,account_status,failed_attempts,locked_until) VALUES (?,?,?,?,?,?,?)",s->bind(s,u));u.setUserId(id);return id;}
 public Optional<AppUser> findById(Long id){return queryOne("SELECT "+C+" FROM app_user WHERE user_id=?",s->s.setLong(1,id),this::map);}
 public Optional<AppUser> findByUsername(String n){return queryOne("SELECT "+C+" FROM app_user WHERE username=?",s->s.setString(1,n),this::map);}
 public List<AppUser> findAll(){return queryList("SELECT "+C+" FROM app_user ORDER BY user_id",s->{},this::map);}
 public boolean update(final AppUser u){return executeUpdate("UPDATE app_user SET username=?,password_hash=?,password_salt=?,customer_id=?,account_status=?,failed_attempts=?,locked_until=? WHERE user_id=?",s->{bind(s,u);s.setLong(8,u.getUserId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM app_user WHERE user_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,AppUser u)throws SQLException{s.setString(1,u.getUsername());s.setString(2,u.getPasswordHash());s.setString(3,u.getPasswordSalt());if(u.getCustomerId()==null)s.setNull(4,Types.BIGINT);else s.setLong(4,u.getCustomerId());s.setString(5,u.getAccountStatus().name());s.setInt(6,u.getFailedAttempts());if(u.getLockedUntil()==null)s.setNull(7,Types.TIMESTAMP);else s.setTimestamp(7,Timestamp.valueOf(u.getLockedUntil()));}
 private AppUser map(ResultSet rs)throws SQLException{AppUser u=new AppUser();u.setUserId(rs.getLong("user_id"));u.setUsername(rs.getString("username"));u.setPasswordHash(rs.getString("password_hash"));u.setPasswordSalt(rs.getString("password_salt"));long c=rs.getLong("customer_id");u.setCustomerId(rs.wasNull()?null:c);u.setAccountStatus(UserAccountStatus.valueOf(rs.getString("account_status")));u.setFailedAttempts(rs.getInt("failed_attempts"));u.setLockedUntil(localDateTime(rs,"locked_until"));u.setCreatedAt(localDateTime(rs,"created_at"));u.setUpdatedAt(localDateTime(rs,"updated_at"));return u;}
}
