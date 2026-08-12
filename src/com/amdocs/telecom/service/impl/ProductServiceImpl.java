package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProductUnavailableException;
import com.amdocs.telecom.model.ProductStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.AuthorizationService;
import com.amdocs.telecom.security.AuthorizationServiceImpl;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProductServiceImpl implements ProductService {
    private final TelecomProductDao productDao;
    private final AuthorizationService authorizationService;

    public ProductServiceImpl(TelecomProductDao productDao) {
        this.productDao = Objects.requireNonNull(productDao, "productDao must not be null");
        this.authorizationService = new AuthorizationServiceImpl();
    }

    public ProductServiceImpl(TelecomProductDao productDao, AuthorizationService authorizationService) {
        this.productDao = Objects.requireNonNull(productDao, "productDao must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
    }

    @Override
    public TelecomProduct createProduct(UserSession session, TelecomProduct product) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        validateProduct(product);
        if (product.getStatus() == null) {
            product.setStatus(ProductStatus.ACTIVE);
        }
        long id = productDao.save(product);
        product.setProductId(id);
        return product;
    }

    @Override
    public TelecomProduct updateProduct(UserSession session, TelecomProduct product) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (product == null || product.getProductId() == null) {
            throw new IllegalArgumentException("Product and productId must not be null.");
        }
        validateProduct(product);
        getProductById(product.getProductId()); // ensures product exists
        boolean updated = productDao.update(product);
        if (!updated) {
            throw new IllegalStateException("Failed to update product.");
        }
        return product;
    }

    @Override
    public void updateProductStatus(UserSession session, Long productId, ProductStatus newStatus) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (productId == null || newStatus == null) {
            throw new IllegalArgumentException("productId and newStatus must not be null.");
        }
        TelecomProduct product = getProductById(productId);
        product.setStatus(newStatus);
        boolean updated = productDao.update(product);
        if (!updated) {
            throw new IllegalStateException("Failed to update product status.");
        }
    }

    @Override
    public TelecomProduct getProductById(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        Optional<TelecomProduct> optional = productDao.findById(productId);
        return optional.orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));
    }

    @Override
    public TelecomProduct getProductByCode(String productCode) {
        if (productCode == null || productCode.trim().isEmpty()) {
            throw new IllegalArgumentException("productCode must not be null or empty");
        }
        Optional<TelecomProduct> optional = productDao.findByProductCode(productCode);
        return optional.orElseThrow(() -> new IllegalArgumentException("Product not found with code: " + productCode));
    }

    @Override
    public List<TelecomProduct> getAllActiveProducts() {
        return productDao.findActiveProducts();
    }

    @Override
    public List<TelecomProduct> getAllProducts(UserSession session) throws AccessDeniedException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        return productDao.findAll();
    }

    @Override
    public void checkProductAvailability(Long productId) throws ProductUnavailableException {
        TelecomProduct product = getProductById(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductUnavailableException("Product '" + product.getProductName() + "' is unavailable (Status: " + product.getStatus() + ").");
        }
    }

    private void validateProduct(TelecomProduct product) {
        if (product == null) {
            throw new IllegalArgumentException("Product must not be null.");
        }
        if (product.getProductCode() == null || product.getProductCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Product code must not be null or empty.");
        }
        if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name must not be null or empty.");
        }
        if (product.getMonthlyPrice() == null || product.getMonthlyPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Monthly price must be non-negative.");
        }
    }
}
