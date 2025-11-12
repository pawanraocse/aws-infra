# AWS-Infra Project - Unified Index & Source of Truth

**Last Updated:** 2025-11-12
**Version:** 2.4.2
**Status:** ✅ Production-Ready Multi-Tenant Microservices Architecture with Angular Frontend (hardening in progress)

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
- **Multi-tenant architecture** with schema-per-tenant isolation
- **AWS Cognito authentication** with Modern UI v2
- **Service discovery** via Netflix Eureka
- **API Gateway** with JWT validation and tenant context propagation
- **Infrastructure as Code** using Terraform
- **Containerized deployment** with Docker Compose

### Business Domain
Enterprise SaaS platform supporting multiple tenants with complete data isolation, centralized authentication, and scalable microservices architecture.

### Key Features
- ✅ Multi-tenant data isolation (schema-per-tenant)
- ✅ AWS Cognito OAuth2/OIDC authentication
- ✅ JWT-based authorization with tenant context
- ✅ Service discovery and load balancing
- ✅ API Gateway with circuit breaker
- ✅ Distributed tracing and metrics
- ✅ Automated infrastructure provisioning
- ✅ SSM Parameter Store for secrets
- ✅ PostgreSQL with Flyway migrations
- ✅ OpenAPI/Swagger documentation

---

<a id="architecture"></a>
## Architecture

### High-Level Architecture Diagram
```
[Angular Frontend] --(JWT)--> [API Gateway] --(JWT validated, headers added)--> [Auth Service | Backend Service] --(tenant context)--> [PostgreSQL]
      |                                                                                                   |
      |                                                                                                   |
      +-------------------[Eureka Service Registry]-------------------+                                   |
      |                                                                                                   |
      +-------------------[AWS Cognito, SSM Parameter Store]----------+                                   |
```

### Request Flow Sequence

**1. User Login Flow:**
```
User → Frontend (4200) → Click "Login with Cognito"
  ↓
Gateway (8080/auth/oauth2/authorization/cognito) → Auth-Service (8081/auth/oauth2/authorization/cognito)
  ↓
Cognito Hosted UI (Modern Managed Login v2)
  ↓
User enters credentials → Cognito validates
  ↓
Cognito redirects to: http://localhost:8081/auth/login/oauth2/code/cognito
  ↓
Auth-Service exchanges code for tokens → Creates session (JSESSIONID)
  ↓
Auth-Service redirects to: http://localhost:4200/#/callback
  ↓
Frontend calls: http://localhost:8080/auth/tokens (with session cookie)
  ↓
Auth-Service extracts JWT from session → Returns JWT
  ↓
Frontend stores JWT in localStorage → Redirects to dashboard
```

**2. API Request Flow:**
```
User (JWT) → Frontend → Gateway → Validate JWT → Extract tenant_id
                                   ↓
                              Add Headers (X-Tenant-Id, X-User-Id)
                                   ↓
                              Backend-Service → TenantContextFilter
                                   ↓
                              Set schema: tenant_acme
                                   ↓
                              PostgreSQL (tenant_acme.entries)
```

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
- Multi-tenant business logic
- Schema-per-tenant data isolation
- CRUD APIs

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
- PostgreSQL 16-alpine
- Schema-per-tenant isolation
- Flyway for migrations
- Hibernate multi-tenancy

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
- SpringDoc OpenAPI 2.8.13
- Swagger UI at `/swagger-ui.html` for each service

---

<a id="testing-strategy"></a>
## Testing Strategy
- JUnit 5, Mockito, AssertJ for unit tests
- Testcontainers for integration tests
- Cypress/Playwright for frontend e2e

---

<a id="monitoring--observability"></a>
## Monitoring & Observability
- Prometheus, Grafana (planned)
- Distributed tracing (planned)
- Centralized logging (planned)

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
## Known Issues & TODOs
- [x] Frontend authentication flow with Angular
- [x] OAuth2 callback URL configuration with servlet context path
- [x] Health check configuration for Docker services
- [ ] Entry management UI (CRUD operations)
- [ ] ECS/ECR deployment automation
- [ ] Full CI/CD pipeline
- [ ] Advanced monitoring and tracing
- [ ] Tenant onboarding automation

---

<a id="flow-verification--implementation-gaps"></a>
## Flow Verification & Implementation Gaps
(Review Date: 2025-11-12)

### Summary
End-to-end auth + routing flow functions under Docker Compose, but several divergences exist between documented intent and current implementation state. This section tracks gaps to close before declaring the hardening phase complete.

### Validated Runtime Flow
```
Frontend (Angular) → Gateway (JWT validation + header enrichment) → Backend-Service (tenant filter) → PostgreSQL
Frontend → Cognito Hosted UI → Auth-Service (session) → /auth/tokens → JWT → Stored → Subsequent API calls
```
All core components start and pass their health checks (`docker-compose.yml`). JWT enrichment headers (`X-User-Id`, `X-Tenant-Id`, `X-Email`, `X-Authorities`) are injected by gateway for `/api/**` routes.

### Discrepancies / Gaps
| Area | Expected (Docs) | Actual (Code / Compose) | Gap / Risk | Action |
|------|-----------------|--------------------------|------------|--------|
| Backend Endpoint Protection | `authenticated()` for business APIs | `permitAll()` (SecurityConfig) | APIs callable without auth if gateway is bypassed | Switch to `.anyRequest().authenticated()` after internal token rollout or add temporary resource-server config (defense-in-depth) |
| Multi-Tenancy Enforcement | Fail-closed at gateway + backend validation | Backend allows all requests; tenant validation only header-based | Missing auth layer could allow anonymous calls with forged headers | Add internal signed token / HMAC phase, restrict direct header trust |
| Swagger/OpenAPI Exposure | Documented; secured in production | Accessible anonymously | Information disclosure | Require auth in non-dev profiles; restrict via feature flag |
| Error Schema Consistency | Unified JSON across all services | Minor variance: backend includes `path`, gateway omits it | Inconsistent observability parsing | Standardize error DTO (shared module) + propagate `path` uniformly |
| CORS Origins | Multi-origin support documented | Single origin via `cors.allowed-origins` property | Harder multi-environment dev | Externalize list to env/SSM; update compose with variable |
| Request Correlation | RequestId everywhere | Backend may generate new ID if missing | Possible trace discontinuity | Ensure gateway always sets `X-Request-Id`; add filter test |
| Security Hardening Flags | Listed in SYSTEM_DESIGN.md | Some missing in properties (`security.gateway.*`) | Drift between config & docs | Introduce consolidated `application.yml` section + defaults |
| Internal Token Strategy | Planned in docs | Not yet started | Future dependency for header trust reduction | Begin ADR 0002 + platform-service bootstrap |
| Metrics & Observability | Planned metrics enumerated | No custom metrics yet | Reduced insight for tuning | Add Micrometer counters for tenant deny/allow flows |
| Frontend Containerization | Compose orchestrates backend only | Frontend not included | Manual startup / drift | Add Angular service block (dev-only) with proper network |
| SSM / Secret Retrieval | Compose sets env vars directly | AWS keys passed via environment | Risk of local leak / misuse | Replace with scripts fetching ephemeral session tokens (STS) |
| Database Migration Separation | Per-service Flyway tables documented | Auth service sets distinct table; backend not shown | Need confirm backend uses separate history | Verify backend config & document both tables |

### Confirmed Correct Items
- Gateway tenant extraction fail-closed (no default).
- Header sanitization global filters present.
- Structured JSON logging patterns implemented in gateway/auth-service.
- Circuit breaker + retry configured for both upstream routes.

### Recommended Immediate Fixes (Priority Order)
1. Enforce backend authentication (defense-in-depth) – even with gateway in front.
2. Introduce shared `platform-shared` module for `ErrorResponse`, header constants, regex reuse.
3. Add Micrometer metrics: `gateway.requests.total`, `gateway.tenant.missing.total`, `backend.tenant.invalid.total`.
4. Extend docker-compose with optional `redis` (future permission cache) and `zipkin` (tracing) placeholders.
5. Normalize error schema (add `path` to gateway responses; remove variance) before integrating centralized log parsing.
6. Add Angular container block for parity in local orchestration.
7. Document current backend security posture explicitly (temporary) until internal token implementation.

### Validation Artifacts To Add
- Integration test: missing `X-Tenant-Id` returns 403 from backend.
- Integration test: multiple tenant groups returns 400 `TENANT_CONFLICT` from gateway.
- Unit test: error response serialization matches canonical schema.

### Tracking
Each item above should gain a task ID in `next_task.md` or consolidated task file; mark completion and update this section. Remove resolved rows rather than adding duplicates.

## Naming Conventions
- Controllers: `*Controller.java`
- Services: `*.service.ts`
- Tests: `*.spec.ts`, `*Test.java`
- Config: `application-<env>.yml`

---

## Test Coverage Map
- Auth Service: 80%+ unit/integration
- Backend Service: 85%+ unit/integration
- Gateway: 85%+ integration & unit (tenant extraction, header sanitization, request id, logging)
- Frontend: 80%+ unit/e2e

---

## Auth/Security Approach
- All external APIs secured with JWT
- User/tenant context propagated via headers
- SSM Parameter Store for secrets
- No JPA entities exposed externally

---

## 📄 Documentation File Pattern

**IMPORTANT:** Always update existing .md files rather than creating new ones for each change.

### Standard Documentation Files

1. **copilot-index.md** (This file)
   - Single source of truth for project overview
   - Architecture, technology stack, project structure
   - High-level documentation
   - Update after every significant architectural change

2. **CURRENT_STATUS.md**
   - Current implementation status
   - What's working, what's next
   - Service configuration details
   - Recent fixes and troubleshooting
   - Quick reference for developers
   - Update after completing features or fixing issues

3. **IMPLEMENTATION_TASKS.md**
   - Detailed task tracking
   - Implementation progress
   - Technical decisions
   - Update as tasks are completed

4. **HLD.md** (High-Level Design)
   - System architecture
   - Design decisions
   - Component interactions
   - Update when architecture changes

5. **README.md**
   - Getting started guide
   - Quick setup instructions
   - Basic usage
   - Keep concise and user-friendly

### Documentation Rules

- ✅ **DO:** Update existing files with new information
- ✅ **DO:** Keep related information together in the same file
- ✅ **DO:** Use clear section headers for easy navigation
- ❌ **DON'T:** Create new .md files for each change
- ❌ **DON'T:** Duplicate information across multiple files
- ❌ **DON'T:** Create temporary or one-off documentation files

### When to Create New Files

Only create new documentation files for:
- New major features that need dedicated documentation
- API documentation (e.g., API_REFERENCE.md)
- Deployment guides (e.g., DEPLOYMENT.md)
- Contributing guidelines (e.g., CONTRIBUTING.md)

---

## Update Policy
- This file is the single source of truth for project overview
- Update after every significant architectural or flow change
- Always prefer updating existing files over creating new ones
- Remove all duplicates and consolidate information

---

<a id="adrs"></a>
## ADRs
- [ADR 0001: Gateway-Centric Identity & Authorization Enforcement](docs/adr/0001-gateway-identity-enforcement.md)

---

<a id="semantic-commits--edge-cases"></a>
## Semantic Commits & Edge Cases (NT-20)

### Commit Strategy (Canonical Prefixes)
`feat:` new feature; `fix:` bug fix; `refactor:` structural change; `chore:` maintenance/build; `docs:` documentation; `test:` tests only; `perf:` performance; `style:` formatting; `build:` build system; `ci:` pipeline; `revert:` rollback.

Rules:
- Use scope: `feat(gateway): enforce tenant conflict error (NT-01)`
- Imperative mood; one concern per commit.
- Reference task IDs (NT-XX) in subject or body.
- Multi-module change: list touched services in body.

### Critical Edge Case Summary (Full table in `next_task.md` NT-20)
| ID | Scenario | Expected |
|----|----------|----------|
| EC-01 | Missing tenant claim | 403 TENANT_MISSING (gateway) |
| EC-02 | Multiple tenant groups | 400 TENANT_CONFLICT |
| EC-03 | Invalid tenant format | 400 TENANT_INVALID_FORMAT |
| EC-07 | Missing X-Request-Id | Generated UUID reused in logs/response |
| EC-09 | /auth/tokens null principal | 401 UNAUTHORIZED JSON |
| EC-12 | Missing Cognito property | Startup abort (fail fast) |
| EC-14 | Backend missing tenant header | 403 TENANT_MISSING |
| EC-15 | Backend invalid tenant format | 400 TENANT_INVALID_FORMAT |
| EC-28 | Token refresh includes refreshToken | Field present & distinct access/id |
| EC-30 | Tenant length =3 | Accepted |
| EC-31 | Tenant length =64 | Accepted |
| EC-32 | Tenant length >64 | 400 TENANT_INVALID_FORMAT |
| EC-33 | Invalid token /api/** | 401 UNAUTHORIZED JSON |
| EC-34 | Forbidden action | 403 ACCESS_DENIED JSON |

Deferred / flagged edge cases documented for future hardening: HMAC signature validation, internal token minting, direct backend bypass (EC-25), observability metrics (EC-26).

Refer to `next_task.md` NT-20 for exhaustive list and coverage mapping.

---

## Naming Conventions
- Controllers: `*Controller.java`
- Services: `*.service.ts`
- Tests: `*.spec.ts`, `*Test.java`
- Config: `application-<env>.yml`

---

## Test Coverage Map
- Auth Service: 80%+ unit/integration
- Backend Service: 85%+ unit/integration
- Gateway: 85%+ integration & unit (tenant extraction, header sanitization, request id, logging)
- Frontend: 80%+ unit/e2e

---

## Auth/Security Approach
- All external APIs secured with JWT
- User/tenant context propagated via headers
- SSM Parameter Store for secrets
- No JPA entities exposed externally

---

## 📄 Documentation File Pattern

**IMPORTANT:** Always update existing .md files rather than creating new ones for each change.

### Standard Documentation Files

1. **copilot-index.md** (This file)
   - Single source of truth for project overview
   - Architecture, technology stack, project structure
   - High-level documentation
   - Update after every significant architectural change

2. **CURRENT_STATUS.md**
   - Current implementation status
   - What's working, what's next
   - Service configuration details
   - Recent fixes and troubleshooting
   - Quick reference for developers
   - Update after completing features or fixing issues

3. **IMPLEMENTATION_TASKS.md**
   - Detailed task tracking
   - Implementation progress
   - Technical decisions
   - Update as tasks are completed

4. **HLD.md** (High-Level Design)
   - System architecture
   - Design decisions
   - Component interactions
   - Update when architecture changes

5. **README.md**
   - Getting started guide
   - Quick setup instructions
   - Basic usage
   - Keep concise and user-friendly

### Documentation Rules

- ✅ **DO:** Update existing files with new information
- ✅ **DO:** Keep related information together in the same file
- ✅ **DO:** Use clear section headers for easy navigation
- ❌ **DON'T:** Create new .md files for each change
- ❌ **DON'T:** Duplicate information across multiple files
- ❌ **DON'T:** Create temporary or one-off documentation files

### When to Create New Files

Only create new documentation files for:
- New major features that need dedicated documentation
- API documentation (e.g., API_REFERENCE.md)
- Deployment guides (e.g., DEPLOYMENT.md)
- Contributing guidelines (e.g., CONTRIBUTING.md)

---

## Update Policy
- This file is the single source of truth for project overview
- Update after every significant architectural or flow change
- Always prefer updating existing files over creating new ones
- Remove all duplicates and consolidate information

---

<a id="adrs"></a>
## ADRs
- [ADR 0001: Gateway-Centric Identity & Authorization Enforcement](docs/adr/0001-gateway-identity-enforcement.md)
