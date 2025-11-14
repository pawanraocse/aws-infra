# Platform Service Plan (Tenant & Policy Core)

**Version:** 1.0  
**Date:** 2025-11-12  
**Owner:** Platform Architecture  

## 1. Purpose
Introduce a dedicated `platform-service` microservice to centralize multi-tenant lifecycle, provisioning logic, tenant metadata, database allocation (schema or database mode), policy/permission management, internal token issuance, and cross-cutting observability hooks. This reduces duplication in domain services (`backend-service`, future `logic-service`) and strengthens isolation/security.

## 2. Drivers
| Driver | Problem Today | Platform-Service Benefit |
|--------|---------------|--------------------------|
| Tenant Provisioning | Mixed in backend admin controller | Clear lifecycle API + audit |
| DB-per-tenant migration | Manual orchestration risk | Automated provisioning & migration engine |
| Policy/Authorization | Planned but absent | Single source of truth + versioning |
| Internal Trust Model | Header spoof reliance | Issue signed internal service tokens / assertions |
| Audit & Compliance | Scattered logs | Central audit event pipeline |
| Scaling New Services | Each reimplements tenant logic | Reuse via platform APIs and shared module |

## 3. Responsibilities (Scope)
| Category | Responsibility |
|----------|----------------|
| Tenant Lifecycle | Create, activate, suspend, archive, delete tenants |
| Provisioning | Schema creation OR database allocation + Flyway migrations |
| Admin Account Creation | Create initial admin user in Cognito for each new tenant; assign to tenant group; seed ADMIN role and permissions |
| Metadata Registry | Store tenant state, storage mode, DB credentials refs, limits, SLA tier |
| Policy Management | CRUD roles, permissions, resource-action mappings, feature flags per tenant |
| Token Issuance (Internal) | Mint short-lived signed service tokens (JWT or opaque) for gateway→service trust |
| Audit Events | Emit structured tenant/user lifecycle events (Kafka/SNS) |
| Quotas & Limits | Track usage metrics (API calls, storage) enforce thresholds |
| Key Management (Future) | Associate KMS keys per tenant for encryption contexts |
| Health & Diagnostics | Tenant-level health summaries (DB reachable, migrations status) |

Out-of-Scope (initial phase): Real-time billing, per-tenant report generation, cross-tenant analytics.

## 4. Interaction Diagram (Conceptual)
```
[ Gateway ] --(AuthN JWT)--> [ Auth-Service ] (User session / tokens)
     |\
     | \--(Tenant/Policy lookup + internal token request)--> [ Platform-Service ]
     |                     |
     |                     \--(DB provisioning + metadata)--> [ PostgreSQL / RDS ]
     |--(Enriched headers + internal token)--> [ Backend-Service / Logic-Service ]
```

## 5. APIs (Draft)
| Method | Path | Purpose |
|--------|------|---------|
| POST | /api/tenants | Provision new tenant (schema or DB); create initial admin user in Cognito; assign to tenant group; seed ADMIN role |
| GET | /api/tenants/{id} | Retrieve tenant metadata |
| PATCH | /api/tenants/{id}/suspend | Suspend tenant (write lock) |
| PATCH | /api/tenants/{id}/activate | Reactivate tenant |
| DELETE | /api/tenants/{id} | Archive/delete (soft delete first) |
| GET | /api/tenants/{id}/health | DB connectivity & migration status |
| POST | /api/tenants/{id}/migrate-storage | Switch schema→database or vice versa (controlled) |
| GET | /api/policies | List role → permissions mappings |
| POST | /api/policies | Create/update policy document (JSON DSL) |
| GET | /api/policies/resolve?role=...&resource=...&action=... | Policy decision API (internal) |
| POST | /api/internal-tokens | Issue internal service token (gateway only) |

## 6. Data Model (Core Tables / Entities)
`tenant`:
- id (PK)
- name
- status (ACTIVE, SUSPENDED, ARCHIVED, PROVISION_ERROR)
- storage_mode (SCHEMA, DATABASE)
- jdbc_url (nullable if schema mode)
- db_user_secret_ref
- created_at / updated_at
- sla_tier (STANDARD, PREMIUM)
- last_migration_version

`tenant_migration_history`:
- id, tenant_id, version, started_at, ended_at, status, notes

`policy_document`:
- id, tenant_id (nullable for global), version, created_at, json_blob (full permission matrix)

`internal_token_signature_key` (future for rotation):
- id, current_key_id, next_key_id, rotated_at

`tenant_quota`:
- tenant_id, api_call_limit, storage_limit_gb, period_start, period_end, usage_api_calls, usage_storage_gb

## 7. Internal Token Strategy (Phase 2)
- Issued by Platform-Service upon gateway request (after successful user JWT validation).
- Contains: userId, tenantId, roles, issuedAt, expiry (~2-5 min), audience=[service-name], signature (rotating key).
- Domain services validate signature using cached JWK served by platform-service (`/api/jwks/internal`).

## 8. Migration from Current State
| Step | Action |
|------|--------|
| 1 | Create platform-service module (Spring Boot) + registry tables |
| 1a | Implement admin account creation logic: call Cognito AdminCreateUser, assign to tenant group, seed ADMIN role/permissions |
| 2 | Move tenant provisioning endpoints from backend-service → platform-service |
| 3 | Introduce platform-shared module (ErrorResponse, header constants, policy DTOs) |
| 4 | Gateway: add policy decision call before routing protected endpoints (feature-flag) |
| 5 | Add internal token issuance endpoint & gateway integration (optional early flag) |
| 6 | Deprecate backend direct provisioning controller; keep read-only tenant info fallback (feature flag) |
| 7 | Implement storage mode migration path (schema ↔ database) |
| 8 | Document and add ADR for internal token model |

## 9. Required Changes (Existing Services)
| Service | Change |
|---------|--------|
| Gateway-Service | Add client for policy decision & internal token request; cache decisions; feature flags: `gateway.policy.enabled`, `gateway.internal-token.enabled` |
| Auth-Service | No direct change (may delegate role/permission mgmt to platform-service later) |
| Backend-Service | Remove provisioning endpoints; depend only on enriched headers + internal token (when enabled) |
| Future Logic-Service | Same pattern as backend-service (stateless header + internal token validation) |
| Shared Module | Introduce `platform-shared` with DTOs: TenantDto, PolicyDecisionRequest/Response, ErrorResponse, InternalTokenClaims |

## 10. Shared Module Contents (Initial)
- `TenantDto` (id, name, status, storageMode, slaTier)
- `ProvisionTenantRequest` (name, storageMode, slaTier)
- `PolicyDecisionRequest` (tenantId, roleSet, resource, action, attributes map)
- `PolicyDecisionResponse` (allowed, reason, matchedRule)
- `InternalTokenClaims` (userId, tenantId, roles, expiresAt, issuedAt, audience)
- `ErrorResponse` (standard schema)
- `HeaderNames` (constants)

## 11. Policy DSL (Example JSON)
```json
{
  "version": 1,
  "rules": [
    { "roles": ["ADMIN"], "resource": "tenant", "actions": ["CREATE","SUSPEND","DELETE"], "effect": "ALLOW" },
    { "roles": ["USER"], "resource": "entry", "actions": ["READ","CREATE"], "effect": "ALLOW" },
    { "roles": ["USER"], "resource": "entry", "actions": ["DELETE"], "effect": "DENY" }
  ],
  "defaultEffect": "DENY"
}
```

## 12. Feature Flags (New)
| Flag | Default | Purpose |
|------|---------|---------|
| `gateway.policy.enabled` | false | Enable policy decision before routing |
| `gateway.internal-token.enabled` | false | Replace raw user headers with signed internal token |
| `platform.db-per-tenant.enabled` | false | Allow provisioning new tenants with dedicated DB |
| `platform.policy.edit.enabled` | true | Permit updating policy docs |
| `platform.internal-token.jwk-rotate.enabled` | false | Enable key rotation automation |

## 13. Observability Additions
- Metrics:
  - `platform.tenants.count{status}`
  - `platform.provision.duration{storageMode}`
  - `platform.policy.decisions.total{allowed}`
  - `platform.internal.token.issued.total`
  - `platform.db.migrations.total{status}`
- Logs: Structured `platform_event` lines for provisioning, migration, policy updates.
- Tracing: Add trace/span decorators for provisioning flows.

## 14. Security Considerations
| Concern | Mitigation |
|---------|------------|
| Privilege escalation via policy API | Strict role requirement + versioned policy changes + audit events |
| Token forgery | JWKS served with rotation + short TTL internal tokens |
| Tenant DB credential leakage | Store only secret reference; fetch at runtime with least-privileged IAM role |
| Migration races | Lock tenant row during migration operations |
| Excessive policy evaluations | Cache decisions in gateway (LRU, TTL ~60s) |

## 15. Rollout Strategy
| Phase | Scope | Success Metric |
|-------|-------|----------------|
| P1 | Platform-service skeleton + tenant read APIs | Service healthy; registry mirrored |
| P2 | Move provisioning + schema creation + admin account creation | 100% new tenants via platform-service; each tenant has initial admin user |
| P3 | Policy storage + decision API | Gateway calling decision API for protected routes |
| P4 | Internal token issuance & gateway integration | Reduced header surface; tokens validated |
| P5 | DB-per-tenant provisioning | Selected tenants migrated successfully |

Rollback: Disable new features via flags; retain legacy provisioning path until decommissioned.

## 16. ADRs to Add
| ADR | Topic |
|-----|-------|
| 0004 | Platform service introduction & boundaries |
| 0005 | Policy decision DSL & evaluation strategy |
| 0006 | Internal token vs HMAC decision |
| 0007 | Database-per-tenant migration approach |

## 17. Open Questions
| ID | Question |
|----|----------|
| Q1 | Policy evaluation caching strategy (size, eviction)? |
| Q2 | Internal token format (JWT vs opaque + introspection)? |
| Q3 | Key rotation interval for internal tokens? |
| Q4 | Do we need multi-region active for platform-service first? |
| Q5 | Resource naming standard for policies (noun vs URI)? |
| Q6 | DB-per-tenant backup retention differences per SLA tier? |

## 18. Acceptance Criteria
- Platform-service deployed with health & tenant read endpoint.
- Backend provisioning removed after P2 (flag controlled).
- Every new tenant is provisioned with an initial admin account in Cognito, assigned to the correct group, and seeded with ADMIN role/permissions.
- Gateway can optionally call policy decision endpoint (flag). 
- Internal token prototype documented & ADR approved before implementation.
- Monitoring dashboards include platform-service tenant & migration metrics.

---

## 19. Implementation & Progress Monitoring Plan

This section details the step-by-step implementation of the platform-service, ensuring production-grade quality, traceability, and alignment with modern practices. Progress should be tracked and updated after each milestone.

### Steps

1. **Service Bootstrap & Project Setup**
   - Create `platform-service/` module (Spring Boot 3.x, Java 21+).
   - Add to root `pom.xml` as a new Maven module.
   - Add Dockerfile for platform-service; update `docker-compose.yml` to include platform-service with healthcheck, environment variables, and network.
   - Add Flyway dependency and configuration for DB migrations.
   - Update copilot-index.md with new service entry and flow.

2. **Core Data Model & Migrations**
   - Implement entities: Tenant, TenantMigrationHistory, PolicyDocument, InternalTokenSignatureKey, TenantQuota.
   - Write Flyway migration scripts for all tables.
   - Seed baseline roles/permissions for new tenants.

3. **API Layer & DTOs**
   - Define DTOs for tenant provisioning, admin account creation, policy management, internal token issuance.
   - Implement REST controllers for all endpoints in PLATFORM_SERVICE_PLAN.md (tenant lifecycle, policy CRUD, decision, internal tokens, health).
   - Ensure OpenAPI documentation is generated and secured.

4. **Tenant Provisioning Logic**
   - Implement service logic for DB/schema creation per tenant.
   - Integrate with AWS Cognito: create initial admin user, assign to tenant group, seed ADMIN role.
   - Store DB credentials in AWS SSM Parameter Store.
   - Emit audit events for all provisioning actions.

5. **Policy & Authorization Engine**
   - Implement policy document CRUD and evaluation logic (JSON DSL).
   - Expose policy decision API for gateway-service integration.
   - Cache decisions (Caffeine/Redis); emit events for changes.

6. **Internal Token Issuance**
   - Implement JWT minting for internal tokens, JWK endpoint for validation.
   - Integrate with gateway-service for token issuance and validation.
   - Add key rotation logic and feature flags.

7. **Observability, Security, and Validation**
   - Add structured logging (SLF4J + Logback), metrics (Micrometer), tracing (OpenTelemetry).
   - Implement health endpoints and readiness/liveness probes.
   - Enforce input validation, error handling, and audit trails.
   - Secure all endpoints with JWT; validate tenant context.

8. **Testing & Documentation**
   - Write unit and integration tests (JUnit 5, Mockito, Testcontainers).
   - Document all APIs, flows, and edge cases in copilot-index.md and ADRs.
   - Update test coverage map and semantic commit history.

9. **Infrastructure & Integration**
   - Update `docker-compose.yml`:
     - Add platform-service block with build context, ports, healthcheck, environment, and network.
     - Ensure dependencies (PostgreSQL, SSM, Cognito) are available.
   - Update root `pom.xml`:
     - Add platform-service as a module.
     - Add required dependencies (Spring Boot, Flyway, AWS SDK, JWT, OpenAPI, Micrometer).
   - Ensure platform-service is included in CI/CD pipeline.

10. **Progress Monitoring & Milestones**
    - Track each step as a milestone in copilot-index.md and/or a dedicated progress file.
    - Mark completion of: project setup, DB migrations, API endpoints, Cognito integration, policy engine, internal token, observability, tests, infra changes.
    - Use semantic commits for traceability (e.g., `feat(platform): add tenant provisioning API (PS-01)`).
    - Update documentation and test coverage after each milestone.

### Further Considerations

1. Should admin onboarding use email/SMS or manual activation? (recommend email with temp password)
2. How will policy evaluation scale for large tenants? (recommend Redis/Caffeine hybrid cache)
3. Should DB-per-tenant be default, or feature-flagged for premium tenants?
4. Ensure rollback strategy for each major change (feature flags, legacy path retention).
5. Document all integration points and update copilot-index.md after each significant change.

## 19.1 Progress Tracker (Milestones)

| ID | Milestone | Status | Notes |
|----|-----------|--------|-------|
| PS-01 | Module scaffold (pom, application.yml, Dockerfile) | DONE | Compiles; added to docker-compose |
| PS-02 | Flyway V1 migrations (registry tables) | DONE | pgcrypto enabled; separate Flyway table |
| PS-03 | Basic REST API (POST /api/tenants, GET /api/tenants/{id}) | DONE | DTOs/controllers/services wired |
| PS-04 | Eureka registration & health endpoint | DONE | Actuator health exposed; compose healthcheck |
| PS-05 | OpenAPI config & Swagger UI | DONE | springdoc configured; context path `/platform` |
| PS-06 | Security (OAuth2 resource server) | DONE | JWT issuer-uri configured; `/actuator/**`, docs permitted |
| PS-06a | Swagger security allowance | DONE | `/swagger-ui.html`, `/swagger-ui/**`, `/webjars/**` permitted |
| PS-07 | Docker Compose integration | DONE | `platform-service` block added |
| PS-08 | Root pom module update | DONE | `platform-service` added to `<modules>` |
| PS-09 | README & run instructions | DONE | Local run & health commands documented |
| PS-10 | DB/schema provisioning logic | DONE | Implemented TenantProvisioner + TenantProvisioningService; per-tenant schema creation, migration history, Micrometer counters, Testcontainers integration |
| PS-11 | Admin account creation (Cognito) | PENDING | Integrate AdminCreateUser + group assignment |
| PS-12 | Policy storage & decision API | PENDING | CRUD + resolve endpoint |
| PS-13 | Internal token issuance & JWK | PENDING | JWT minting + JWK hosting |
| PS-14 | Metrics & observability | PARTIAL | Provisioning counters/timer + active gauge done; tracing pending |
| PS-15 | Tests (unit/integration) | PARTIAL | Unit + provisioning integration tests added; edge cases & failure paths pending |
| PS-16 | Cross-service cleanup (remove redundant tenant/policy logic) | PENDING | Backend/gateway/auth refactors staged |
| PS-17 | Communication client & shared module rollout | PENDING | platform-client + platform-shared DTO adoption |

### 19.1.2 Immediate Next Tasks
1. PS-11: Implement `CognitoAdminService` for initial tenant admin creation (AdminCreateUser + group assignment + audit event).
2. PS-12: Policy storage & decision API (JSON DSL + evaluation service + caching layer).
3. PS-13: Internal token issuance (JWT + JWK endpoint) and gateway integration behind feature flag.
4. PS-14: Add tracing spans + additional metrics (status-based counts, histogram for provision duration).
5. PS-15: Expand tests (DATABASE mode flag rejection, PROVISION_ERROR retry scenario, validation failures).
6. PS-16: Backend-service cleanup (remove legacy provisioning code, adopt PlatformClient).
7. PS-17: Introduce `platform-shared` module & replace duplicate ErrorResponse/Header constants.

### 19.1.4 Provisioning Implementation Addendum (Completed PS-10)
- Components: `TenantProvisioner` (schema creation), `TenantProvisioningServiceImpl` (orchestration), `GlobalExceptionHandler`, domain exceptions.
- Metrics added: `platform.tenants.provision.attempts`, `platform.tenants.provision.success`, `platform.tenants.provision.failure`, `platform.tenants.migration.duration` timer, active tenants gauge.
- Persistence: `tenant` and `tenant_migration_history` tables seeded via Flyway V1; migration history rows recorded with STARTED/SUCCESS/FAILED.
- Tests: Unit (happy path + duplicate conflict), Integration (Testcontainers via `BaseIntegrationTest` abstract class). DATABASE mode currently disabled by flag.
- Logging: Structured `tenant_provisioned` / `tenant_provision_failed` messages include tenantId, storageMode, durationMs.
- Error Handling: Returns standardized ErrorResponse (conflict, validation, provisioning error).
- Security: Resource server JWT configured; test profile overrides with permit-all filter chain.

### 19.1.5 Remaining Gaps Post PS-10
| Gap | Description | Planned Fix |
|-----|-------------|------------|
| Per-schema domain migrations | Placeholder only | Programmatic Flyway per schema (PS-14 extension) |
| DATABASE mode provisioning | Feature flag disabled | Implement admin DataSource + secret storage (PS-11/PS-14) |
| Retry endpoint | Missing for PROVISION_ERROR | Add `/api/tenants/{id}/retry` (PS-15) |
| Audit event emission | Logging only | Introduce async publisher (Kafka/SNS) (PS-14) |
| PlatformClient adoption | Other services still direct | Implement shared REST client (PS-17) |

---
**End of PLATFORM_SERVICE_PLAN.md**
