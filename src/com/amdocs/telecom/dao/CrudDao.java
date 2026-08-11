package com.amdocs.telecom.dao;

import java.util.List;
import java.util.Optional;

public interface CrudDao<T> {
    long save(T entity);
    Optional<T> findById(Long id);
    List<T> findAll();
    boolean update(T entity);
    boolean delete(Long id);
}
