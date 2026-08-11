package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.model.EngineerAvailability;
import com.amdocs.telecom.model.ProvisioningEngineer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProvisioningEngineerDaoImpl extends AbstractJdbcDao implements ProvisioningEngineerDao {
    private static final String COLUMNS="engineer_id,employee_code,engineer_name,specialization,region,availability,active_tasks,experience_years,user_id";
    public long save(final ProvisioningEngineer e){long id=insert("INSERT INTO provisioning_engineer (employee_code,engineer_name,specialization,region,availability,active_tasks,experience_years,user_id) VALUES (?,?,?,?,?,?,?,?)",s->bind(s,e));e.setEngineerId(id);return id;}
    public Optional<ProvisioningEngineer> findById(Long id){return queryOne("SELECT "+COLUMNS+" FROM provisioning_engineer WHERE engineer_id=?",s->s.setLong(1,id),this::mapRow);}
    public Optional<ProvisioningEngineer> findByEmployeeCode(String code){return queryOne("SELECT "+COLUMNS+" FROM provisioning_engineer WHERE employee_code=?",s->s.setString(1,code),this::mapRow);}
    public List<ProvisioningEngineer> findAll(){return queryList("SELECT "+COLUMNS+" FROM provisioning_engineer ORDER BY engineer_id",s->{},this::mapRow);}
    public boolean update(final ProvisioningEngineer e){return executeUpdate("UPDATE provisioning_engineer SET employee_code=?,engineer_name=?,specialization=?,region=?,availability=?,active_tasks=?,experience_years=?,user_id=? WHERE engineer_id=?",s->{bind(s,e);s.setLong(9,e.getEngineerId());});}
    public boolean delete(Long id){return executeUpdate("DELETE FROM provisioning_engineer WHERE engineer_id=?",s->s.setLong(1,id));}
    private void bind(PreparedStatement s,ProvisioningEngineer e)throws SQLException{s.setString(1,e.getEmployeeCode());s.setString(2,e.getEngineerName());s.setString(3,e.getSpecialization());s.setString(4,e.getRegion());s.setString(5,e.getAvailability().name());s.setInt(6,e.getActiveTasks());s.setInt(7,e.getExperienceYears());if(e.getUserId()==null)s.setNull(8,java.sql.Types.BIGINT);else s.setLong(8,e.getUserId());}
    private ProvisioningEngineer mapRow(ResultSet rs)throws SQLException{ProvisioningEngineer e=new ProvisioningEngineer();e.setEngineerId(rs.getLong("engineer_id"));e.setEmployeeCode(rs.getString("employee_code"));e.setEngineerName(rs.getString("engineer_name"));e.setSpecialization(rs.getString("specialization"));e.setRegion(rs.getString("region"));e.setAvailability(EngineerAvailability.valueOf(rs.getString("availability")));e.setActiveTasks(rs.getInt("active_tasks"));e.setExperienceYears(rs.getInt("experience_years"));long userId=rs.getLong("user_id");e.setUserId(rs.wasNull()?null:userId);return e;}
}
