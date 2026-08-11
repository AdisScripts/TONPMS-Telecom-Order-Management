package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.LoginHistory;
import java.util.List;
public interface LoginHistoryDao extends CrudDao<LoginHistory> {
    List<LoginHistory> findByUsernameAttempted(String usernameAttempted);
}
