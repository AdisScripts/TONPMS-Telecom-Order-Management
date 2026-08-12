# Implementation Plan - Phase 10: Application Console CLI & Controller Integration Layer

## 1. Baseline & Overview

### Completed Baseline (Phases 1–9)
The project baseline is committed at **`6170ea5` — Phase 9 complete: Reporting Analytics and Data Export**.
- **Phase 1**: Core requirements, business scenarios, and 4 role definitions.
- **Phase 2**: Relational database schema with 16 normalized tables.
- **Phase 3**: Domain model classes, enums, DTOs, and exception hierarchy.
- **Phase 4**: Pure JDBC infrastructure (`DatabaseConnection`, `JdbcTransactionManager`) and DAO layer for all 16 tables.
- **Phase 5**: Authentication, CAPTCHA, OTP, Login History, Account Locking, and Authorization services.
- **Phase 6**: Customer profile, Product catalog, and Order workflows (with strategy-pattern pricing).
- **Phase 7**: Transactional domain services (Payment, Inventory, Provisioning, Activation, Notification, Audit).
- **Phase 8**: Multithreading, background worker processors, and scheduled report generators (`SchedulerManager`).
- **Phase 9**: Reporting, analytics, and standard Core Java CSV/TXT data export service (`ReportService`).

### Phase 10 Goals
Phase 10 builds the final runnable Console CLI application layer, assembling all completed Phase 1–9 services into a clean, role-driven, interactive user experience (`MainApplication` & `Controllers`) as specified in Case Study Sections 2, 15, and 16.

---

## 2. Phase 10 Exact Requirements & Scope

1. **Main Application Entry Point (`MainApplication.java`)**:
   - Instantiates DAO & Service dependencies via standard Core Java dependency injection.
   - Initializes and starts `SchedulerManager` background processors on startup.
   - Drives the top-level Main Menu loop.
   - Implements a shutdown hook to invoke `SchedulerManager.shutdownAll()` upon exit.

2. **Role-Based Controller Hierarchy**:
   - **`AuthenticationController`**: Manages Customer Login, Registration (with CAPTCHA/OTP), Administrator Login, Engineer Login, Forgot Password, and Password Reset.
   - **`CustomerController`**: Manages Customer Dashboard (View Profile, Browse Products, Create Order, View My Orders, Track Order, Make Payment, View Active Services, Cancel Order, Notifications).
   - **`AdminController`**: Manages Order Administrator Dashboard (Customer Management, Product Management, Order Management, Provisioning Management, Payment Management, Failed Orders, Audit Logs, Scheduler Control).
   - **`InventoryController`**: Manages Inventory Administrator Dashboard (Stock view, low inventory alerts, warehouse stock breakdown).
   - **`ProvisioningController`**: Manages Provisioning Engineer Dashboard (View assigned tasks, update task status to `SUCCESS`/`FAILED`, view workload).
   - **`ReportController`**: Manages Reports & Analytics menu and CSV/TXT export file prompts.

3. **Strict Separation of Concerns**:
   - Controllers handle user input parsing, basic display formatting, and menu navigation.
   - Controllers **MUST NOT** duplicate business rules, authorization logic, pricing strategies, transaction management, or direct SQL execution.

---

## 3. CLI & Menu Architecture

```
                       +------------------------+
                       |    MainApplication     |
                       +------------------------+
                                   |
                +------------------+------------------+
                |                                     |
                v                                     v
    +-----------------------+             +-----------------------+
    | AuthenticationContr.  |             |   SchedulerManager    |
    +-----------------------+             +-----------------------+
                |
  +-------------+-------------+-----------------------+
  |                           |                       |
  v                           v                       v
+-------------------+   +--------------------+  +-----------------------+
| CustomerController|   |  AdminController   |  | InventoryController   |
+-------------------+   +--------------------+  +-----------------------+
                              |                       |
                              v                       v
                        +-----------------------------------------------+
                        | ProvisioningController  &  ReportController   |
                        +-----------------------------------------------+
```

---

## 4. Menu Hierarchy Details

### Main Menu (Login & Registration Screen - Case Study Section 2):
```
====================================================
       TELECOM ORDER & PROVISIONING SYSTEM
====================================================
1. Customer Login
2. Customer Registration
3. Order Administrator Login
4. Provisioning Engineer Login
5. Inventory Administrator Login
6. Forgot Password
7. Exit
```

### Customer Dashboard Menu (Case Study Section 15):
```
----------------------------------------------------
               CUSTOMER DASHBOARD
----------------------------------------------------
1. View Profile
2. Browse Products
3. Create Order
4. View My Orders
5. Track Order
6. Make Payment
7. View Active Services
8. Cancel Order
9. Notifications
10. Logout
```

### Order Administrator Dashboard Menu (Case Study Section 16):
```
----------------------------------------------------
           ORDER ADMINISTRATOR DASHBOARD
----------------------------------------------------
1. Customer Management
2. Product Management
3. Order Management
4. Inventory Management
5. Provisioning Management
6. Payment Management
7. Failed Orders
8. Audit Logs
9. Reports & Analytics
10. Background Scheduler Control
11. Logout
```

### Inventory Administrator Dashboard Menu:
```
----------------------------------------------------
         INVENTORY ADMINISTRATOR DASHBOARD
----------------------------------------------------
1. View Available Stock
2. View Low Inventory Stock Alerts
3. View Stock by Warehouse
4. Logout
```

### Provisioning Engineer Dashboard Menu:
```
----------------------------------------------------
          PROVISIONING ENGINEER DASHBOARD
----------------------------------------------------
1. View My Active Provisioning Tasks
2. Complete Provisioning Request (SUCCESS / FAILED)
3. View My Workload & Specialization
4. Logout
```

---

## 5. Security & Session Lifecycle

- Upon successful authentication, `AuthenticationService` returns a `UserSession`.
- Sub-controllers receive the active `UserSession` and pass it to service API invocations.
- Logout invalidates the active `UserSession` and returns execution to the Main Menu loop.

---

## 6. Application Startup & Graceful Shutdown

- **Startup**:
  1. Initialize all DAOs (`TelecomOrderDaoImpl`, `CustomerDaoImpl`, `TelecomProductDaoImpl`, etc.).
  2. Initialize all Services (`AuthenticationServiceImpl`, `CustomerServiceImpl`, `OrderServiceImpl`, `PaymentServiceImpl`, `InventoryServiceImpl`, `ProvisioningServiceImpl`, `ActivationServiceImpl`, `NotificationServiceImpl`, `AuditServiceImpl`, `ReportServiceImpl`).
  3. Initialize and start `SchedulerManager` (`schedulerManager.startAll()`).
  4. Launch Main Menu loop.
- **Shutdown**:
  1. Call `schedulerManager.shutdownAll()`.
  2. Verify all background thread pools terminate cleanly within 5 seconds.
  3. Display graceful exit message.

---

## 7. File Plan

### New Files Created (8 files)

#### Package: `com.amdocs.telecom.controller`
1. [`src/com/amdocs/telecom/controller/AuthenticationController.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/controller/AuthenticationController.java) [NEW]
   - Controller handling login, registration, OTP, CAPTCHA, and password recovery.
2. [`src/com/amdocs/telecom/controller/CustomerController.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/controller/CustomerController.java) [NEW]
   - Controller handling Customer Dashboard actions.
3. [`src/com/amdocs/telecom/controller/AdminController.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/controller/AdminController.java) [NEW]
   - Controller handling Order Administrator Dashboard actions.
4. [`src/com/amdocs/telecom/controller/InventoryController.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/controller/InventoryController.java) [NEW]
   - Controller handling Inventory Administrator Dashboard actions.
5. [`src/com/amdocs/telecom/controller/ProvisioningController.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/controller/ProvisioningController.java) [NEW]
   - Controller handling Provisioning Engineer Dashboard actions.
6. [`src/com/amdocs/telecom/controller/ReportController.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/controller/ReportController.java) [NEW]
   - Controller handling Reports & Analytics display and CSV/TXT file exports.

#### Package: `com.amdocs.telecom.main`
7. [`src/com/amdocs/telecom/main/MainApplication.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/main/MainApplication.java) [NEW]
   - Main entry point containing `public static void main(String[] args)` and dependency wiring.

#### Test Package: `com.amdocs.telecom.main`
8. [`tests/com/amdocs/telecom/main/MainApplicationTest.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/tests/com/amdocs/telecom/main/MainApplicationTest.java) [NEW]
   - Integration test driving application startup, main menu rendering, customer registration path, invalid CAPTCHA input handling, return to main menu, application exit, and clean `SchedulerManager` shutdown.

### Modified Files (2 existing files)
1. [`src/com/amdocs/telecom/service/AuthenticationService.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/service/AuthenticationService.java) [MODIFY]
2. [`src/com/amdocs/telecom/service/impl/AuthenticationServiceImpl.java`](file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/service/impl/AuthenticationServiceImpl.java) [MODIFY]

*Reason for Modification*: Added two service-layer helper methods:
- `generateOtpForUsername(String username, OtpPurpose purpose)`
- `verifyOtpForUsername(String username, OtpPurpose purpose, String otpInput)`

These methods are used by `AuthenticationController` for registration OTP verification and password-reset OTP verification, avoiding direct DAO access from the controller layer.

*Explicit Notes*:
- No other Phase 1–9 source files were modified.
- No database schema files were modified.

---

## 8. Verification & Test Plan

### Automated Tests
1. **Compilation**: `javac --release 8 -cp "lib/*" -d out ...`
2. **Phase 10 Main Application Test (`MainApplicationTest`)**:
   - Test application startup, main menu rendering, customer registration path, intentionally invalid CAPTCHA input, registration failure handling, return to main menu, application exit, and clean `SchedulerManager` background shutdown.
3. **Phase 5 Security Regression Tests**: `PasswordUtilsTest`, `CaptchaServiceTest`, `OtpServiceTest`, `AuthenticationServiceTest`.
4. **Phase 6 Workflow Regression Tests**: `CustomerServiceTest`, `ProductServiceTest`, `OrderServiceTest`.
5. **Phase 7 Transaction Regression Tests**: `InventoryServiceTest`, `PaymentServiceTest`, `ProvisioningServiceTest`, `ActivationServiceTest`.
6. **Phase 8 Scheduler Regression Tests**: `NotificationProcessorTest`, `ProvisioningProcessorTest`, `OrderProcessorTest`, `InventoryMonitorTest`, `SchedulerManagerTest`.
7. **Phase 9 Reporting Regression Tests**: `ReportServiceTest`.
8. **Integration Smoke Test**: `DaoIntegrationSmokeTest`.
9. **Git Verification**: `git diff --check` and `git status`.

---

## 9. Open Questions & Decisions Requiring Approval

1. **Interactive Console Input Testing**:
   - `MainApplicationTest` will use simulated `ByteArrayInputStream` to drive console inputs non-interactively in automated test runs.
2. **Scheduler Auto-Start**:
   - `SchedulerManager` will automatically start upon application launch to allow background order processing, low inventory monitoring, and metric report generation during user sessions.
