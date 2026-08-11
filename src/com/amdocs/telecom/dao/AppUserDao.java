package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.AppUser;
import java.util.Optional;
public interface AppUserDao extends CrudDao<AppUser> {
    Optional<AppUser> findByUsername(String username);
}
