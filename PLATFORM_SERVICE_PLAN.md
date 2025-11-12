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
| POST | /api/tenants | Provision new tenant (schema or DB) |
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
| P2 | Move provisioning + schema creation | 100% new tenants via platform-service |
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
- Gateway can optionally call policy decision endpoint (flag). 
- Internal token prototype documented & ADR approved before implementation.
- Monitoring dashboards include platform-service tenant & migration metrics.

---
**End of PLATFORM_SERVICE_PLAN.md**

