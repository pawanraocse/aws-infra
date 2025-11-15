# Platform Service Plan (Tenant & Policy Core)

**Version:** 1.1  
**Date:** 2025-11-14  
**Owner:** Platform Architecture  

## 1. Purpose
Centralize multi-tenant lifecycle, provisioning logic, tenant metadata, database-per-tenant allocation (preferred isolation), policy/permission management, internal token issuance, and cross-cutting observability hooks. Reduces duplication in domain services and strengthens isolation/security.

## 2. Drivers
| Driver | Problem Today | Platform-Service Benefit |
|--------|---------------|--------------------------|
| Tenant Provisioning | Mixed in backend admin controller | Clear lifecycle API + audit |
| DB-per-tenant migration | Manual orchestration risk | Automated provisioning & migration engine |
| Policy/Authorization | Planned but absent | Single source of truth + versioning |
| Internal Trust Model | Header spoof reliance | Signed internal service tokens |
| Audit & Compliance | Scattered logs | Central audit event pipeline |
| Scaling New Services | Each reimplements tenant logic | Reuse via platform APIs and shared module |

## 3. Responsibilities (Scope)
| Category | Responsibility |
|----------|----------------|
| Tenant Lifecycle | Create, activate, suspend, archive, delete tenants |
| Provisioning | Database-per-tenant OR schema (fallback) + baseline migrations |
| Admin Account Creation | Seed initial admin user & role in Cognito |
| Metadata Registry | Store tenant state, storage mode, JDBC URL, SLA tier, migration version |
| Policy Management | CRUD roles, permissions, resource-action mappings |
| Internal Token Issuance | Mint short-lived signed service tokens |
| Audit Events | Emit structured lifecycle events |
| Quotas & Limits | Track usage metrics and enforce limits |
| Health & Diagnostics | Tenant DB connectivity + migration status |

Out-of-Scope (initial): Real-time billing, cross-tenant analytics.

## 4. Interaction Diagram (Conceptual)
```
[ Gateway ] --(JWT)--> [ Auth-Service ]
     |\
     | \--> [ Platform-Service ] --(Provision / Metadata)---> [ Master DB ]
     |                                  \--> [ Tenant DBs ] (tenant_<id>)
     |--> [ Backend-Service ] --(tenant routing)--> [ Tenant DB ]
```

## 5. APIs (Initial Delivered + Planned)
| Method | Path | Purpose | Status |
|--------|------|---------|--------|
| POST | /api/tenants | Provision new tenant | ✅ (PS-10) |
| GET | /api/tenants/{id} | Retrieve tenant metadata | ✅ |
| PATCH | /api/tenants/{id}/suspend | Suspend tenant | Planned |
| PATCH | /api/tenants/{id}/activate | Reactivate tenant | Planned |
| DELETE | /api/tenants/{id} | Archive/delete | Planned |
| GET | /api/tenants/{id}/health | DB & migration status | Planned |
| POST | /api/tenants/{id}/retry | Retry failed provisioning | Planned (PS-15) |
| POST | /api/tenants/{id}/migrate-storage | schema ↔ database | Planned |
| POST | /api/internal-tokens | Issue internal service token | Planned (PS-13) |
| GET | /api/policies | List policies | Planned (PS-12) |
| POST | /api/policies | Create/update policy | Planned (PS-12) |
| GET | /api/policies/resolve | Policy decision | Planned (PS-12) |

## 6. Data Model (Master DB)
`tenant`:
- id (PK)
- name
- status (ACTIVE, SUSPENDED, ARCHIVED, PROVISION_ERROR)
- storage_mode (DATABASE, SCHEMA)
- jdbc_url (nullable if SCHEMA)
- sla_tier (STANDARD, PREMIUM)
- last_migration_version
- created_at / updated_at

`tenant_migration_history`:
- id, tenant_id, version, applied_at, status (SUCCESS/FAILED), notes

Future tables: `policy_document`, `internal_token_signature_key`, `tenant_quota`.

## 7. Provisioning Strategy
### Action Chain Pattern (SOLID)
Each provisioning step is encapsulated in a `TenantProvisionAction` implementation with single responsibility:
| Action | Responsibility | Order |
|--------|----------------|-------|
| StorageProvisionAction | Physical DB / schema creation and JDBC URL assignment | 10 |
| MigrationHistoryAction | Baseline migration tracking record creation | 20 |
| AdminUserSeedAction | Cognito admin user & role seed | 30 |
| AuditLogAction | Emit audit event | 90 |

- Orchestration performed by `TenantProvisioningServiceImpl`: loops ordered list.
- Fail-fast: On exception, status set to `PROVISION_ERROR`; counters updated; error logged.
- Extensibility: Adding new post-actions only requires a new bean with `@Order` and implementation; no modification of existing logic (Open/Closed Principle).

### Database-per-Tenant (Preferred Isolation)
Flow when `storageMode=DATABASE` and flag `platform.db-per-tenant.enabled=true`:
```
1. Insert tenant row (status=PROVISIONING)
2. Create dedicated database (e.g., tenant_a12bcdef) via admin connection
3. Run Flyway baseline migrations against tenant DB
4. Record migration history, update lastMigrationVersion
5. Execute admin seed & audit actions
6. Mark status=ACTIVE
```
If flag disabled and request asks for DATABASE mode → validation error (400).

### Schema Mode (Fallback / Legacy)
Used only if full DB creation not yet enabled; same action pipeline except DB creation replaced by schema creation.

### 7.1 On-Demand Per-Service Tenant Migrations (NT-23 Design)
**Decision (NT-23):** Platform-service provisions an empty per-tenant database; each domain microservice (e.g., backend-service) owns and applies its own Flyway migrations **on demand** via an internal endpoint: `POST /internal/tenants/{tenantId}/migrate`.

**Why Change from Previous Baseline Approach?**
- Avoids coupling tenant domain schema evolution to platform-service deployment cycles.
- Preserves microservice autonomy (each service version controls only its tables).
- Eliminates startup migration overhead for all tenants (migrations run once per tenant per service).
- Simplifies rollback (platform only manages control-plane metadata; data-plane failures isolated to service).

**Responsibilities Split**
| Component | Responsibility |
|-----------|----------------|
| Platform-Service | Create DB, persist tenant metadata (jdbcUrl, storageMode, SLA), trigger service migrations (synchronous REST for now) |
| Backend-Service (and future services) | Programmatic Flyway migrate against tenant DB when triggered; idempotent endpoint; owns its DDL/R__ seeds |
| Future Async Layer (Deferred) | Event bus (Kafka) for decoupled migration triggers |

**Flow (DATABASE mode)**
```
1. Request: POST /api/tenants { id, name, storageMode=DATABASE, slaTier }
2. Platform: Insert tenant row (status=PROVISIONING)
3. Platform: CREATE DATABASE tenant_<id>
4. Platform: Save jdbcUrl, mark status=PROVISIONED_NO_SCHEMA
5. Platform: Call backend-service /internal/tenants/{id}/migrate (and other services later)
6. Backend-service: Runs Flyway (classpath:db/migration for its domain), responds success with lastVersion
7. Platform: Update tenant row (last_migration_version=<backend version>), status=ACTIVE (or ACTIVE_PARTIAL if multi-service pending)
8. Return TenantDto to caller
```

**Status Model Update**
Add new statuses:
- `PROVISIONED_NO_SCHEMA` – DB created, no domain migrations yet.
- `MIGRATION_ERROR` – At least one service migration failed; retry possible.
- `ACTIVE_PARTIAL` – Some required services migrated, others pending (optional future multi-service nuance).

**Per-Service Migration Tracking (Planned)**
Introduce new master table `tenant_service_migration_status` (future NT-24):
```
tenant_id VARCHAR, service_name VARCHAR, status (PENDING|IN_PROGRESS|SUCCESS|ERROR), last_version VARCHAR, attempts INT, last_started_at TIMESTAMP, last_completed_at TIMESTAMP, error_message TEXT
```
Initial implementation (NT-23) may inline success into tenant row (single-service scenario) before multi-service expansion.

**Internal Endpoint Contract (Service Side)**
```
POST /internal/tenants/{tenantId}/migrate
Response 200: { "lastVersion": "V5" }
Error 500: Standard error schema (code=MIGRATION_FAILED)
```
Idempotent: second call when already current returns same lastVersion without side effects.

**Error Handling**
- Platform treats non-2xx from service as migration failure → tenant status MIGRATION_ERROR.
- Retry path: future endpoint `POST /api/tenants/{id}/retry` will re-trigger migration sequence only for failed services.

**Security (Placeholder)**
Internal calls currently rely on network trust; will migrate to signed internal token (Phase 2) or mTLS.

**Metrics (Minimum)**
- `platform.tenants.migration.trigger.attempts`
- `platform.tenants.migration.trigger.success`
- `platform.tenants.migration.trigger.failure`
- Service side: `tenant.migration.duration{service}` timer + success/failure counters.

**Logging (Structured)**
- Platform: `tenant_db_create_success tenantId=... dbName=...`, `tenant_migration_trigger tenantId=... service=backend-service`, `tenant_migration_failed tenantId=... service=backend-service error=...`
- Service: `tenant_migration_start tenantId=...`, `tenant_migration_success tenantId=... version=... durationMs=...`, `tenant_migration_error tenantId=... error=...`

**Fallback**
If migration call fails (network/service down): leave status PROVISIONED_NO_SCHEMA, allow later manual retry.

### Migration Layering Strategy (Planned Enhancement)
Current implementation runs tenant migrations from the same Flyway location as platform master migrations. We will separate concerns:

Layers:
| Layer | Location | Purpose |
|-------|----------|---------|
| Platform Core | `classpath:db/platform` | Platform service registry, policy, internal token tables |
| Tenant Base | `classpath:db/tenant/base` | Mandatory per-tenant domain tables |
| Tenant Tier (Premium) | `classpath:db/tenant/tier/premium` | SLA-specific enhancements |
| Tenant Feature (Audit) | `classpath:db/tenant/feature/audit` | Optional modules gated by flags |
| Tenant Region (EU) | `classpath:db/tenant/region/eu` | Regional compliance adjustments |

Rationale:
- Avoid platform vs tenant version collisions.
- Support tier/feature-specific schema evolution cleanly.
- Independent cadence: tenant features can ship without platform redeploy.
- Improved audit trail: separate history tables and clearer version lineage.

Execution Changes:
- Master DB uses Spring Boot Flyway auto-run pointing only to `db/platform`.
- Tenant provisioning dynamically composes Flyway locations list based on SLA + feature flags and invokes manual migrate per tenant DB.
- `tenant_migration_history` records independent version sequence per tenant.

Safeguards & Guidelines:
- All tenant migrations additive (no DROP) during provisioning.
- Idempotent repeatable scripts (R__*.sql) for data seeds.
- Feature/location inclusion behind properties: `platform.migrations.tier.premium.enabled`, etc.

## 8. Internal Token Strategy (Phase 2)
- Issued by Platform-Service upon gateway request (after successful user JWT validation).
- Contains: userId, tenantId, roles, issuedAt, expiry (~2-5 min), audience=[service-name], signature (rotating key).
- Domain services validate signature using cached JWK served by platform-service (`/api/jwks/internal`).

## 9. Migration from Current State
| Step | Action |
|------|--------|
| 1 | Create platform-service module (Spring Boot) + registry tables |
| 2 | Implement DB-per-tenant database creation (DONE) |
| 3 | Split migration directories (platform vs tenant) (PLANNED) |
| 4 | Refactor migration runner for dynamic locations (PLANNED) |
| 5 | Move tenant provisioning endpoints from backend-service → platform-service (DONE) |
| 6 | Introduce admin account creation action (PLANNED) |
| 7 | Policy engine & decision API (PLANNED) |
| 8 | Internal token issuance endpoint & JWK (PLANNED) |
| 9 | Retry provisioning endpoint (PLANNED) |

## 10. Required Changes (Existing Services)
| Service | Change |
|---------|--------|
| Gateway-Service | Add client for policy decision & internal token request; cache decisions; feature flags: `gateway.policy.enabled`, `gateway.internal-token.enabled` |
| Auth-Service | No direct change (may delegate role/permission mgmt to platform-service later) |
| Backend-Service | Remove provisioning endpoints; depend only on enriched headers + internal token (when enabled) |
| Future Logic-Service | Same pattern as backend-service (stateless header + internal token validation) |
| Shared Module | Introduce `platform-shared` with DTOs: TenantDto, PolicyDecisionRequest/Response, ErrorResponse, InternalTokenClaims |

## 11. Shared Module Contents (Initial)
- `TenantDto` (id, name, status, storageMode, slaTier)
- `ProvisionTenantRequest` (name, storageMode, slaTier)
- `PolicyDecisionRequest` (tenantId, roleSet, resource, action, attributes map)
- `PolicyDecisionResponse` (allowed, reason, matchedRule)
- `InternalTokenClaims` (userId, tenantId, roles, expiresAt, issuedAt, audience)
- `ErrorResponse` (standard schema)
- `HeaderNames` (constants)

## 12. Policy DSL (Example JSON)
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

## 13. Feature Flags
| Flag | Default | Purpose |
|------|---------|---------|
| `platform.db-per-tenant.enabled` | false | Enable DB create path in storage action |
| `platform.tenant.provisioning.enabled` | true | Allow provisioning endpoint |
| `platform.tenant.database-mode.enabled` | false | Accept requests with `storageMode=DATABASE` |
| `platform.audit.enabled` | false | Emit audit events externally |
| `platform.internal-token.enabled` | false | Issue internal tokens |

## 14. Observability Additions
- Metrics:
  - `platform.tenants.count{status}`
  - `platform.provision.duration{storageMode}`
  - `platform.policy.decisions.total{allowed}`
  - `platform.internal.token.issued.total`
  - `platform.db.migrations.total{status}`
- Logs: Structured `platform_event` lines for provisioning, migration, policy updates.
- Tracing: Add trace/span decorators for provisioning flows.

## 15. Security Considerations
| Concern | Mitigation |
|---------|------------|
| DB credential leakage | Store secret ref only in tenant row; fetch via IAM at runtime |
| Provision race conditions | Row lock during provisioning (optimistic concurrency) |
| Unauthorized provisioning | Require PLATFORM_ADMIN role (method security) + validate feature flags |
| Migration script drift | Separate directories & ADR-defined governance |

## 16. Rollout Strategy
| Phase | Scope | Success Metric |
|-------|-------|----------------|
| P1 | Basic provisioning (schema mode) | Tenants reachable; status=ACTIVE |
| P2 | DB-per-tenant activation | New tenants use dedicated DB; zero cross-tenant queries |
| P2a | Migration directory split | Independent versions; no collisions |
| P3 | Policy engine | Gateway decisions enforced |
| P4 | Internal tokens | Reduced trust surface |
| P5 | Advanced observability | Dashboard covers provisioning/migrations |

## 17. ADRs to Add
| ADR | Topic |
|-----|-------|
| 0004 | Database-per-tenant vs schema-per-tenant choice |
| 0005 | Provisioning action chain pattern |
| 0006 | Dynamic DataSource routing strategy |

## 18. Open Questions
| ID | Question |
|----|----------|
| Q1 | DB naming convention final format? (use short hash vs UUID) |
| Q2 | How to handle tenant DB deletion safety (grace period)? |
| Q3 | Per-tenant connection pool limits? |
| Q4 | Retry strategy for transient creation failures? |
| Q5 | Flyway script management for tenant DB upgrades? |
| Q6 | SLA tier mapping to resource quotas? |

## 19. Acceptance Criteria (Updated)
- Tenant DB created per request when `storageMode=DATABASE` feature flag enabled.
- Platform does **not** run domain Flyway migrations; only triggers service endpoints.
- Backend-service migration endpoint runs Flyway programmatically and returns last applied version.
- Tenant status progression: PROVISIONING → PROVISIONED_NO_SCHEMA → ACTIVE (single-service success) OR MIGRATION_ERROR on failure.
- Duplicate tenant ID returns 409 with standard error schema.
- Migration failures recorded (log + metrics); no partial silent activation.
- Endpoint idempotency: re-trigger migrate for already migrated tenant returns current version without error.
- Error responses follow standard error schema.

## 20. Progress Tracker (Milestones)
| ID | Milestone | Status | Notes |
|----|-----------|--------|-------|
| PS-01 | Module scaffold | DONE | Included in root build |
| PS-02 | Flyway V1 migrations | DONE | Master tables created |
| PS-03 | Basic REST API | DONE | POST + GET implemented |
| PS-04 | Eureka registration | DONE | Health check operational |
| PS-05 | OpenAPI config | DONE | springdoc configured |
| PS-06 | Security (resource server) | DONE | JWT validation configured |
| PS-07 | Docker Compose integration | DONE | Service runs locally |
| PS-08 | Provision Action Pipeline | DONE | Storage/Migration/Audit actions wired |
| PS-09 | Metrics counters | DONE | Attempts/success/failure exposed |
| PS-10 | Tenant provisioning logic | DONE | Schema mode; pipeline validated |
| PS-11 | DB-per-tenant enablement | PARTIAL | DB creation OK; migration split pending |
| PS-12 | Policy storage & decision | PENDING | |
| PS-13 | Internal token issuance | PENDING | |
| PS-14 | Advanced metrics & tracing | PENDING | |
| PS-15 | Retry & failure scenarios | PENDING | |
| PS-16 | Backend dynamic routing | PENDING | |
| PS-17 | Shared platform-client | PENDING | |

## 21. Immediate Next Steps
1. Implement status expansion (add PROVISIONED_NO_SCHEMA, MIGRATION_ERROR) to tenant entity & Flyway migration.
2. Add platform trigger logic (internal client call to backend-service migrate endpoint).
3. Create backend-service internal controller + programmatic Flyway runner (disable auto-run).
4. Add minimal integration test: provision tenant → verify backend tables exist in new tenant DB.
5. Instrument trigger & service migrations with counters/timers.
6. Define error handling + retry flow (design for `POST /api/tenants/{id}/retry`).
7. Plan `tenant_service_migration_status` table (multi-service readiness) – defer to NT-24.
8. Update documentation & ADR 0005 to reflect on-demand migration decision.

---
**End of PLATFORM_SERVICE_PLAN.md**
