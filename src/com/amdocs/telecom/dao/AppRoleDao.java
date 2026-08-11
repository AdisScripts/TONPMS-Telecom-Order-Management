package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.AppRole;
import java.util.Optional;
public interface AppRoleDao extends CrudDao<AppRole> {
    Optional<AppRole> findByRoleCode(String roleCode);
}
