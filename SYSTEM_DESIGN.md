# System Design & Service Responsibilities

**Version:** 1.0  
**Date:** 2025-11-12  
**Owner:** Architecture / Platform Security  

---
## 1. Purpose
This document provides a comprehensive architectural blueprint of the multi-tenant AWS application, detailing service boundaries, responsibilities, security flows, data model, and operational considerations. It complements `HLD.md` with deeper context and implementation guidance.

---
## 2. High-Level Overview
A multi-tenant platform using:
- AWS Cognito for Authentication (OIDC / JWT) & group-based tenant mapping.
- Spring Cloud Gateway for centralized identity enforcement, header enrichment, and routing.
- Auth-Service for user lifecycle (signup/login/logout) and future authorization policy storage.
- Backend-Service for tenant-scoped business logic & data using schema-per-tenant strategy.
- Eureka Server for service discovery.
- PostgreSQL with `public` (registry) + `tenant_<id>` schemas.

---
## 3. Context Diagram (Condensed)
```
[ Angular Frontend ] --(HTTP + Bearer JWT)--> [ API Gateway ] --(Routed w/ enriched headers)--> [ Auth-Service | Backend-Service ] --> [ PostgreSQL ]
                                          |--> [ Eureka ]
                                          |--> [ AWS Cognito (JWKS, OAuth2) ]
```

---
## 4. Service Responsibilities
### 4.1 Gateway-Service (Port 8080)
Central security boundary. Functions:
- Validate Cognito JWT (issuer + JWKS).
- Extract tenant from `cognito:groups` (`tenant_<id>`) or `custom:tenant_id` claim.
- Fail-closed on missing/invalid/conflicting tenant (NT-01).
- Sanitize spoofable headers (NT-02).
- Generate `X-Request-Id` (NT-03).
- Enrich downstream headers: `X-User-Id`, `X-Username`, `X-Email`, `X-Tenant-Id`, optionally `X-Authorities`.
- Circuit breaker, retry, CORS.
- Structured JSON logging (completion/error lines).
- Future: Authorization checks via Auth-Service (policy API), optional HMAC signing (deferred).

### 4.2 Auth-Service (Port 8081)
Identity lifecycle & token handling.
- User signup (Cognito admin API or hosted UI).
- User login (authorization code flow preferred; admin password flow deprecated behind flag `auth.direct-login.enabled`).
- `/tokens` endpoint exposes properly separated `accessToken`, `idToken`, optional `refreshToken`.
- `/me` returns current user details from OIDC principal.
- Future: Role/permission storage, policy evaluation, audit events.
- Provides structured error & domain-specific exceptions.

### 4.3 Backend-Service (Port 8082)
Tenant-scoped business operations.
- Consumes enriched headers from gateway; **does not perform JWT validation**.
- Enforces presence & format of `X-Tenant-Id` (403 on missing). No fallback.
- Schema-per-tenant persistence: `public.tenants` registry + `tenant_<id>` schemas.
- Tenant provisioning endpoint (`/api/admin/tenants`) guarded by minimal role check (`ROLE_ADMIN`) only if passed from gateway.
- Future: Replace role trust with internal token / signed assertion to reduce header spoof reliance.

### 4.4 Eureka-Server (Port 8761)
- Service registration & discovery.
- Instance metadata for region/version.

### 4.5 External: AWS Cognito
- OIDC authorization server (issuer URI, JWKS).
- User Pool groups align with tenant membership (`tenant_<id>`).
- Provides tokens with `sub`, `email`, optional `custom:tenant_id` fallback.

---
## 5. Identity & Security Flow (Gateway-First)
1. Client sends `Authorization: Bearer <JWT>` to gateway.
2. Gateway validates signature & issuer.
3. Gateway extracts tenant (groups or custom claim). Validation:
   - Multiple tenant groups → 400 `TENANT_CONFLICT`.
   - Invalid tenant format (regex `^[a-zA-Z0-9_-]{3,64}$`) → 400 `TENANT_INVALID_FORMAT`.
   - Missing → 403 `TENANT_MISSING`.
4. Gateway sanitizes inbound identity headers.
5. Gateway adds enriched headers only if tenant resolved.
6. Backend trusts enriched headers and only validates mandatory `X-Tenant-Id` and format.
7. Auth-Service uses OAuth2 principal to render user-centric endpoints; no duplicate JWT parsing.

**Security Boundary Principle:** All authentication & (future) authorization decisions centralized at gateway + auth-service. Backend remains a trust domain consumer.

---
## 6. Multi-Tenancy Model (Current: Schema-per-Tenant)
| Aspect | Strategy |
|--------|----------|
| Isolation | Schema-per-tenant (`tenant_<id>`) |
| Registry | `public.tenants` table stores tenant metadata |
| Provisioning | Admin endpoint triggers schema creation + migrations |
| Migration | Flyway executed per new tenant schema from template scripts |
| Access scoping | Hibernate / DataSource routing by `TenantContext` (set from headers) |
| Future | Move to shared connection pool with schema switching; or separate DB per large tenant |

**Tenant ID Source Hierarchy:** `cognito:groups` > `custom:tenant_id` > reject.

### 6.1 Alternative Model: Database-per-Tenant (Requested Enhancement)
**Goal:** Provide stronger isolation (blast radius, noisy neighbor prevention, compliance boundary) by provisioning an entirely separate physical (or logical) database instance per tenant.

#### 6.1.1 Rationale & When to Choose
| Trigger | Indicator |
|---------|-----------|
| Compliance | Tenant requires data-at-rest encryption keys isolated (per KMS key) |
| Performance | Large tenants saturate shared instance (CPU / I/O heavy) |
| Data Volume | > (configurable threshold, e.g. 50GB) per tenant or high growth projections |
| Noisy Neighbor Risk | Latency spikes correlated to specific tenants |
| SLA Tiering | Premium tenants offered dedicated performance guarantees |
| Lifecycle Ops | Need independent backup/restore cadence or region-level failover |

#### 6.1.2 Architecture Changes
| Aspect | Schema-per-Tenant | Database-per-Tenant |
|--------|-------------------|---------------------|
| Isolation | Same cluster, separate schema | Separate database (instance, cluster, or serverless DB) |
| Provisioning Complexity | Low | Higher (infrastructure + credentials) |
| Cross-Tenant Query Risk | Possible if misconfigured | Virtually none |
| Cost Footprint | Lower (shared resources) | Higher (per DB overhead) |
| Backup Strategy | Shared schedule | Per-tenant customizable |
| Scaling | Global scaling impacts all | Per-tenant scaling (vertical/horizontal) |
| Migration Overhead | One Flyway run per schema | One Flyway run per database |

#### 6.1.3 Provisioning Workflow (Database-per-Tenant)
1. Generate unique tenant ID (validate regex).
2. Allocate database (e.g. AWS RDS/Aurora or serverless cluster): name pattern `tenant_<id>`.
3. Create DB user with least privileges (schema owner only) & rotate password (store in AWS Secrets Manager or Parameter Store).
4. Run Flyway migrations against the new database (baseline + versioned scripts).
5. Register tenant in `public.tenants` (global registry) with connection metadata (JDBC URL, username secret ref, encryption flags, status ACTIVE).
6. Add user to Cognito group `tenant_<id>`.
7. Warm connection pool (optional) or lazy-init on first request.

#### 6.1.4 Runtime Connection Resolution
Two options:
- Lazy Lookup: On first request for tenant, fetch connection metadata from registry, initialize HikariDataSource, cache in ConcurrentHashMap keyed by tenantId.
- Pre-Warm: At startup, load active tenants (risk: high memory footprint if many small tenants).

Eviction Policy: Close & remove idle DataSource if last access > configurable TTL (e.g. 30 minutes) AND pool size == 0.

#### 6.1.5 Flyway Strategy
- Maintain shared migration scripts; parameterize schema differences minimal.
- Each new database runs full migration set from version 0.
- Global migrations affecting all tenants: iterate over registry and run migrations sequentially or in parallel with throttling.

#### 6.1.6 Configuration Flag
Add application property to backend-service & future domain services:
```
multitenancy:
  mode: schema   # values: schema | database
  datasource-cache:
    eviction-ttl-minutes: 30
    max-pools: 200
```
Behavior:
- `schema`: existing ThreadLocal + schema switching (current).
- `database`: dynamic DataSource routing (DataSourceResolver) based on incoming `X-Tenant-Id`.

#### 6.1.7 Class & Component Adjustments (Future)
| Component | New Responsibility |
|-----------|--------------------|
| `TenantDataSourceRegistry` | Maintain active tenant DataSource pool (create/evict). |
| `TenantConnectionResolver` | Resolve DataSource per request based on tenantId. |
| `TenantMigrationService` | Execute Flyway for new or updated tenant databases. |
| `TenantProvisioningService` | Extended provisioning to allocate DB + credentials. |

#### 6.1.8 Security & Secrets
- Store DB credentials per tenant in AWS Secrets Manager (secret naming: `/tenants/<tenantId>/db-credential`).
- Rotate credentials periodically (e.g. every 90 days); on rotation update registry & gracefully drain old pool.
- Encrypt backups per database (KMS key per tenant if required).

#### 6.1.9 Operational Considerations
| Concern | Mitigation |
|---------|------------|
| Connection Explosion | Set `max-pools`; evict idle pools; consider serverless Aurora |
| Migration Duration | Parallelize with limit; track status table `public.tenant_migrations` |
| Secret Rotation Impact | Implement dual-credential window (old + new) until pools refreshed |
| Monitoring | Per-tenant metrics: active connections, latency, pool utilization |
| Incident Isolation | Ability to lock single tenant DB (set status SUSPENDED) without global downtime |

#### 6.1.10 Pros / Cons Summary
- Pros: Strong isolation, tailored scaling, compliance alignment.
- Cons: Higher operational complexity, cost overhead, risk of connection proliferation, migration coordination required.

#### 6.1.11 Migration Path From Schema-per-Tenant
1. Identify candidate tenants (large / regulated).
2. Provision dedicated DB & migrate data via export/import (or pg_dump to new instance).
3. Update registry entry to switch mode for tenant (include flag `storageMode=DATABASE`).
4. Deploy service with fallback: if tenant has `storageMode=DATABASE`, route to dedicated DataSource else schema switching.
5. After validation period, decommission old schema.

#### 6.1.12 Edge Cases
| Case | Handling |
|------|----------|
| Tenant DB creation fails mid-migration | Mark tenant status PROVISION_ERROR; retry or rollback registry entry |
| DataSource pool init failure | Return 503 with code `TENANT_DB_UNAVAILABLE`; alert Ops |
| Credential rotation in progress | Allow both credential versions until rotation finalization flag set |
| Max pools reached | Reject new tenant provisioning with 429 or queue provisioning task |

#### 6.1.13 Additional Error Codes (if Database Mode Enabled)
- `TENANT_DB_UNAVAILABLE`
- `TENANT_DB_PROVISION_ERROR`
- `TENANT_DB_MIGRATION_FAILED`

#### 6.1.14 Monitoring Additions
| Metric | Description |
|--------|-------------|
| `tenant.datasource.active` | Count of active DataSource pools |
| `tenant.db.migration.duration` | Time for per-tenant DB migration |
| `tenant.db.errors.total` | Count of DB provisioning/migration errors |
| `tenant.db.evictions.total` | DataSource pools evicted due to inactivity |

#### 6.1.15 Decision Matrix (Quick Reference)
| Criterion | Schema-per-Tenant | Database-per-Tenant |
|-----------|-------------------|---------------------|
| Tenants < 200 small | ✅ | ❌ |
| Large data (>50GB) single tenant | ⚠️ (possible) | ✅ |
| Regulatory isolation | ⚠️ | ✅ |
| Lowest cost priority | ✅ | ❌ |
| Predictable per-tenant scaling | ⚠️ | ✅ |
| Simplicity for MVP | ✅ | ❌ |

---
## 7. Gateway Filter Ordering (Desired Chain)
1. `HeaderSanitizingGlobalFilter` (remove spoofed headers).
2. `RequestIdGlobalFilter` (ensure correlation id).
3. (Future) Rate Limiter / DDoS guard.
4. JWT validation (Spring Security resource server).
5. `JwtAuthenticationGatewayFilterFactory` (tenant extraction + header enrichment).
6. (Optional future) `HeaderSignatureFilter` (HMAC signing) – deferred.
7. `EnhancedLoggingGlobalFilter` (emit final structured log line).

---
## 8. Standard Error Response Schema
```json
{
  "timestamp": "2025-11-12T10:15:30Z",
  "status": 403,
  "code": "TENANT_MISSING",
  "message": "Tenant claim missing",
  "requestId": "uuid-value"
}
```
**Codes:** `TENANT_MISSING`, `TENANT_CONFLICT`, `TENANT_INVALID_FORMAT`, `UNAUTHORIZED`, `ACCESS_DENIED`, `INVALID_CREDENTIALS`, `USER_NOT_FOUND`, `USER_EXISTS`, `OAUTH2_FAILURE`, `SIGNATURE_INVALID` (deferred), `INTERNAL_ERROR`.

---
## 9. Configuration & Feature Flags
| Property | Default | Purpose |
|----------|---------|---------|
| `security.gateway.fail-on-missing-tenant` | true | Enforce tenant presence |
| `security.gateway.sanitize-headers` | true | Strip spoofable headers |
| `security.gateway.hmac.enabled` | false | Enable HMAC signature (deferred) |
| `auth.direct-login.enabled` | false | Legacy password flow toggle |
| `auth.tokens.compat.enabled` | false | (Reserved) backward compatibility |
| `security.backend.allow-missing-tenant` | false | Temporary rollback for tenant enforcement |
| `logging.enhanced` | true | Structured logging filter |
| `auth.cognito.singleton.enabled` | true | Reuse Cognito client bean |
| `platform.shared.enabled` | true | Shared module usage |

---
## 10. Data Layer Summary
**Schemas:**
- `public`: tenant registry (`tenants`), audit tables.
- `tenant_<id>`: isolated business entities (`entries`, `notes`, etc.).

**Provisioning Steps:**
1. Insert into `public.tenants`.
2. Create schema `tenant_<id>`.
3. Execute template migrations (Flyway).
4. Assign user to Cognito group `tenant_<id>`.

---
## 11. Logging & Observability
**Gateway Log Line:**
```
gateway_log {"event":"request_completed","requestId":"...","userId":"...","tenantId":"...","method":"GET","path":"/api/entries","status":200,"durationMs":42}
```
**Metrics (Future):**
- `gateway.requests.total{outcome=ALLOW|DENY,reason}`
- `gateway.tenant.missing.total`
- `auth.tokens.issued.total`
- `backend.tenant.missing.total`

**Tracing:** Micrometer + Zipkin (Brave). RequestId aligns with traceId when available.

---
## 12. Security Considerations
| Risk | Mitigation |
|------|------------|
| Header spoofing | Gateway sanitization (NT-02) |
| Tenant escalation via multiple groups | Conflict detection (400) |
| Weak password flow | Disabled by default / hosted UI preferred |
| Missing tenant causing silent default | Fail-closed; no `default` fallback |
| Replay/internal tampering | Future internal token or HMAC signing |
| Error leakage | Standardized minimal JSON errors |

**Principle of Least Privilege:** Backend never decodes JWT or manages authZ decisions.

---
## 13. Testing Strategy
| Layer | Tests |
|-------|-------|
| Gateway | Unit: tenant extraction, sanitization; Integration: 403/400 flows |
| Auth-Service | Unit: token separation, exception mapping; Integration: OAuth2 login |
| Backend | Integration: tenant enforcement, schema routing |
| Data | Migration verification per tenant schema |
| Performance (Future) | Load tests for provisioning & high-concurrency CRUD |

---
## 14. Future Roadmap
1. Shared security module (`platform-shared`) – unify DTOs & error codes.
2. Internal signed service token to replace raw header trust.
3. Policy-based authorization (resource + action) stored in Auth-Service.
4. Metrics & alerting: per-tenant latency, error ratios.
5. Audit trail events (login, CRUD, provisioning).
6. Tenant lifecycle automation (suspend, archive, delete flows).
7. Dynamic rate limiting per tenant.

---
## 15. ADR References (Planned)
| ADR | Title | Status |
|-----|-------|--------|
| 0001 | Gateway identity enforcement | Pending creation |
| 0002 | Deprecation of admin password flow | Draft |
| 0003 | Internal token/HMAC strategy | Future |

---
## 16. Glossary
| Term | Definition |
|------|------------|
| Tenant | Logical customer grouping with isolated schema |
| Tenant ID | Unique identifier used in schema name and Cognito group suffix |
| JWT | JSON Web Token from Cognito representing identity & claims |
| Enrichment | Process of adding identity headers post validation in gateway |
| Fail-Closed | Reject request if required identity/tenant info cannot be derived |
| Hosted UI | Cognito-managed login page using OAuth2 authorization code flow |

---
## 17. Non-Goals (Current Phase)
- Real-time permission revocation.
- Cross-tenant querying.
- Data sharding beyond schema separation.
- Multi-region active-active replication.

---
## 18. Open Questions
| ID | Question | Target Decision |
|----|----------|-----------------|
| Q1 | When to fully remove admin password grant? | After hosted UI adoption >95% |
| Q2 | HMAC vs internal JWT for service identity? | Evaluate in security workshop |
| Q3 | Tenant deletion strategy (data retention)? | Define compliance policy |
| Q4 | Introduce caching for tenant metadata? | If registry access > threshold |
| Q5 | Threshold for switching a tenant to dedicated DB (data size / RPS)? | Define in ops playbook |
| Q6 | Do we need per-tenant KMS keys and audit logs segregation? | Compliance review |
| Q7 | Strategy for automated idle DB teardown (cost optimization)? | Cost mgmt plan |
| Q8 | Blue/green migration method between schema and dedicated DB? | Data migration ADR |

---
## 19. Acceptance Criteria for This Architecture Doc
- Clear separation of responsibilities per service.
- Security flow documents fail-closed behavior.
- Multi-tenancy strategy & provisioning steps explicit.
- Standard error schema defined.
- Feature flags enumerated.
- Roadmap & open questions captured.

---
**End of SYSTEM_DESIGN.md**
