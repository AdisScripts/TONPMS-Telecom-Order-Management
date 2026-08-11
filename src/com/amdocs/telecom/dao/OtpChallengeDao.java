package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.OtpChallenge;
import java.util.List;
public interface OtpChallengeDao extends CrudDao<OtpChallenge> {
    List<OtpChallenge> findByUserId(Long userId);
}
