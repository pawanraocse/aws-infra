# AWS-Infra Project - Unified Index & Source of Truth

**Last Updated:** 2025-11-15
**Version:** 2.6.0
**Status:** ✅ Multi-Tenant Microservices Architecture (DB-per-tenant path functional; migration split design pending)

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Project Structure](#project-structure)
5. [Microservices](#microservices)
6. [Infrastructure (Terraform)](#infrastructure-terraform)
7. [Authentication & Security](#authentication--security)
8. [Database & Multi-Tenancy](#database--multi-tenancy)
9. [Configuration Management](#configuration-management)
10. [Build & Deployment](#build--deployment)
11. [API Documentation](#api-documentation)
12. [Testing Strategy](#testing-strategy)
13. [Monitoring & Observability](#monitoring--observability)
14. [Development Workflow](#development-workflow)
15. [Known Issues & TODOs](#known-issues--todos)
16. [Flow Verification & Implementation Gaps](#flow-verification--implementation-gaps)
17. [ADRs](#adrs)
18. [Semantic Commits & Edge Cases](#semantic-commits--edge-cases)

---

<a id="project-overview"></a>
## Project Overview

### Purpose
Production-ready AWS-based Spring Boot microservices platform with Angular frontend, implementing:
- **Multi-tenant architecture** (transitioning from schema-per-tenant → database-per-tenant for stronger isolation)
- **AWS Cognito authentication** (OAuth2 / OIDC)
- **Service discovery** via Netflix Eureka
- **API Gateway** with JWT validation and tenant context propagation
- **Infrastructure as Code** using Terraform
- **Containerized deployment** with Docker Compose

### Business Domain
Enterprise SaaS platform supporting multiple tenants with complete data isolation, centralized authentication, and scalable microservices architecture.

### Key Features
- ✅ Tenant provisioning orchestration (PS-10) with pluggable post-actions
- ✅ DB-per-tenant strategy planned (feature flag currently off: `platform.db-per-tenant.enabled`)
- ✅ AWS Cognito OAuth2/OIDC authentication
- ✅ JWT-based authorization with tenant context
- ✅ Service discovery and gateway routing
- ✅ API documentation (SpringDoc)
- ✅ Flyway migrations (master registry)
- ✅ Structured error responses (shared module partially)

---

<a id="architecture"></a>
## Architecture

### High-Level Architecture Diagram
```
[Angular Frontend] --(JWT)--> [API Gateway] --> [Auth Service] (user session/JWT)
                                    |\
                                    | \--> [Platform Service] (tenant lifecycle, metadata)
                                    |        |--> [PostgreSQL master DB] (tenants registry)
                                    |        |--> [Tenant DBs] (tenant_db_<id>)
                                    |--> [Backend Service] (domain logic, tenant-scoped)
```

### Request Flow Sequence (Simplified)
1. User authenticates via Cognito → receives JWT.
2. Frontend sends requests with JWT → Gateway validates.
3. Gateway enriches headers (`X-Tenant-Id`, `X-User-Id`, `X-Request-Id`).
4. Backend-service resolves tenant and uses routing (future: dynamic DataSource per tenant DB).
5. Platform-service responsible for creating tenant metadata row + provisioning dedicated DB + running migrations.

---

<a id="technology-stack"></a>
## Technology Stack

### Backend
| Component         | Technology         | Version   | Purpose                                 |
|-------------------|-------------------|-----------|-----------------------------------------|
| Language          | Java              | 21 LTS    | Modern Java features, records, pattern matching |
| Framework         | Spring Boot       | 3.5.6     | Microservices framework                  |
| Cloud             | Spring Cloud      | 2025.0.0  | Service discovery, gateway, config       |
| Build Tool        | Maven             | 3.9+      | Dependency management, multi-module builds |
| Database          | PostgreSQL        | 16-alpine | Relational database with JSONB support   |
| Migration         | Flyway            | Latest    | Database version control                 |
| ORM               | Hibernate/JPA     | Latest    | Object-relational mapping                |
| Auth              | AWS Cognito       | -         | OAuth2/OIDC authentication               |
| Service Discovery | Netflix Eureka    | Latest    | Service registry                         |
| API Gateway       | Spring Cloud Gateway | Latest | Reactive gateway with filters            |
| Tracing           | Micrometer + Brave| Latest    | Distributed tracing                      |
| Metrics           | Prometheus        | Latest    | Metrics collection                       |
| Documentation     | SpringDoc OpenAPI | 2.8.13    | API documentation                        |

### Frontend
| Component   | Technology   | Version   | Purpose                |
|-------------|-------------|-----------|------------------------|
| Framework   | Angular     | 20.3.0    | SPA framework          |
| Language    | TypeScript  | 5.9.2     | Type-safe JavaScript   |
| Build       | Angular CLI | 20.3.7    | Build and dev server   |
| Testing     | Jasmine + Karma | Latest | Unit testing           |
| Routing     | Hash-based  | -         | Client-side routing    |
| State       | Signals     | Latest    | Reactive state management |

### Infrastructure
| Component   | Technology   | Version   | Purpose                |
|-------------|-------------|-----------|------------------------|
| Container   | Docker       | Latest    | Containerization       |
| IaC         | Terraform    | >=1.9.0   | Infrastructure as Code |
| Cloud       | AWS          | -         | Cloud provider         |
| Secrets     | AWS SSM      | -         | Parameter Store        |
| Registry    | AWS ECR      | Planned   | Container Registry     |
| Orchestration | AWS ECS Fargate | Planned | Container Orchestration |
| CI/CD       | GitHub Actions | Planned | Continuous Integration |
| Monitoring  | Prometheus, Grafana | Planned | Monitoring           |
| Logging     | CloudWatch, Fluent Bit | Planned | Logging           |
| Tracing     | AWS X-Ray, OpenTelemetry | Planned | Tracing         |

---

<a id="project-structure"></a>
## Project Structure

- **/auth-service/**: Handles authentication, JWT, and user info
- **/backend-service/**: Business logic, multi-tenant data, CRUD APIs
- **/gateway-service/**: API Gateway, JWT validation, routing
- **/eureka-server/**: Service registry
- 
- **/platform-service/**: Centralized tenant lifecycle & provisioning, policy management, internal token issuance
- **/frontend/**: Angular SPA
- **/terraform/**: Infrastructure as Code
- **/scripts/**: Utility scripts

---

<a id="microservices"></a>
## Microservices

### Eureka Server (Port 8761)
- Service discovery and registration
- Health monitoring
- Load balancing support
- Endpoint: `http://localhost:8761`

### Auth Service (Port 8081)
- OAuth2/OIDC authentication with AWS Cognito
- Session management (JSESSIONID)
- JWT token extraction
- User info endpoint
- Logout functionality
- **Servlet Context Path:** `/auth`
- Endpoints:
  - `GET /auth/oauth2/authorization/cognito` - Initiate OAuth2 login flow
  - `GET /auth/login/oauth2/code/cognito` - OAuth2 callback handler
  - `GET /auth/tokens` - Extract JWT from session
  - `GET /auth/me` - Get current user information
  - `POST /auth/logout` - Logout and clear session
  - `GET /auth/logged-out` - Logout confirmation page

### Gateway Service (Port 8080)
- API Gateway with JWT validation and routing
- Circuit breaker, header propagation, CORS
- Endpoints:
  - `/auth/**` → lb://auth-service
  - `/api/**` → lb://backend-service

### Backend Service (Port 8082)
- Domain APIs (CRUD, business operations)
- Will consume platform-service tenant metadata & connect to corresponding tenant DB (future dynamic DataSource)

### Platform Service (Port 8083)
- Central authority for tenant lifecycle & provisioning
- Context path: `/platform`
- Swagger UI: `http://localhost:8083/platform/swagger-ui.html`
- Endpoints:
  - `POST /platform/api/tenants` – Provision tenant
  - `GET /platform/api/tenants/{id}` – Get tenant metadata
- Pluggable post-provision actions implemented (SOLID pipeline): Storage, MigrationHistory, AdminUserSeed (placeholder), AuditLog
- Metrics: attempts/success/failure counters

---

<a id="infrastructure-terraform"></a>
## Infrastructure (Terraform)

<!-- TODO: Summarize Terraform modules (VPC, RDS, Cognito, ECS/EKS planned) and state management practices. -->

---

<a id="authentication--security"></a>
## Authentication & Security
- OAuth2/OIDC with AWS Cognito
- JWT-based authentication
- Tenant context propagation via headers
- Secure secrets in AWS SSM Parameter Store
- Spring Security for all endpoints

---

<a id="database--multi-tenancy"></a>
## Database & Multi-Tenancy

### Strategy Evolution
| Phase | Strategy | Notes |
|-------|----------|-------|
| Current | Single master DB + per-tenant metadata | Registry & provisioning only |
| Transition | Database-per-tenant (DB-per-tenant) | Feature flag controlled, improved isolation (DB creation implemented) |
| Future | Hybrid (DB-per-tenant + optional shared analytics DB) | Aggregation patterns |

### Master DB Tables
- `tenant` (id, name, status, storage_mode, sla_tier, jdbc_url, last_migration_version, created_at, updated_at)
- `tenant_migration_history` (id, tenant_id, version, applied_at, status, notes)

### Tenant DB Naming Convention
`tenant_<id>` (sanitized, truncated to 63 chars). Example: `tenant_acme123`.

### Provisioning Flow (DB-per-tenant)
```
POST /platform/api/tenants
  → Validate uniqueness
  → Insert tenant row (status=PROVISIONING)
  → Create dedicated Postgres database (tenant_<id>) via admin connection (autocommit, outside transaction)
  → Run baseline Flyway migrations against new DB (currently uses shared location)
  → Record migration history (tenant_migration_history)
  → Update lastMigrationVersion
  → Mark status=ACTIVE
  → Return TenantDto
```

### Migration Layering & Split Rationale (Planned)
Current state: Both platform master schema and per-tenant baseline use a single Flyway location (`classpath:db/migration`).

Problems with single shared location:
1. Version Collision – Platform evolution (V2__add_global_table.sql) can unintentionally shift tenant baseline ordering.
2. Coupled Release Cadence – Every tenant provisioning implicitly depends on platform deployment cadence; harder to roll tenant-only changes.
3. Limited Tier Customization – PREMIUM / REGION-specific tables cannot be conditionally applied cleanly without branching logic inside scripts.
4. Upgrade Complexity – Retrofitting new optional modules to existing tenants needs separate evolution path; shared location obscures intent.
5. Audit Clarity – Harder to differentiate platform vs tenant structural versions when investigating incidents.

Planned split:
- Platform migrations: `classpath:db/platform` (tables used only by platform-service, registry, policy, internal token keys).
- Tenant base migrations: `classpath:db/tenant/base` (core domain tables each tenant requires).
- Optional layers (feature/tier/region):
  - `classpath:db/tenant/tier/premium`
  - `classpath:db/tenant/feature/audit`
  - `classpath:db/tenant/region/eu`

Execution strategy:
- Master DB continues using Spring Boot Flyway auto-run against `db/platform`.
- Tenant provisioning composes a dynamic list of locations based on SLA tier & feature flags, invoking Flyway manually per tenant DB.
- Each tenant has independent version sequence recorded in `tenant_migration_history` (baseline + applied versions).

Safeguards:
- Repeatable migrations (R__) for data seeds idempotent across tenants.
- Strict additive DDL in tenant scripts to avoid destructive operations at provision time.
- Feature flags gate inclusion of optional locations.

### Future DataSource Routing
- Dynamic `AbstractRoutingDataSource` keyed by tenant ID
- Lazy creation + bounded pool (max 5 connections per tenant) with eviction policy

### Per-Tenant DB User & Credential Management (2025-11-16)
- Platform-service now creates a unique DB user per tenant (convention: tenant_<id>_user) with a random password.
- Password is encrypted using AES and stored in the `tenant` table (`db_user_password_enc`).
- Internal API `/internal/tenants/{tenantId}/db-info` returns JDBC URL, username, and encrypted password (backend-service decrypts using shared utility).
- Integration tests verify:
  - User is created in PostgreSQL (`pg_roles`)
  - User can connect to the tenant DB
  - User has correct permissions (can SELECT, cannot CREATE TABLE)
- Caching of tenant DB info in backend-service for performance (Caffeine, 5 min TTL).

---

<a id="configuration-management"></a>
## Configuration Management
- All secrets and config in AWS SSM Parameter Store
- Environment-specific YAML files
- Terraform manages SSM parameters

---

<a id="build--deployment"></a>
## Build & Deployment
- Maven multi-module build
- Docker Compose for local dev
- Terraform for AWS infra
- GitHub Actions for CI/CD (planned)

---

<a id="api-documentation"></a>
## API Documentation
- SpringDoc OpenAPI
- Platform-service docs: `/platform/v3/api-docs`
- Swagger UI (platform-service): `/platform/swagger-ui.html`
- Backend-service docs: `/v3/api-docs` (context path not set)

---

<a id="testing-strategy"></a>
## Testing Strategy
- JUnit 5, Mockito, AssertJ for unit tests
- Testcontainers for integration tests
- Cypress/Playwright for frontend e2e
### Platform-Service Provisioning Tests (Update)
| Test | Coverage |
|------|----------|
| Happy path provisioning | ✅ |
| Duplicate tenant ID | ✅ |
| Action chain order | ✅ (Ordered actions enforced by @Order) |
| Failure rollback | ⚠️ Pending simulation test |
| DB-per-tenant flag disabled | ⚠️ Pending feature flag test |
| Per-tenant DB user/permissions | ✅ (integration test: verifyTenantDbUserAndPermissions) |

---

<a id="monitoring--observability"></a>
## Monitoring & Observability
- Prometheus, Grafana (planned)
- Distributed tracing (planned)
- Centralized logging (planned)

### Metrics (Current)
- `platform.tenants.provision.attempts`
- `platform.tenants.provision.success`
- `platform.tenants.provision.failure`

### Metrics (Planned)
- `platform.tenants.count{status}`
- `platform.tenants.provision.duration{storageMode}`
- `platform.tenants.migration.time{version}`

---

<a id="development-workflow"></a>
## Development Workflow
- Feature branches, semantic commits
- PR review checklist:
  - End-to-end flow complete
  - Dependency injection and test coverage
  - Security, logging, validation integrated
  - Scalable and reusable
  - Names clearly communicate intent

---

<a id="known-issues--todos"></a>
## Known Issues & TODOs (Updated 2025-11-15)
- [x] PS-10 Tenant provisioning action pipeline
- [x] DB creation path (DATABASE mode) with autocommit outside transaction
- [ ] Split Flyway migration locations (platform vs tenant) – design approved
- [ ] Dynamic location composition (tier/feature/region) in `MigrationHistoryAction`
- [ ] Enable and validate retry endpoint for PROVISION_ERROR (PS-15)
- [ ] Backend dynamic DataSource routing (PS-16)
- [ ] Policy engine implementation (PS-12)
- [ ] Internal token issuance (PS-13)

---

<a id="flow-verification--implementation-gaps"></a>
## Flow Verification & Implementation Gaps
...existing code...

### Newly Addressed
- Autocommit CREATE DATABASE outside transactional boundary.
- Credential injection fix for per-tenant migrations (using `spring.datasource.username/password`).

### Pending Gaps (Migration Split)
| Gap | Impact | Plan |
|-----|--------|------|
| Shared migration location | Version coupling | Introduce layered directories & dynamic Flyway invocation |
| No tier-based schema | Premium feature delay | Add conditional location inclusion by SLA |
| No feature flag gating per migration set | Risk of premature rollout | Properties to enable optional locations |

---

<a id="adrs"></a>
## ADRs
- [ADR 0001: Gateway-Centric Identity & Authorization Enforcement](docs/adr/0001-gateway-identity-enforcement.md)

### Upcoming ADRs
| ADR | Topic |
|-----|-------|
| 0004 | Database-per-tenant vs schema-per-tenant choice |
| 0005 | Migration layering strategy & dynamic location composition |
| 0006 | Dynamic DataSource routing strategy |

## Immediate Next Steps (Execution Queue)
1. Create migration directory split: `db/platform` & `db/tenant/base` (+ placeholders).
2. Refactor `TenantMigrationRunner` to accept dynamic location list.
3. Update `MigrationHistoryAction` to build location list (tier/feature flags).
4. Add integration test for premium tier provisioning applying extra migration.
5. Draft ADR 0005 and update plan references.

---

**End of copilot-index.md**
