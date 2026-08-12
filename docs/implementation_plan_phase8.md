**# Implementation Plan - Phase 8: Multithreading & Concurrency**

**## 1. Overview & Baseline**

**### Completed Baseline (Phases 1–7)**
**The project baseline is committed at \*\*\`50b70a2\` — Phase 7 complete: Payment Inventory Provisioning and Activation\*\*.**
**- \*\*Phase 1\*\*: Established core functional specifications and role definitions.**
**- \*\*Phase 2\*\*: Relational schema with 16 normalized tables (\`app\_user\`, \`customer\`, \`telecom\_order\`, \`order\_item\`, \`inventory\_item\`, \`provisioning\_engineer\`, \`provisioning\_request\`, \`order\_payment\`, \`customer\_subscription\`, \`notification\`, \`audit\_log\`, etc.).**
**- \*\*Phase 3\*\*: Core domain models, enums, and \`TelecomDomainException\` hierarchy.**
**- \*\*Phase 4\*\*: Pure JDBC infrastructure (\`DatabaseConnection\`, \`JdbcTransactionManager\`, \`DatabaseException\`) and DAO contracts for all 16 tables.**
**- \*\*Phase 5\*\*: Authentication and security services (\`PasswordUtils\` SHA-256+Salt, \`CaptchaService\`, \`OtpService\`, \`AuthenticationService\`, \`AuthorizationService\`, \`UserSession\`).**
**- \*\*Phase 6\*\*: Customer, Product catalog, and Order strategy services (\`CustomerService\`, \`ProductService\`, \`PricingStrategy\` factory for Individual/SME/Enterprise discounts, \`OrderService\`).**
**- \*\*Phase 7\*\*: Transactional domain services (\`PaymentService\` with payment-inventory reservation transaction, \`InventoryService\` with single-JVM thread safety and item-type mapping, \`ProvisioningService\` with Stream API ranking engine, \`ActivationService\` with service activation transaction and lifecycle completion, \`NotificationService\`, \`AuditService\`).**

**### Phase 8 Goals**
**Phase 8 introduces \*\*Multithreading, Concurrency, and Background Scheduling\*\* to the TONPMS platform as specified in Section 18 ("Multithreading") and Section 19 ("Low Inventory Alert") of the case study PDF.**

**---**

**## 2. Phase 8 Scope & Detailed Requirements**

**According to case study Sections 18 and 19, Phase 8 implements five background processing components and a central scheduler manager:**

**1. \*\*\`OrderProcessor\`\*\*:**
**   - Asynchronously processes pending orders (\`CREATED\`, \`VALIDATED\`, or \`PAYMENT\_PENDING\`) from an in-memory thread-safe \`BlockingQueue\<Long>\` or database query.**
**   - For unpaid orders requiring background automated processing, invokes \`PaymentService.processPayment()\` using the order's total amount and a specified payment mode (e.g. \`CARD\` or \`UPI\`).**
**   - If payment succeeds, \`PaymentService\` automatically sets \`paymentStatus = SUCCESS\`, reserves inventory, and sets \`orderStatus = INVENTORY\_RESERVED\`.**
**   - \`OrderProcessor\` then invokes \`ProvisioningService.createProvisioningRequest(...)\`, obtains the resulting \`provisioningRequestId\`, and enqueues \`provisioningRequestId\` directly into \`ProvisioningProcessor\`'s queue.**

**2. \*\*\`ProvisioningProcessor\`\*\*:**
**   - Asynchronously processes pending provisioning requests (\`PENDING\` or \`IN\_PROGRESS\`) using worker threads via \`ExecutorService\`.**
**   - Recommends engineers using Stream API, updates request status to \`SUCCESS\` or \`FAILED\`, updates engineer workload atomically, and triggers service activation (\`ActivationService.activateService()\`).**

**3. \*\*\`InventoryMonitor\`\*\*:**
**   - Scheduled periodic background task using \`ScheduledExecutorService\` (e.g., running every N seconds/minutes).**
**   - Scans available inventory grouped by \`itemType\` and \`warehouse\`.**
**   - Detects inventory levels falling below a configurable threshold (e.g., threshold = 10 units per warehouse/type).**
**   - Generates automatic administrator alert notifications (\`NotificationService.sendNotification(...)\`).**

**4. \*\*\`NotificationProcessor\`\*\*:**
**   - Asynchronous background delivery engine using a \`BlockingQueue\<Long>\` (notification IDs) and worker threads via \`ExecutorService\`.**
**   - Notification flow: \`NotificationService\` creates and persists notification $\rightarrow$ enqueues \`notificationId\` into \`NotificationProcessor\` queue $\rightarrow$ worker processes notification $\rightarrow$ updates status from \`PENDING\` to \`SENT\` or \`FAILED\` in the database (simulation/persistence-only, no external SMTP/SMS infrastructure).**

**5. \*\*\`OrderReportGenerator\`\*\*:**
**   - Scheduled background task using \`ScheduledExecutorService\`.**
**   - Periodically computes system performance metrics (active orders count, orders by status, revenue by product, failed provisioning count, available inventory count) and logs background audit metrics.**

**6. \*\*\`SchedulerManager\`\*\*:**
**   - Central lifecycle manager for starting, monitoring, and gracefully shutting down all thread pools (\`ExecutorService\`, \`ScheduledExecutorService\`).**

**---**

**## 3. Failure & Retry Rules**

**Background tasks strictly adhere to deterministic failure handling without inventing unrequested infinite retry loops or backoff logic:**

**- \*\*Order Processing Failure (\`OrderProcessor\`)\*\*:**
**  - Deterministic Rule: Any validation, payment, or inventory failure in \`OrderProcessor\` sets \`TelecomOrder.orderStatus = OrderStatus.FAILED\`.**
**  - Audit log is created via \`AuditService.logAction(...)\`.**
**  - Failure notification is created via \`NotificationService.sendNotification(...)\`.**
**  - Order is removed from the queue.**
**  - No automatic retry.**

**- \*\*Provisioning Failure (\`ProvisioningProcessor\`)\*\*:**
**  - If provisioning execution fails (e.g., no engineer available or execution error):**
**  - Request status is set to \`ProvisioningStatus.FAILED\` via \`ProvisioningService.updateProvisioningStatus()\`.**
**  - Assigned engineer workload is decremented atomically.**
**  - Order status transitions to \`OrderStatus.FAILED\`.**
**  - Customer notification and audit log are recorded.**
**  - Request is removed from the queue without automatic retry.**

**- \*\*Notification Dispatch Failure (\`NotificationProcessor\`)\*\*:**
**  - Notification status is set to \`NotificationStatus.FAILED\` in the database.**

**---**

**## 4. Architecture & Processor Queue Handoff**

**### Component Architecture & Handoff Pipeline**
**\`\`\`**
** [ Application / Client ]                                                      **
**           |**
**           v (enqueue orderId)**
**+-----------------------+      (creates & enqueues reqId)     +-----------------------+**
**|    OrderProcessor     | ----------------------------------> | ProvisioningProcessor |**
**|   (ExecutorService)   |                                     |   (ExecutorService)   |**
**+-----------------------+                                     +-----------------------+**
**           |                                                              |**
**           v                                                              v**
**   Payment & Inventory                                            Service Activation**
**           |                                                              |**
**           +------------------------------+-------------------------------+**
**                                          |**
**                                          v (creates & enqueues notificationId)**
**                               +-----------------------+**
**                               | NotificationProcessor |**
**                               |   (ExecutorService)   |**
**                               +-----------------------+**
**                                          |**
**                                          v**
**                               PENDING -> SENT / FAILED**
**\`\`\`**

**### Core Java 8 Concurrency Technologies & Configurability**
**- \*\*\`ExecutorService\`\*\*: Configurable thread pools for asynchronous task execution (\`OrderProcessor\`: 4 threads, \`ProvisioningProcessor\`: 4 threads, \`NotificationProcessor\`: 2 threads). Thread pool sizes are configurable constructor parameters.**
**- \*\*\`ScheduledExecutorService\`\*\*: Configurable scheduled thread pools (\`InventoryMonitor\`, \`OrderReportGenerator\`: 2 threads).**
**- \*\*\`BlockingQueue\<T>\`\*\*: \`LinkedBlockingQueue\` for thread-safe producer-consumer queuing between processors.**
**- \*\*\`Callable\<V>\` & \`Future\<V>\`\*\*: Used for async tasks returning confirmation results or report metrics.**
**- \*\*\`ThreadFactory\`\*\*: Named daemon thread factory (\`tonpms-order-worker-%d\`, \`tonpms-scheduler-%d\`) ensuring clean application shutdown.**
**- \*\*ThreadLocal JDBC Safety\*\*: Every background worker task thread MUST explicitly call \`DatabaseConnection.clearThreadConnection()\` in its \`finally\` block to prevent connection leaks across reused threads in the thread pool.**

**\`\`\`java**
**// Pattern for background worker thread JDBC connection safety**
**public void run() {**
**    Connection conn = null;**
**    try {**
**        conn = DatabaseConnection.getConnection();**
**        DatabaseConnection.setThreadConnection(conn);**
**        // Execute background service workflow\...**
**    } finally {**
**        DatabaseConnection.clearThreadConnection();**
**        if (conn != null) {**
**            try { conn.close(); } catch (Exception ignored) {}**
**        }**
**    }**
**}**
**\`\`\`**

**---**

**## 5. Sequence Diagrams**

**### A. Async Order Processing & Queue Handoff Flow (\`OrderProcessor\`)**
**\`\`\`mermaid**
**sequenceDiagram**
**    autonumber**
**    participant App as Application / Client**
**    participant OP as OrderProcessor**
**    participant PP as ProvisioningProcessor**
**    participant OS as OrderService**
**    participant PS as PaymentService**
**    participant IS as InventoryService**
**    participant PRV as ProvisioningService**
**    participant AUD as AuditService**
**    participant NS as NotificationService**

**    App->>OP: submitOrderForBackgroundProcessing(orderId)**
**    OP->>OP: queue.offer(orderId)**
**    Note over OP: Background Worker Thread**
**    OP->>OP: queue.take()**
**    OP->>OS: getOrderById(systemSession, orderId)**

**    alt paymentStatus == PENDING**
**        OP->>PS: processPayment(systemSession, orderId, totalAmount, CARD)**
**        alt payment & inventory SUCCESS**
**            PS->>IS: reserveInventoryForOrder(orderId)**
**            OP->>PRV: createProvisioningRequest(systemSession, orderId, provisioningType)**
**            PRV-->>OP: ProvisioningRequest (with provisioningId)**
**            OP->>PP: enqueueProvisioningRequest(provisioningId)**
**        else payment or inventory FAILURE**
**            OP->>OS: updateOrderStatus(systemSession, orderId, FAILED)**
**            Note over OP: OrderStatus set to FAILED**
**            OP->>AUD: logAction(systemSession, "ORDER\_PROCESSING\_FAILED", ...)**
**            OP->>NS: sendNotification(customerId, "Order processing failed...")**
**            Note over OP: Order removed from queue (NO RETRY)**
**        end**
**    else paymentStatus == SUCCESS**
**        OP->>PRV: createProvisioningRequest(systemSession, orderId, provisioningType)**
**        PRV-->>OP: ProvisioningRequest (with provisioningId)**
**        OP->>PP: enqueueProvisioningRequest(provisioningId)**
**    end**
**\`\`\`**

**### B. Async Provisioning & Activation Flow (\`ProvisioningProcessor\`)**
**\`\`\`mermaid**
**sequenceDiagram**
**    autonumber**
**    participant PP as ProvisioningProcessor**
**    participant PRV as ProvisioningService**
**    participant ACT as ActivationService**
**    participant AUD as AuditService**
**    participant NS as NotificationService**

**    Note over PP: Background Worker Thread**
**    PP->>PP: queue.take()**
**    PP->>PRV: getProvisioningRequestById(systemSession, reqId)**

**    alt provisioning SUCCESS**
**        PP->>PRV: updateProvisioningStatus(systemSession, reqId, SUCCESS, null)**
**        Note over PRV: Updates Engineer Active Tasks & Status**
**        PP->>ACT: activateService(systemSession, orderId)**
**        Note over ACT: Creates Subscription, Sets Inventory INSTALLED, Order ACTIVATED**
**    else provisioning FAILURE**
**        PP->>PRV: updateProvisioningStatus(systemSession, reqId, FAILED, errorMsg)**
**        Note over PRV: Decrements Engineer Active Tasks atomically & sets OrderStatus = FAILED**
**        PP->>AUD: logAction(systemSession, "PROVISIONING\_FAILED", ...)**
**        PP->>NS: sendNotification(customerId, "Provisioning failed...")**
**        Note over PP: Request removed from queue (NO RETRY)**
**    end**
**\`\`\`**

**### C. Scheduled Inventory Low-Stock Alert & Notification Flow (\`InventoryMonitor\`)**
**\`\`\`mermaid**
**sequenceDiagram**
**    autonumber**
**    participant SES as ScheduledExecutorService**
**    participant IM as InventoryMonitor**
**    participant IS as InventoryService**
**    participant NS as NotificationService**
**    participant NP as NotificationProcessor**

**    SES->>IM: trigger Periodic Check (every N seconds)**
**    IM->>IS: getAllInventoryItems(systemSession)**
**    Note over IM: Group by (warehouse, itemType) & count AVAILABLE**
**    alt Available count < Threshold (e.g. 10)**
**        IM->>NS: sendNotification(adminId, "LOW INVENTORY ALERT: SIM in Mumbai warehouse count: 5")**
**        NS->>NS: persist Notification (status = PENDING)**
**        NS->>NP: enqueueNotification(notificationId)**
**        Note over NP: Background Worker Thread**
**        NP->>NP: queue.take()**
**        NP->>NS: update status PENDING -> SENT / FAILED in DB**
**    end**
**\`\`\`**

**---**

**## 6. File Plan**

**### New Files to Create (11 files)**

**#### Package: \`com.amdocs.telecom.scheduler\`**
**1. [\`src/com/amdocs/telecom/scheduler/OrderProcessor.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/scheduler/OrderProcessor.java) [NEW]**
**   - Manages asynchronous order processing using \`ExecutorService\` and \`BlockingQueue\<Long>\`.**
**2. [\`src/com/amdocs/telecom/scheduler/ProvisioningProcessor.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/scheduler/ProvisioningProcessor.java) [NEW]**
**   - Manages asynchronous provisioning execution using \`ExecutorService\` and \`BlockingQueue\<Long>\`.**
**3. [\`src/com/amdocs/telecom/scheduler/InventoryMonitor.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/scheduler/InventoryMonitor.java) [NEW]**
**   - Scheduled background task using \`ScheduledExecutorService\` to check low inventory and trigger admin alerts.**
**4. [\`src/com/amdocs/telecom/scheduler/NotificationProcessor.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/scheduler/NotificationProcessor.java) [NEW]**
**   - Asynchronous background delivery engine for queued customer notifications.**
**5. [\`src/com/amdocs/telecom/scheduler/OrderReportGenerator.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/scheduler/OrderReportGenerator.java) [NEW]**
**   - Scheduled background task to periodically generate system order & revenue metrics.**
**6. [\`src/com/amdocs/telecom/scheduler/SchedulerManager.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/src/com/amdocs/telecom/scheduler/SchedulerManager.java) [NEW]**
**   - Central lifecycle controller for starting, pausing, and gracefully shutting down all thread pools and background processors.**

**#### Test Package: \`com.amdocs.telecom.scheduler\`**
**7. [\`tests/com/amdocs/telecom/scheduler/OrderProcessorTest.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/tests/com/amdocs/telecom/scheduler/OrderProcessorTest.java) [NEW]**
**   - Concurrency unit & integration test for asynchronous order processing.**
**8. [\`tests/com/amdocs/telecom/scheduler/ProvisioningProcessorTest.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/tests/com/amdocs/telecom/scheduler/ProvisioningProcessorTest.java) [NEW]**
**   - Concurrency test for asynchronous provisioning execution and service activation.**
**9. [\`tests/com/amdocs/telecom/scheduler/InventoryMonitorTest.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/tests/com/amdocs/telecom/scheduler/InventoryMonitorTest.java) [NEW]**
**   - Unit test for scheduled low-stock inventory threshold detection and alert notification dispatch.**
**10. [\`tests/com/amdocs/telecom/scheduler/NotificationProcessorTest.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/tests/com/amdocs/telecom/scheduler/NotificationProcessorTest.java) [NEW]**
**    - Asynchronous notification queue processing test.**
**11. [\`tests/com/amdocs/telecom/scheduler/SchedulerManagerTest.java\`]\(file:///c:/Users/adity/Documents/ChatGPT/Java%20Project/tests/com/amdocs/telecom/scheduler/SchedulerManagerTest.java) [NEW]**
**    - Lifecycle test verifying thread pool startup, task execution, and clean shutdown.**

**### Modified Files**
**- \*\*\`NONE\`\*\*. Zero Phase 4–7 source files will be modified.**

**---**

**## 7. Security, System Sessions & Authorization**

**Background scheduler tasks run asynchronously without an active human user interaction.**
**To satisfy Phase 5–7 \`UserSession\` authorization checks:**
**- \`SchedulerManager\` initializes a dedicated system background session:**
**  \`UserSession systemSession = new UserSession(0L, "SYSTEM\_SCHEDULER", null, null, EnumSet.of(RoleCode.ORDER\_ADMINISTRATOR, RoleCode.PROVISIONING\_ENGINEER, RoleCode.INVENTORY\_ADMINISTRATOR));\`**
**- Background processor threads pass this \`systemSession\` to service methods (\`getOrderById\`, \`processPayment\`, \`createProvisioningRequest\`, \`activateService\`, \`sendNotification\`).**

**---**

**## 8. Verification & Test Plan**

**### Automated Tests**
**1. \*\*Compilation\*\*: \`javac --release 8 -cp "lib/\*" -d out ...\`**
**2. \*\*Phase 8 Unit & Concurrency Tests\*\*:**
**   - \`OrderProcessorTest\`: Submits multiple orders concurrently, verifies all complete processing without deadlocks or state corruption.**
**   - \`ProvisioningProcessorTest\`: Verifies async engineer allocation and activation.**
**   - \`InventoryMonitorTest\`: Mocks inventory stock below threshold (e.g., 5 items) and asserts an alert notification is generated.**
**   - \`NotificationProcessorTest\`: Enqueues notifications and asserts status changes to \`SENT\`.**
**   - \`SchedulerManagerTest\`: Starts system, runs tasks, invokes \`shutdown()\`, and verifies all thread pools shut down within 5 seconds.**
**3. \*\*Phase 5 Security Regression Tests\*\*:**
**   - \`PasswordUtilsTest\`, \`CaptchaServiceTest\`, \`OtpServiceTest\`, \`AuthenticationServiceTest\`.**
**4. \*\*Phase 6 Workflow Regression Tests\*\*:**
**   - \`CustomerServiceTest\`, \`ProductServiceTest\`, \`OrderServiceTest\`.**
**5. \*\*Phase 7 Transaction Regression Tests\*\*:**
**   - \`InventoryServiceTest\`, \`PaymentServiceTest\`, \`ProvisioningServiceTest\`, \`ActivationServiceTest\`.**
**6. \*\*Integration Smoke Test\*\*: \`DaoIntegrationSmokeTest\`.**
**7. \*\*Git Verification\*\*: \`git diff --check\` and \`git status\`.**