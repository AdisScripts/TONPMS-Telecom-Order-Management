package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dao.OtpChallengeDao;
import com.amdocs.telecom.model.*;
import java.sql.*;
import java.util.*;
public class OtpChallengeDaoImpl extends AbstractJdbcDao implements OtpChallengeDao {
 private static final String C="otp_id,user_id,purpose,otp_hash,expires_at,consumed_at,attempts";
 public long save(final OtpChallenge o){long id=insert("INSERT INTO otp_challenge (user_id,purpose,otp_hash,expires_at,consumed_at,attempts) VALUES (?,?,?,?,?,?)",s->bind(s,o));o.setOtpId(id);return id;}
 public Optional<OtpChallenge> findById(Long id){return queryOne("SELECT "+C+" FROM otp_challenge WHERE otp_id=?",s->s.setLong(1,id),this::map);}
 public List<OtpChallenge> findByUserId(Long id){return queryList("SELECT "+C+" FROM otp_challenge WHERE user_id=? ORDER BY otp_id",s->s.setLong(1,id),this::map);}
 public List<OtpChallenge> findAll(){return queryList("SELECT "+C+" FROM otp_challenge ORDER BY otp_id",s->{},this::map);}
 public boolean update(final OtpChallenge o){return executeUpdate("UPDATE otp_challenge SET user_id=?,purpose=?,otp_hash=?,expires_at=?,consumed_at=?,attempts=? WHERE otp_id=?",s->{bind(s,o);s.setLong(7,o.getOtpId());});}
 public boolean delete(Long id){return executeUpdate("DELETE FROM otp_challenge WHERE otp_id=?",s->s.setLong(1,id));}
 private void bind(PreparedStatement s,OtpChallenge o)throws SQLException{s.setLong(1,o.getUserId());s.setString(2,o.getPurpose().name());s.setString(3,o.getOtpHash());s.setTimestamp(4,Timestamp.valueOf(o.getExpiresAt()));if(o.getConsumedAt()==null)s.setNull(5,Types.TIMESTAMP);else s.setTimestamp(5,Timestamp.valueOf(o.getConsumedAt()));s.setInt(6,o.getAttempts());}
 private OtpChallenge map(ResultSet rs)throws SQLException{OtpChallenge o=new OtpChallenge();o.setOtpId(rs.getLong("otp_id"));o.setUserId(rs.getLong("user_id"));o.setPurpose(OtpPurpose.valueOf(rs.getString("purpose")));o.setOtpHash(rs.getString("otp_hash"));o.setExpiresAt(localDateTime(rs,"expires_at"));o.setConsumedAt(localDateTime(rs,"consumed_at"));o.setAttempts(rs.getInt("attempts"));return o;}
}
