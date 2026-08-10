# TONPMS Phase 2 - Database Design

This document separates what the official PDF says from trainer constraints and implementation decisions.

## Source classification

### Explicit PDF requirements

The PDF explicitly names the following entities and attributes:

- `Customer`: customerId, customerNumber, customerName, email, mobileNumber, customerType, address, city, identityStatus, accountStatus, registrationDate.
- `TelecomProduct`: productId, productCode, productName, productType, description, monthlyPrice, activationFee, contractPeriod, status.
- `TelecomOrder`: orderId, orderNumber, customerId, orderDate, orderType, totalAmount, paymentStatus, orderStatus, requestedActivationDate.
- `OrderItem`: orderItemId, orderId, productId, quantity, unitPrice, discount, tax, totalAmount.
- `InventoryItem`: inventoryId, itemCode, itemType, serialNumber, warehouse, location, status, assignedOrderId.
- `ProvisioningRequest`: provisioningId, orderId, serviceId, provisioningType, networkElement, requestedDate, completedDate, status, errorMessage.
- `ProvisioningEngineer`: engineerId, employeeCode, engineerName, specialization, region, availability, activeTasks, experienceYears.
- `OrderPayment`: paymentId, orderId, transactionReference, amount, paymentMode, paymentDate, status.
- `CustomerSubscription`, `Notification`, `AuditLog`, and `LoginHistory` are named but their complete attribute lists are not supplied.

The PDF explicitly requires primary keys, foreign keys, unique constraints, NOT NULL constraints, indexes, status columns, timestamps, and SQL demonstrations including joins, grouping, aggregates, subqueries, CASE expressions, and date functions. Views, stored procedures, functions, and triggers are optional only where appropriate.

### Trainer/evaluation constraints

- Use Core Java, Java 8 features, and direct JDBC.
- Do not use Spring, Spring Boot, Hibernate, JPA, ORM, Spring Security, or another application framework.
- Demonstrate transactions, commit, rollback, savepoints, prepared statements, and concurrency control.
- The payment and activation flows must remain separate.

### Implementation/design decisions

- MySQL 8 is selected because it supports the required transaction and row-locking behavior, including `SELECT ... FOR UPDATE`.
- Authentication requires `app_user`, `app_role`, and `app_user_role`; these are implementation tables because the PDF requires authentication and role authorization but does not define authentication columns.
- `otp_challenge` is added to persist OTP lifecycle state; CAPTCHA is generated and verified in memory and therefore needs no table.
- `app_user.customer_id` links a customer credential to its customer profile, while `provisioning_engineer.user_id` links an engineer credential to its engineer profile. This supports customer and engineer login without duplicating identity data.
- `created_at` and `updated_at` are added to transactional tables because the PDF explicitly requires timestamps, although it does not list every timestamp attribute.
- `product_type` and `inventory_item.item_type` remain independent. The PDF does not specify `product_id` on inventory. Compatibility will be maintained by an explicit Java mapping between product types and reservable inventory types. No Product-to-Inventory foreign key is created.
- `assigned_order_id` is sufficient for the PDF's one-item-to-one-current-order assignment: an inventory item can be assigned to at most one order at a time. No order-inventory mapping table is added. Reservation history belongs in `audit_log`.
- `order_payment` permits multiple attempts per order. This is an implementation decision, not an explicit PDF requirement, and supports payment failure/retry while retaining auditability.
- `customer_subscription` uses one row per activated service. The PDF requires updating the customer's subscription but does not define its columns, so the remaining attributes are implementation decisions.
- Status values use `VARCHAR` plus `CHECK` constraints rather than MySQL-specific ENUMs, keeping Java-to-database mapping explicit.
- No views, procedures, functions, or triggers are included in the base schema. They may be added later only if a concrete reporting or integrity need is identified.

## Entity design

| Entity | PK | Attributes | FK | Relationships / cardinality | Reason |
|---|---|---|---|---|---|
| Customer | customer_id | customer_number, name, email, mobile, type, address, city, identity_status, account_status, registration_date | None | Customer 1:N Order; Customer 1:N Subscription; Customer 1:N Notification | PDF entity and customer operations |
| TelecomProduct | product_id | code, name, type, description, monthly_price, activation_fee, contract_period, status | None | Product 1:N OrderItem | `product_id` in OrderItem establishes catalogue selection |
| TelecomOrder | order_id | number, date, type, total_amount, payment_status, order_status, requested_activation_date | customer_id -> Customer | Customer 1:N Order; Order 1:N OrderItem; Order 1:N Payment; Order 1:N ProvisioningRequest | PDF lifecycle entity |
| OrderItem | order_item_id | quantity, unit_price, discount, tax, total_amount | order_id -> Order; product_id -> Product | Order N:M Product through OrderItem | PDF explicitly permits multiple products per order |
| InventoryItem | inventory_id | code, type, serial_number, warehouse, location, status, assigned_order_id | assigned_order_id -> Order, nullable | Order 1:N assigned InventoryItem; each item 0..1 current order | `assignedOrderId` is the PDF-provided link |
| ProvisioningEngineer | engineer_id | employee_code, name, specialization, region, availability, active_tasks, experience_years | user_id -> AppUser, nullable | Engineer 1:N ProvisioningRequest | PDF recommendation requirement |
| ProvisioningRequest | provisioning_id | service_id, type, network_element, requested_date, completed_date, status, error_message | order_id -> Order; engineer_id -> Engineer, nullable | Order 1:N ProvisioningRequest; Engineer 1:N Request | PDF provisioning lifecycle |
| OrderPayment | payment_id | transaction_reference, amount, mode, payment_date, status | order_id -> Order | Order 1:N Payment | Retry support is an implementation decision |
| CustomerSubscription | subscription_id | service_id, service_type, activation_date, termination_date, status | customer_id -> Customer; order_id -> Order | Customer 1:N Subscription; Order 1:N Subscription | Required for service activation |
| Notification | notification_id | recipient_user_id, customer_id, type, message, status, created_at, sent_at | customer_id -> Customer; recipient_user_id -> AppUser | Customer 1:N Notification | Required for customer/admin notifications |
| AuditLog | audit_id | actor_user_id, entity_type, entity_id, action, details, created_at | actor_user_id -> AppUser | User 1:N AuditLog | Required to record audit events |
| LoginHistory | login_history_id | user_id, attempted_at, success, ip_address, failure_reason | user_id -> AppUser | User 1:N LoginHistory | Explicit PDF security requirement |
| AppUser | user_id | username, password_hash, password_salt, account_status, failed_attempts, locked_until, customer_id | customer_id -> Customer | User M:N Role; optional Customer profile | Authentication implementation table |
| AppRole | role_id | role_code, role_name | None | Role M:N User through AppUserRole | Authorization implementation table |
| AppUserRole | user_id + role_id | assigned_at | user_id -> AppUser; role_id -> AppRole | User M:N Role | Normalized role assignment |
| OtpChallenge | otp_id | user_id, purpose, otp_hash, expires_at, consumed_at, attempts | user_id -> AppUser | User 1:N OTP challenges | OTP persistence implementation decision |

## Distinct transaction flows

### Payment transaction from the PDF

`Validate Order -> Validate Amount -> Process Payment -> Update Order -> Reserve Inventory -> Audit -> COMMIT`

On failure, the JDBC transaction rolls back. This flow is implemented separately from activation so payment behavior can be demonstrated independently.

### Activation transaction from the PDF

`Validate Order -> Validate Payment -> Reserve Inventory -> Create Provisioning Request -> Activate Service -> Update Order -> Update Inventory -> Audit -> Notification -> COMMIT`

If provisioning fails, the transaction rolls back so inventory cannot remain incorrectly reserved.

## Stream API traceability plan

| PDF report requirement | Future class | Future method |
|---|---|---|
| Most ordered products | `ReportService` | `getMostOrderedProducts()` |
| Revenue by product | `ReportService` | `getRevenueByProduct()` |
| Orders by status | `ReportService` | `getOrdersByStatus()` |
| Orders by customer type | `ReportService` | `getOrdersByCustomerType()` |
| Inventory utilization | `ReportService` | `getInventoryUtilization()` |
| Available inventory by warehouse | `ReportService` | `getAvailableInventoryByWarehouse()` |
| Engineer workload | `ReportService` | `getEngineerWorkload()` |
| Failed provisioning requests | `ReportService` | `getFailedProvisioningRequests()` |
| Average order processing time | `ReportService` | `getAverageOrderProcessingTime()` |
| Top customers by revenue | `ReportService` | `getTopCustomersByRevenue()` |

Each method will contain visible `filter`, `map`, `sorted`, `groupingBy`, aggregate, and `Optional` operations where appropriate, rather than hiding the required logic in a generic helper.

## Explicit PDF ambiguities

1. The PDF does not define the full attributes of Notification, AuditLog, CustomerSubscription, or LoginHistory.
2. The PDF does not define a product-to-inventory relationship or an inventory compatibility algorithm.
3. The PDF does not specify whether an order may have multiple payment attempts.
4. The PDF does not specify whether an order may reserve multiple inventory items or whether reservation history needs a separate table.
5. The PDF names authentication requirements but does not define user, role, password, OTP, or CAPTCHA tables.
6. The PDF does not specify a database vendor.
7. `ProvisioningRequest.serviceId` is specified, but no Service entity is defined. It is retained as an opaque service identifier rather than inventing an unsupported Service table.

The decisions above resolve these ambiguities explicitly and can be changed before Java implementation if the evaluator provides different guidance.
