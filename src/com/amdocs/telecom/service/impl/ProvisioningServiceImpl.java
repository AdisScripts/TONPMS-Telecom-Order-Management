package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.dao.ProvisioningEngineerDao;
import com.amdocs.telecom.dao.ProvisioningRequestDao;
import com.amdocs.telecom.dao.TelecomOrderDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.ProvisioningException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.EngineerAvailability;
import com.amdocs.telecom.model.OrderStatus;
import com.amdocs.telecom.model.PaymentStatus;
import com.amdocs.telecom.model.ProvisioningEngineer;
import com.amdocs.telecom.model.ProvisioningRequest;
import com.amdocs.telecom.model.ProvisioningStatus;
import com.amdocs.telecom.model.ProvisioningType;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.model.TelecomOrder;
import com.amdocs.telecom.security.AuthorizationService;
import com.amdocs.telecom.security.AuthorizationServiceImpl;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.AuditService;
import com.amdocs.telecom.service.ProvisioningService;
import com.amdocs.telecom.util.DatabaseConnection;
import com.amdocs.telecom.util.JdbcTransactionManager;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProvisioningServiceImpl implements ProvisioningService {
    private final ProvisioningRequestDao provisioningRequestDao;
    private final ProvisioningEngineerDao provisioningEngineerDao;
    private final TelecomOrderDao telecomOrderDao;
    private final CustomerDao customerDao;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public ProvisioningServiceImpl(ProvisioningRequestDao provisioningRequestDao,
                                  ProvisioningEngineerDao provisioningEngineerDao,
                                  TelecomOrderDao telecomOrderDao, CustomerDao customerDao,
                                  AuditService auditService) {
        this.provisioningRequestDao = Objects.requireNonNull(provisioningRequestDao, "provisioningRequestDao must not be null");
        this.provisioningEngineerDao = Objects.requireNonNull(provisioningEngineerDao, "provisioningEngineerDao must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.customerDao = Objects.requireNonNull(customerDao, "customerDao must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.authorizationService = new AuthorizationServiceImpl();
    }

    public ProvisioningServiceImpl(ProvisioningRequestDao provisioningRequestDao,
                                  ProvisioningEngineerDao provisioningEngineerDao,
                                  TelecomOrderDao telecomOrderDao, CustomerDao customerDao,
                                  AuditService auditService, AuthorizationService authorizationService) {
        this.provisioningRequestDao = Objects.requireNonNull(provisioningRequestDao, "provisioningRequestDao must not be null");
        this.provisioningEngineerDao = Objects.requireNonNull(provisioningEngineerDao, "provisioningEngineerDao must not be null");
        this.telecomOrderDao = Objects.requireNonNull(telecomOrderDao, "telecomOrderDao must not be null");
        this.customerDao = Objects.requireNonNull(customerDao, "customerDao must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService must not be null");
    }

    @Override
    public ProvisioningRequest createProvisioningRequest(UserSession session, Long orderId, ProvisioningType provisioningType)
            throws AccessDeniedException, ProvisioningException {
        authorizationService.checkAccess(session, RoleCode.ORDER_ADMINISTRATOR);
        if (orderId == null || provisioningType == null) {
            throw new IllegalArgumentException("orderId and provisioningType must not be null.");
        }

        Optional<TelecomOrder> orderOpt = telecomOrderDao.findById(orderId);
        TelecomOrder order = orderOpt.orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        if (order.getPaymentStatus() != PaymentStatus.SUCCESS || order.getOrderStatus() != OrderStatus.INVENTORY_RESERVED) {
            throw new ProvisioningException("Order ID " + orderId + " is not ready for provisioning. Must be paid and inventory reserved.");
        }

        Optional<Customer> custOpt = customerDao.findById(order.getCustomerId());
        String city = custOpt.map(Customer::getCity).orElse(null);

        Optional<ProvisioningEngineer> recEngOpt = recommendEngineer(provisioningType, city);

        ProvisioningRequest request = new ProvisioningRequest();
        request.setOrderId(orderId);
        request.setServiceId("SRV-" + orderId);
        request.setProvisioningType(provisioningType);
        request.setRequestedDate(LocalDateTime.now());

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            DatabaseConnection.setThreadConnection(conn);
            JdbcTransactionManager.begin(conn);

            if (recEngOpt.isPresent()) {
                ProvisioningEngineer eng = recEngOpt.get();
                request.setEngineerId(eng.getEngineerId());
                request.setStatus(ProvisioningStatus.IN_PROGRESS);
                eng.setActiveTasks(eng.getActiveTasks() + 1);
                provisioningEngineerDao.update(eng);
            } else {
                request.setStatus(ProvisioningStatus.PENDING);
            }

            long reqId = provisioningRequestDao.save(request);
            request.setProvisioningId(reqId);

            order.setOrderStatus(OrderStatus.PROVISIONING);
            telecomOrderDao.update(order);

            auditService.logAction(session.getUserId(), "PROVISIONING_REQUEST_CREATED",
                    "Created provisioning request ID " + reqId + " for order ID " + orderId);

            JdbcTransactionManager.commit(conn);
            return request;

        } catch (Exception ex) {
            if (conn != null) {
                try {
                    JdbcTransactionManager.rollback(conn);
                } catch (Exception ignored) { }
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException("Failed to complete provisioning request creation transaction.", ex);
        } finally {
            DatabaseConnection.clearThreadConnection();
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public Optional<ProvisioningEngineer> recommendEngineer(ProvisioningType provisioningType, String customerCity) {
        if (provisioningType == null) {
            return Optional.empty();
        }
        List<ProvisioningEngineer> allEngineers = provisioningEngineerDao.findAll();

        Comparator<ProvisioningEngineer> rankComparator = Comparator
                .comparingInt(ProvisioningEngineer::getActiveTasks)
                .thenComparing(Comparator.comparingInt(ProvisioningEngineer::getExperienceYears).reversed())
                .thenComparingLong(ProvisioningEngineer::getEngineerId);

        // Try region match first using Streams + Lambdas + Comparators
        if (customerCity != null && !customerCity.trim().isEmpty()) {
            Optional<ProvisioningEngineer> regionMatch = allEngineers.stream()
                    .filter(e -> e.getAvailability() == EngineerAvailability.AVAILABLE)
                    .filter(e -> provisioningType.name().equalsIgnoreCase(e.getSpecialization()))
                    .filter(e -> customerCity.equalsIgnoreCase(e.getRegion()))
                    .min(rankComparator);

            if (regionMatch.isPresent()) {
                return regionMatch;
            }
        }

        // Fallback to any available engineer with matching specialization
        return allEngineers.stream()
                .filter(e -> e.getAvailability() == EngineerAvailability.AVAILABLE)
                .filter(e -> provisioningType.name().equalsIgnoreCase(e.getSpecialization()))
                .min(rankComparator);
    }

    @Override
    public void updateProvisioningStatus(UserSession session, Long provisioningId, ProvisioningStatus status, String errorMessage)
            throws AccessDeniedException, ProvisioningException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        boolean isEng = session.hasRole(RoleCode.PROVISIONING_ENGINEER);
        boolean isAdmin = session.hasRole(RoleCode.ORDER_ADMINISTRATOR);
        if (!isEng && !isAdmin) {
            throw new AccessDeniedException("Updating provisioning status requires PROVISIONING_ENGINEER or ORDER_ADMINISTRATOR role.");
        }
        if (provisioningId == null || status == null) {
            throw new IllegalArgumentException("provisioningId and status must not be null.");
        }

        Optional<ProvisioningRequest> opt = provisioningRequestDao.findById(provisioningId);
        ProvisioningRequest req = opt.orElseThrow(() -> new IllegalArgumentException("Provisioning request not found with ID: " + provisioningId));

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            DatabaseConnection.setThreadConnection(conn);
            JdbcTransactionManager.begin(conn);

            req.setStatus(status);
            if (errorMessage != null) {
                req.setErrorMessage(errorMessage);
            }
            if (status == ProvisioningStatus.SUCCESS || status == ProvisioningStatus.FAILED) {
                req.setCompletedDate(LocalDateTime.now());
                if (req.getEngineerId() != null) {
                    Optional<ProvisioningEngineer> engOpt = provisioningEngineerDao.findById(req.getEngineerId());
                    if (engOpt.isPresent()) {
                        ProvisioningEngineer eng = engOpt.get();
                        if (eng.getActiveTasks() > 0) {
                            eng.setActiveTasks(eng.getActiveTasks() - 1);
                            provisioningEngineerDao.update(eng);
                        }
                    }
                }
            }

            boolean updated = provisioningRequestDao.update(req);
            if (!updated) {
                throw new IllegalStateException("Failed to update provisioning request.");
            }

            auditService.logAction(session.getUserId(), "PROVISIONING_STATUS_UPDATE",
                    "Updated provisioning request ID " + provisioningId + " to status " + status);

            JdbcTransactionManager.commit(conn);

        } catch (Exception ex) {
            if (conn != null) {
                try {
                    JdbcTransactionManager.rollback(conn);
                } catch (Exception ignored) { }
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException("Failed to complete provisioning status update transaction.", ex);
        } finally {
            DatabaseConnection.clearThreadConnection();
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public ProvisioningRequest getProvisioningRequestById(UserSession session, Long provisioningId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (provisioningId == null) {
            throw new IllegalArgumentException("provisioningId must not be null.");
        }
        Optional<ProvisioningRequest> opt = provisioningRequestDao.findById(provisioningId);
        return opt.orElseThrow(() -> new IllegalArgumentException("Provisioning request not found with ID: " + provisioningId));
    }

    @Override
    public List<ProvisioningRequest> getProvisioningRequestsByOrder(UserSession session, Long orderId) throws AccessDeniedException {
        if (session == null) {
            throw new AccessDeniedException("Active session required.");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null.");
        }
        return provisioningRequestDao.findByOrderId(orderId);
    }
}
