package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProductUnavailableException;
import com.amdocs.telecom.model.ProductStatus;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import java.util.List;

public interface ProductService {
    TelecomProduct createProduct(UserSession session, TelecomProduct product) throws AccessDeniedException;
    TelecomProduct updateProduct(UserSession session, TelecomProduct product) throws AccessDeniedException;
    void updateProductStatus(UserSession session, Long productId, ProductStatus newStatus) throws AccessDeniedException;
    TelecomProduct getProductById(Long productId);
    TelecomProduct getProductByCode(String productCode);
    List<TelecomProduct> getAllActiveProducts();
    List<TelecomProduct> getAllProducts(UserSession session) throws AccessDeniedException;
    void checkProductAvailability(Long productId) throws ProductUnavailableException;
}
