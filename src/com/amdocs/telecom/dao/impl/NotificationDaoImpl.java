package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.NotificationDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class NotificationDaoImpl extends AbstractJdbcDao implements NotificationDao {
 private static final String C="notification_id,customer_id,recipient_user_id,notification_type,message,status,created_at,sent_at";
 public long save(final Notification n){long id=insert("INSERT INTO notification (customer_id,recipient_user_id,notification_type,message,status,sent_at) VALUES (?,?,?,?,?,?)",s->bind(s,n));n.setNotificationId(id);return id;}
 public Optional<Notification> findById(Long id){return queryOne("SELECT "+C+" FROM notification WHERE notification_id=?",s->s.setLong(1,id),this::map);}
 public List<Notification> findByCustomerId(Long id){return queryList("SELECT "+C+" FROM notification WHERE customer_id=? ORDER BY notification_id",s->s.setLong(1,id),this::map);}
 public List<Notification> findByRecipientUserId(Long id){return queryList("SELECT "+C+" FROM notification WHERE recipient_user_id=? ORDER BY notification_id",s->s.setLong(1,id),this::map);}
 public List<Notification> findAll(){return queryList("SELECT "+C+" FROM notification ORDER BY notification_id",s->{},this::map);}
 public boolean update(final Notification n){return executeUpdate("UPDATE notification SET customer_id=?,recipient_user_id=?,notification_type=?,message=?,status=?,sent_at=? WHERE notification_id=?",s->{bind(s,n);s.setLong(7,n.getNotificationId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM notification WHERE notification_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,Notification n)throws SQLException{if(n.getCustomerId()==null)s.setNull(1,Types.BIGINT);else s.setLong(1,n.getCustomerId());if(n.getRecipientUserId()==null)s.setNull(2,Types.BIGINT);else s.setLong(2,n.getRecipientUserId());s.setString(3,n.getNotificationType());s.setString(4,n.getMessage());s.setString(5,n.getStatus().name());if(n.getSentAt()==null)s.setNull(6,Types.TIMESTAMP);else s.setTimestamp(6,Timestamp.valueOf(n.getSentAt()));}
 private Notification map(ResultSet rs)throws SQLException{Notification n=new Notification();n.setNotificationId(rs.getLong("notification_id"));long c=rs.getLong("customer_id");n.setCustomerId(rs.wasNull()?null:c);long u=rs.getLong("recipient_user_id");n.setRecipientUserId(rs.wasNull()?null:u);n.setNotificationType(rs.getString("notification_type"));n.setMessage(rs.getString("message"));n.setStatus(NotificationStatus.valueOf(rs.getString("status")));n.setCreatedAt(localDateTime(rs,"created_at"));n.setSentAt(localDateTime(rs,"sent_at"));return n;}
}
