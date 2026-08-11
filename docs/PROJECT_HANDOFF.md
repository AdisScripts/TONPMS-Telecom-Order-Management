# TONPMS Project Handoff Document

## 1. Project Overview

Project Name: Telecom Order and Network Provisioning Management System (TONPMS)

This is an Amdocs pre-boarding capstone project.

The project is a console-based telecom order management and provisioning system.

The official case-study PDF is the primary source for business requirements.

The trainer transcript provides additional evaluation constraints.

---

# 2. Current Project Status

Current Phase: Phase 4 COMPLETE

Phase 1 — Requirements Analysis: COMPLETE
Phase 2 — Database Design: COMPLETE
Phase 3 — Java Foundation: COMPLETE
Phase 4 — JDBC + DAO: COMPLETE

No Phase 5 or later implementation should be assumed to exist.

---

# 3. Technology Requirements

The project MUST use:

- Core Java
- Java 8 features
- Direct JDBC
- MySQL 8
- SQL
- Java Collections
- Java Threads / Executors
- File handling
- Console application

The project MUST NOT use:

- Spring
- Spring Boot
- Hibernate
- JPA
- ORM
- Spring Security
- Any application framework

The trainer explicitly requires Core Java + Java 8 + JDBC.

---

# 4. Implementation Phases

The project is divided into the following phases:

1. Requirements Analysis
2. Database Design
3. Java Foundation
4. JDBC + DAO Layer
5. Authentication + Security
6. Customer/Product/Order Workflows
7. Payment + Inventory + Provisioning + Activation Transactions
8. Multithreading + Concurrency
9. Stream API Reports + File Handling
10. Integration Testing + Final Documentation

Only Phases 1–4 are currently complete.

---

# 5. Completed Phase 1

Phase 1 established the complete functional and technical requirements.

Important requirements include:

- Customer login and registration
- Admin login
- Provisioning Engineer role
- Inventory Administrator role
- CAPTCHA
- OTP
- Account locking
- Login history
- Role-based authorization
- Customer operations
- Product management
- Order management
- Inventory management
- Provisioning
- Payment
- Activation
- Notifications
- Audit logging
- Reports
- CSV/TXT export
- Concurrency control
- Java 8 features
- JDBC
- Multithreading

The official case-study PDF is the authoritative source for business requirements.

---

# 6. Completed Phase 2 — Database

Database: MySQL 8

Database name:

tonpms

Schema file:

database/schema.sql

Database design document:

database/DATABASE_DESIGN.md

The approved schema contains 16 tables.

The approved foreign-key relationships must not be changed without explicit review.

Important design decisions:

## AppUser / ProvisioningEngineer

AppUser does NOT contain engineer_id.

The relationship is one-way:

ProvisioningEngineer.user_id -> AppUser.user_id

Do NOT recreate the removed circular relationship.

## Inventory

InventoryItem contains:

assigned_order_id -> telecom_order.order_id

There is no product_id foreign key in inventory_item.

Do not invent a Product -> Inventory foreign key.

## Payments

An order can have multiple payment attempts.

Payment and activation are separate transaction flows.

## OTP

OTP state is persisted using otp_challenge.

CAPTCHA is intended to be handled in memory.

## Database relationships

Important relationships include:

Customer 1:N Order

Customer 1:N Subscription

Customer 1:N Notification

Order 1:N OrderItem

Product 1:N OrderItem

Order 1:N Payment

Order 1:N ProvisioningRequest

Engineer 1:N ProvisioningRequest

Order 1:N Inventory assignment

User M:N Role through AppUserRole

User 1:N LoginHistory

User 1:N AuditLog

User 1:N OtpChallenge

---

# 7. Completed Phase 3 — Java Foundation

Java source root:

src/com/amdocs/telecom/

Current model classes:

- Customer
- TelecomProduct
- TelecomOrder
- OrderItem
- InventoryItem
- ProvisioningEngineer
- ProvisioningRequest
- OrderPayment
- CustomerSubscription
- Notification
- AuditLog
- LoginHistory
- AppUser
- AppRole
- AppUserRole
- OtpChallenge

Current enums include:

- CustomerType
- IdentityStatus
- CustomerAccountStatus
- ProductStatus
- OrderType
- OrderStatus
- PaymentStatus
- PaymentTransactionStatus
- PaymentMode
- InventoryItemType
- InventoryStatus
- ProvisioningType
- ProvisioningStatus
- EngineerAvailability
- NotificationStatus
- SubscriptionStatus
- UserAccountStatus
- OtpPurpose
- RoleCode

Required domain exceptions:

- InvalidOrderException
- ProductUnavailableException
- CustomerNotEligibleException
- InventoryUnavailableException
- DuplicateOrderException
- ProvisioningException

Shared exception base:

- TelecomDomainException

Phase 3 validation:

- Java compilation passed using Java 8 compatibility
- Models correspond to approved database design
- Enum values were audited
- AppUser has no engineer_id
- No JDBC was implemented
- No DAO was implemented
- No service was implemented
- No controller was implemented
- No authentication was implemented
- No multithreading was implemented
- No reports were implemented

---

# 8. Current Package Structure

src/com/amdocs/telecom/

    controller/
    service/
        impl/
    dao/
        impl/
    model/
    dto/
    exception/
    validation/
    security/
    scheduler/
    report/
    util/
    main/

Only packages/classes required by the current phase should be created.

Do not create artificial/demo classes just to demonstrate Java concepts.

---

# 9. Architecture

The intended architecture is:

Console UI
    |
Controllers
    |
Services
    |
DAOs
    |
JDBC
    |
MySQL

Responsibilities:

controller:
- Console menus
- Input handling
- Role navigation

service:
- Business logic
- Business workflows
- Transaction orchestration

dao:
- SQL operations
- JDBC interaction
- Persistence

model:
- Domain entities

exception:
- Domain-specific exceptions

security:
- Authentication and authorization later

scheduler:
- Background processing later

report:
- Stream API reports and exports later

util:
- JDBC utilities
- Configuration
- Logging
- Shared utilities

---

# 10. Design Patterns Planned

The project should naturally demonstrate meaningful design patterns.

Planned patterns:

1. DAO
2. Factory
3. Strategy

Observer may be used if genuinely useful for notifications.

Do not introduce patterns artificially.

---

# 11. Completed Phase — Phase 4

Phase 4 implemented:

- JDBC connection infrastructure
- Database configuration
- Connection management
- DAO interfaces
- DAO implementations
- PreparedStatement
- ResultSet
- CRUD operations
- Batch operations where appropriate
- JDBC transaction infrastructure
- commit
- rollback
- savepoints
- Resource management

- `DatabaseConfig`, `DatabaseConnection`, and `JdbcTransactionManager`
- `DatabaseException`
- DAO interfaces and JDBC implementations for all 16 approved tables
- Prepared statements, ResultSet mapping, generated keys, and `OrderItemDao.saveBatch()`
- `config/config.properties.example`; local `config/config.properties` is Git-ignored
- a manual DAO integration smoke test that skips truthfully without local credentials/Connector-J

---

# 12. Phase 4 MUST NOT Implement Yet

Do NOT implement:

- Authentication
- CAPTCHA
- OTP
- Password hashing
- Login locking
- Customer business workflows
- Order business workflows
- Payment business logic
- Provisioning business logic
- Activation workflow
- Multithreading
- Reports
- File exports
- Controllers
- Complete services

Those belong to later phases.

---

# 13. JDBC Rules

Use:

- Connection
- PreparedStatement
- ResultSet
- CallableStatement only if later genuinely required

All user-provided SQL values MUST use PreparedStatement.

Do not concatenate user input directly into SQL.

Use try-with-resources where appropriate.

Do not use ORM.

---

# 14. Java Compatibility

The project must remain Java 8 compatible.

Do not introduce APIs that require newer Java versions.

Use Java 8 features where appropriate, including:

- Lambda expressions
- Streams
- Optional
- Functional interfaces
- Method references
- Interface default/static methods

---

# 15. Important Development Rule

The project is being developed incrementally.

After completing each phase:

1. Compile the entire project.
2. Run appropriate tests.
3. Verify against the approved database design.
4. Check that previous phases were not broken.
5. Update documentation if necessary.
6. Stop at the phase boundary.

Do NOT silently start the next phase.

---

# 16. Git / GitHub Status

Git repository:

TONPMS-Telecom-Order-Management

Current branch:

main

Phase 1–3 checkpoint commit:

8ae5e5f

Commit message:

Phase 1-3 complete: requirements, database, Java foundation

The working tree was clean after the Phase 3 commit.

Future phases should be committed separately.

Recommended commit format:

Phase 4 complete: JDBC and DAO layer

Phase 5 complete: authentication and security

etc.

---

# 17. Handoff Instructions for Another Coding Agent

If Codex usage ends and another coding agent such as Claude Code or another tool takes over:

1. Read this PROJECT_HANDOFF.md first.
2. Read database/DATABASE_DESIGN.md.
3. Read database/schema.sql.
4. Read docs/ARCHITECTURE.md.
5. Inspect the complete src/ directory.
6. Do not redo Phases 1–3.
7. Do not change approved database relationships without explicit approval.
8. Continue from the current phase only.
9. Follow the Core Java + Java 8 + JDBC-only constraint.
10. Do not introduce Spring, Spring Boot, Hibernate, JPA, ORM, or other frameworks.
11. Compile and verify the project after implementation.
12. Report exactly what was changed.
13. Stop when the requested phase is complete.

---

# 18. Critical Constraints

These constraints have priority throughout the project:

- Core Java only
- Java 8 compatible
- Direct JDBC
- MySQL 8
- Console application
- No frameworks
- Follow approved database schema
- Do not recreate AppUser.engineer_id
- Do not invent database relationships
- Do not skip required Java 8 features
- Do not skip required concurrency demonstrations
- Do not skip transaction/rollback demonstrations
- Do not implement multiple phases at once

---

# 19. Current Checkpoint

CURRENT STATUS:

Phase 1: COMPLETE
Phase 2: COMPLETE
Phase 3: COMPLETE
Phase 4: COMPLETE

NEXT ACTION:

Begin Phase 5 only when explicitly approved.
