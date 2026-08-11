package com.amdocs.telecom.dao;
import com.amdocs.telecom.model.TelecomProduct;
import java.util.List;
import java.util.Optional;
public interface TelecomProductDao extends CrudDao<TelecomProduct> {
    Optional<TelecomProduct> findByProductCode(String productCode);
    List<TelecomProduct> findActiveProducts();
}
