package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.TelecomProductDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProductUnavailableException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.ProductStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomProduct;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.ProductServiceImpl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProductServiceTest {
    private ProductServiceTest() { }

    public static void main(String[] args) {
        System.out.println("Running ProductServiceTest...");
        testProductCrudAndCatalog();
        testProductAvailabilityCheck();
        testAuthorization();
        System.out.println("PASS: ProductServiceTest completed successfully.");
    }

    private static void testProductCrudAndCatalog() {
        MockTelecomProductDao dao = new MockTelecomProductDao();
        ProductService service = new ProductServiceImpl(dao);
        UserSession adminSession = createSession(1L, RoleCode.ORDER_ADMINISTRATOR);

        TelecomProduct p = new TelecomProduct("P100", "5G Plan", "MOBILE_PLAN", new BigDecimal("999.00"));
        p.setStatus(ProductStatus.ACTIVE);
        p.setActivationFee(BigDecimal.ZERO);
        p.setContractPeriod(12);

        TelecomProduct created = service.createProduct(adminSession, p);
        require(created.getProductId() != null, "Product creation failed");

        TelecomProduct found = service.getProductByCode("P100");
        require(found.getProductName().equals("5G Plan"), "Product code lookup failed");

        List<TelecomProduct> active = service.getAllActiveProducts();
        require(active.size() == 1, "Active products lookup count mismatch");

        // Update status to INACTIVE
        service.updateProductStatus(adminSession, created.getProductId(), ProductStatus.INACTIVE);
        require(service.getAllActiveProducts().isEmpty(), "Active products should be empty after setting INACTIVE");
    }

    private static void testProductAvailabilityCheck() {
        MockTelecomProductDao dao = new MockTelecomProductDao();
        ProductService service = new ProductServiceImpl(dao);
        UserSession adminSession = createSession(1L, RoleCode.ORDER_ADMINISTRATOR);

        TelecomProduct p1 = new TelecomProduct("P200", "Fiber 500", "BROADBAND", new BigDecimal("799.00"));
        p1.setStatus(ProductStatus.ACTIVE);
        long id1 = dao.save(p1);

        TelecomProduct p2 = new TelecomProduct("P201", "Old Plan", "BROADBAND", new BigDecimal("499.00"));
        p2.setStatus(ProductStatus.DISCONTINUED);
        long id2 = dao.save(p2);

        service.checkProductAvailability(id1); // Should pass

        try {
            service.checkProductAvailability(id2);
            require(false, "Discontinued product should throw ProductUnavailableException");
        } catch (ProductUnavailableException ex) {
            require(ex.getMessage().contains("DISCONTINUED"), "Expected DISCONTINUED message");
        }
    }

    private static void testAuthorization() {
        MockTelecomProductDao dao = new MockTelecomProductDao();
        ProductService service = new ProductServiceImpl(dao);
        UserSession customerSession = createSession(2L, RoleCode.CUSTOMER);

        TelecomProduct p = new TelecomProduct("P300", "Unauthorized Product", "MOBILE_PLAN", new BigDecimal("100.00"));

        try {
            service.createProduct(customerSession, p);
            require(false, "Customer should not create products");
        } catch (AccessDeniedException ex) {
            // expected
        }

        try {
            service.getAllProducts(customerSession);
            require(false, "Customer should not view all admin products");
        } catch (AccessDeniedException ex) {
            // expected
        }
    }

    private static UserSession createSession(Long userId, RoleCode role) {
        Set<RoleCode> roles = new HashSet<>();
        roles.add(role);
        Customer c = new Customer();
        c.setCustomerId(100L);
        return new UserSession(userId, "user" + userId, c, null, roles);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static class MockTelecomProductDao implements TelecomProductDao {
        private final Map<Long, TelecomProduct> map = new HashMap<>();
        private long idSequence = 1;

        @Override
        public long save(TelecomProduct p) {
            long id = idSequence++;
            p.setProductId(id);
            map.put(id, p);
            return id;
        }

        @Override
        public Optional<TelecomProduct> findById(Long id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public Optional<TelecomProduct> findByProductCode(String productCode) {
            return map.values().stream().filter(p -> productCode.equals(p.getProductCode())).findFirst();
        }

        @Override
        public List<TelecomProduct> findActiveProducts() {
            List<TelecomProduct> active = new ArrayList<>();
            for (TelecomProduct p : map.values()) {
                if (p.getStatus() == ProductStatus.ACTIVE) {
                    active.add(p);
                }
            }
            return active;
        }

        @Override
        public List<TelecomProduct> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public boolean update(TelecomProduct p) {
            if (map.containsKey(p.getProductId())) {
                map.put(p.getProductId(), p);
                return true;
            }
            return false;
        }

        @Override
        public boolean delete(Long id) {
            return map.remove(id) != null;
        }
    }
}
