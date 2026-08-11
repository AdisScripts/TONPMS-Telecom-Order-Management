package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.LoginHistoryDao;
import com.amdocs.telecom.model.LoginHistory;
import java.sql.*;
import java.util.*;
public class LoginHistoryDaoImpl extends AbstractJdbcDao implements LoginHistoryDao {
 private static final String C="login_history_id,user_id,username_attempted,attempted_at,success,ip_address,failure_reason";
 public long save(final LoginHistory h){long id=insert("INSERT INTO login_history (user_id,username_attempted,attempted_at,success,ip_address,failure_reason) VALUES (?,?,?,?,?,?)",s->bind(s,h));h.setLoginHistoryId(id);return id;}
 public Optional<LoginHistory> findById(Long id){return queryOne("SELECT "+C+" FROM login_history WHERE login_history_id=?",s->s.setLong(1,id),this::map);}
 public List<LoginHistory> findByUsernameAttempted(String u){return queryList("SELECT "+C+" FROM login_history WHERE username_attempted=? ORDER BY attempted_at DESC",s->s.setString(1,u),this::map);}
 public List<LoginHistory> findAll(){return queryList("SELECT "+C+" FROM login_history ORDER BY login_history_id",s->{},this::map);}
 public boolean update(final LoginHistory h){return executeUpdate("UPDATE login_history SET user_id=?,username_attempted=?,attempted_at=?,success=?,ip_address=?,failure_reason=? WHERE login_history_id=?",s->{bind(s,h);s.setLong(7,h.getLoginHistoryId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM login_history WHERE login_history_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,LoginHistory h)throws SQLException{if(h.getUserId()==null)s.setNull(1,Types.BIGINT);else s.setLong(1,h.getUserId());s.setString(2,h.getUsernameAttempted());if(h.getAttemptedAt()==null)s.setNull(3,Types.TIMESTAMP);else s.setTimestamp(3,Timestamp.valueOf(h.getAttemptedAt()));s.setBoolean(4,h.getSuccess());s.setString(5,h.getIpAddress());s.setString(6,h.getFailureReason());}
 private LoginHistory map(ResultSet rs)throws SQLException{LoginHistory h=new LoginHistory();h.setLoginHistoryId(rs.getLong("login_history_id"));long u=rs.getLong("user_id");h.setUserId(rs.wasNull()?null:u);h.setUsernameAttempted(rs.getString("username_attempted"));h.setAttemptedAt(localDateTime(rs,"attempted_at"));h.setSuccess(rs.getBoolean("success"));h.setIpAddress(rs.getString("ip_address"));h.setFailureReason(rs.getString("failure_reason"));return h;}
}
