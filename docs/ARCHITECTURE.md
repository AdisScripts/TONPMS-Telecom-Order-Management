# TONPMS Architecture

## Phase 3 scope

This phase establishes the project package layout, schema-aligned model layer, fixed-domain enums, and domain exceptions. It intentionally contains no database access, business services, security implementation, menus, reports, background processing, or controllers.

## Packages

| Package | Responsibility | Phase 3 state |
|---|---|---|
| `controller` | Console menus and request coordination | Reserved for a later phase |
| `service` / `service.impl` | Business rules and transaction orchestration | Reserved for a later phase |
| `dao` / `dao.impl` | Direct JDBC persistence operations | Reserved for Phase 4 |
| `model` | Database-aligned entities and business enums | Implemented |
| `dto` | Input/output objects only where persistence models should not be exposed | Reserved; no DTO is needed yet |
| `exception` | Domain exception hierarchy | Implemented |
| `validation` | Reusable input and business validation | Reserved for a later phase |
| `security` | CAPTCHA, passwords, OTP, and authorization | Reserved for a later phase |
| `scheduler` | Background processors and queues | Reserved for a later phase |
| `report` | Stream-based reports and file export | Reserved for a later phase |
| `util` | Configuration, logging, and shared technical utilities | Reserved for a later phase |
| `main` | Application bootstrap | Reserved until a runnable flow exists |

## Model layer

Each model maps to one approved Phase 2 table. Object references and collection fields model the approved relationships for in-memory use; they are not additional database columns. In particular, `AppUser` contains an optional `Customer` reference only. The one-way engineer credential link is held by `ProvisioningEngineer.user`, corresponding to `provisioning_engineer.user_id -> app_user.user_id`.

## Planned separation

Controllers will delegate to services. Services will enforce validation and coordinate transactions. DAOs will own all SQL and JDBC calls. Models will remain free of JDBC and workflow logic.

## Direct JDBC

The evaluation requires direct JDBC, including prepared statements, transactions, rollback, savepoints, and locking. JDBC will therefore be implemented in the DAO/util layers in a later phase. Spring, Spring Boot, Hibernate, JPA, ORM tooling, Spring Security, and other application frameworks are intentionally excluded.
