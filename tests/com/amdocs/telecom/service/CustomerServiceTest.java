package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.CustomerDao;
import com.amdocs.telecom.exception.AccessDeniedException;
import com.amdocs.telecom.exception.CustomerNotEligibleException;
import com.amdocs.telecom.model.Customer;
import com.amdocs.telecom.model.CustomerAccountStatus;
import com.amdocs.telecom.model.CustomerType;
import com.amdocs.telecom.model.IdentityStatus;
import com.amdocs.telecom.model.RoleCode;
import com.amdocs.telecom.security.UserSession;
import com.amdocs.telecom.service.impl.CustomerServiceImpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CustomerServiceTest {
    private CustomerServiceTest() { }

    public static void main(String[] args) {
        System.out.println("Running CustomerServiceTest...");
        testProfileOperations();
        testEligibilityRules();
        testAuthorizationRules();
        System.out.println("PASS: CustomerServiceTest completed successfully.");
    }

    private static void testProfileOperations() {
        MockCustomerDao dao = new MockCustomerDao();
        CustomerService service = new CustomerServiceImpl(dao);

        Customer c = new Customer("CUST-100", "John Doe", "john@example.com", "9876543210", CustomerType.INDIVIDUAL);
        c.setAddress("123 Main St");
        c.setCity("Mumbai");
        c.setAccountStatus(CustomerAccountStatus.ACTIVE);
        c.setIdentityStatus(IdentityStatus.VERIFIED);
        c.setRegistrationDate(LocalDate.now());
        long id = dao.save(c);

        Customer found = service.getCustomerProfile(id);
        require(found.getCustomerName().equals("John Doe"), "Customer profile name mismatch");

        Customer foundNum = service.getCustomerByNumber("CUST-100");
        require(foundNum.getCustomerId().equals(id), "Customer profile number lookup mismatch");

        Customer foundEmail = service.getCustomerByEmail("john@example.com");
        require(foundEmail.getCustomerId().equals(id), "Customer profile email lookup mismatch");

        // Profile update by self
        UserSession selfSession = createSession(1L, id, RoleCode.CUSTOMER);
        c.setCity("Pune");
        service.updateCustomerProfile(selfSession, c);
        require(service.getCustomerProfile(id).getCity().equals("Pune"), "Customer city update failed");
    }

    private static void testEligibilityRules() {
        MockCustomerDao dao = new MockCustomerDao();
        CustomerService service = new CustomerServiceImpl(dao);

        // 1. ACTIVE + VERIFIED = Eligible
        Customer c1 = createCustomer("C-1", CustomerAccountStatus.ACTIVE, IdentityStatus.VERIFIED);
        long id1 = dao.save(c1);
        service.checkCustomerEligibility(id1); // Should not throw

        // 2. ACTIVE + PENDING = Eligible
        Customer c2 = createCustomer("C-2", CustomerAccountStatus.ACTIVE, IdentityStatus.PENDING);
        long id2 = dao.save(c2);
        service.checkCustomerEligibility(id2); // Should not throw

        // 3. ACTIVE + REJECTED = Ineligible
        Customer c3 = createCustomer("C-3", CustomerAccountStatus.ACTIVE, IdentityStatus.REJECTED);
        long id3 = dao.save(c3);
        try {
            service.checkCustomerEligibility(id3);
            require(false, "Should fail for REJECTED identity status");
        } catch (CustomerNotEligibleException ex) {
            require(ex.getMessage().contains("REJECTED"), "Expected REJECTED message");
        }

        // 4. SUSPENDED = Ineligible
        Customer c4 = createCustomer("C-4", CustomerAccountStatus.SUSPENDED, IdentityStatus.VERIFIED);
        long id4 = dao.save(c4);
        try {
            service.checkCustomerEligibility(id4);
            require(false, "Should fail for SUSPENDED account status");
        } catch (CustomerNotEligibleException ex) {
            require(ex.getMessage().contains("SUSPENDED"), "Expected SUSPENDED message");
        }

        // 5. CLOSED = Ineligible
        Customer c5 = createCustomer("C-5", CustomerAccountStatus.CLOSED, IdentityStatus.VERIFIED);
        long id5 = dao.save(c5);
        try {
            service.checkCustomerEligibility(id5);
            require(false, "Should fail for CLOSED account status");
        } catch (CustomerNotEligibleException ex) {
            require(ex.getMessage().contains("CLOSED"), "Expected CLOSED message");
        }
    }

    private static void testAuthorizationRules() {
        MockCustomerDao dao = new MockCustomerDao();
        CustomerService service = new CustomerServiceImpl(dao);

        Customer c1 = createCustomer("C-1", CustomerAccountStatus.ACTIVE, IdentityStatus.VERIFIED);
        long id1 = dao.save(c1);

        Customer c2 = createCustomer("C-2", CustomerAccountStatus.ACTIVE, IdentityStatus.VERIFIED);
        long id2 = dao.save(c2);

        UserSession user1Session = createSession(101L, id1, RoleCode.CUSTOMER);
        UserSession adminSession = createSession(999L, null, RoleCode.ORDER_ADMINISTRATOR);

        // User1 trying to update User2 profile -> AccessDeniedException
        try {
            c2.setCity("Nagpur");
            service.updateCustomerProfile(user1Session, c2);
            require(false, "User1 should not update User2 profile");
        } catch (AccessDeniedException ex) {
            // expected
        }

        // Admin updating User2 account status -> OK
        service.updateAccountStatus(adminSession, id2, CustomerAccountStatus.SUSPENDED);
        require(dao.findById(id2).get().getAccountStatus() == CustomerAccountStatus.SUSPENDED, "Admin account status update failed");

        // User1 trying to update account status -> AccessDeniedException
        try {
            service.updateAccountStatus(user1Session, id2, CustomerAccountStatus.ACTIVE);
            require(false, "User1 should not update account status");
        } catch (AccessDeniedException ex) {
            // expected
        }
    }

    private static Customer createCustomer(String number, CustomerAccountStatus accountStatus, IdentityStatus identityStatus) {
        Customer c = new Customer(number, "Name " + number, number + "@test.com", "9000000000", CustomerType.INDIVIDUAL);
        c.setAccountStatus(accountStatus);
        c.setIdentityStatus(identityStatus);
        c.setRegistrationDate(LocalDate.now());
        return c;
    }

    private static UserSession createSession(Long userId, Long customerId, RoleCode role) {
        Set<RoleCode> roles = new HashSet<>();
        roles.add(role);
        Customer c = null;
        if (customerId != null) {
            c = new Customer();
            c.setCustomerId(customerId);
        }
        return new UserSession(userId, "user" + userId, c, null, roles);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static class MockCustomerDao implements CustomerDao {
        private final Map<Long, Customer> map = new HashMap<>();
        private long idSequence = 1;

        @Override
        public long save(Customer customer) {
            long id = idSequence++;
            customer.setCustomerId(id);
            map.put(id, customer);
            return id;
        }

        @Override
        public Optional<Customer> findById(Long id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public Optional<Customer> findByCustomerNumber(String customerNumber) {
            return map.values().stream().filter(c -> customerNumber.equals(c.getCustomerNumber())).findFirst();
        }

        @Override
        public Optional<Customer> findByEmail(String email) {
            return map.values().stream().filter(c -> email.equals(c.getEmail())).findFirst();
        }

        @Override
        public List<Customer> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public boolean update(Customer customer) {
            if (map.containsKey(customer.getCustomerId())) {
                map.put(customer.getCustomerId(), customer);
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
