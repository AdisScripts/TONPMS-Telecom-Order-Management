# TONPMS Architecture

## Phase 4 scope

Phase 3 established the project package layout, schema-aligned model layer, fixed-domain enums, and domain exceptions. Phase 4 adds JDBC infrastructure and persistence-only DAOs. Business services, security implementation, menus, reports, background processing, and controllers remain intentionally absent.

## Packages

| Package | Responsibility | Phase 3 state |
|---|---|---|
| `controller` | Console menus and request coordination | Reserved for a later phase |
| `service` / `service.impl` | Business rules and transaction orchestration | Reserved for a later phase |
| `dao` / `dao.impl` | Direct JDBC persistence operations | Implemented for all approved tables |
| `model` | Database-aligned entities and business enums | Implemented |
| `dto` | Input/output objects only where persistence models should not be exposed | Reserved; no DTO is needed yet |
| `exception` | Domain exception hierarchy | Implemented |
| `validation` | Reusable input and business validation | Reserved for a later phase |
| `security` | CAPTCHA, passwords, OTP, and authorization | Reserved for a later phase |
| `scheduler` | Background processors and queues | Reserved for a later phase |
| `report` | Stream-based reports and file export | Reserved for a later phase |
| `util` | Configuration, logging, and shared technical utilities | JDBC configuration, connections, and transaction primitives implemented |
| `main` | Application bootstrap | Reserved until a runnable flow exists |

## Model layer

Each model maps to one approved Phase 2 table. Object references and collection fields model the approved relationships for in-memory use; they are not additional database columns. In particular, `AppUser` contains an optional `Customer` reference only. The one-way engineer credential link is held by `ProvisioningEngineer.user`, corresponding to `provisioning_engineer.user_id -> app_user.user_id`.

## Planned separation

Controllers will delegate to services. Services will enforce validation and coordinate transactions. DAOs will own all SQL and JDBC calls. Models will remain free of JDBC and workflow logic.

## Direct JDBC and DAO layer

The evaluation requires direct JDBC, including prepared statements, transactions, rollback, savepoints, and locking. `DatabaseConfig` reads uncommitted local settings from `config/config.properties`; `DatabaseConnection` loads MySQL Connector/J and creates short-lived connections. DAO implementations use prepared statements, generated keys, ResultSet mappers, and try-with-resources. `JdbcTransactionManager` supplies begin/commit/rollback/savepoint primitives only; no business transaction workflow is implemented in this phase.

MySQL Connector/J is a runtime driver requirement, not an application framework. It must be supplied on the local classpath when running database integration tests. Spring, Spring Boot, Hibernate, JPA, ORM tooling, Spring Security, and other application frameworks are intentionally excluded.
