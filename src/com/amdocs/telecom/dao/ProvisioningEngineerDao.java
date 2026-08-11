package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.ProvisioningEngineer;
import java.util.Optional;
public interface ProvisioningEngineerDao extends CrudDao<ProvisioningEngineer> {
    Optional<ProvisioningEngineer> findByEmployeeCode(String employeeCode);
}
