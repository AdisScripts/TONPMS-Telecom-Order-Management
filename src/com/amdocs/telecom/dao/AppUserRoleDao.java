package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.AppUserRole;
import java.util.List;
import java.util.Optional;
public interface AppUserRoleDao {
    boolean save(AppUserRole entity);
    Optional<AppUserRole> findByUserIdAndRoleId(Long userId, Short roleId);
    List<AppUserRole> findByUserId(Long userId);
    boolean delete(Long userId, Short roleId);
}
