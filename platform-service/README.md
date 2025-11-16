# Platform Service

## Overview
The Platform Service is the control-plane for tenant lifecycle management in a multi-tenant SaaS architecture. It is responsible for orchestrating all infrastructure and metadata operations related to tenant onboarding, status management, and initial schema setup. It does **not** own or manage business/domain data tables.

---

## Responsibilities
- **Tenant Provisioning:**
  - Validate and persist tenant metadata (ID, name, storage mode, SLA tier).
  - Create per-tenant storage (PostgreSQL schema or database) using the configured storage mode.
- **Downstream Orchestration:**
  - After storage creation, call downstream services (e.g., backend-service, auth-service) to run their own Flyway migrations in the new tenant DB.
- **Status Management:**
  - Track and update tenant status: `PROVISIONING` → `MIGRATING` → `ACTIVE` (or `PROVISION_ERROR` / `MIGRATION_ERROR`).
  - Provide a retry endpoint for failed migrations.
- **Cleanup (Future):**
  - Destroy tenant storage on deletion (planned).
- **Observability:**
  - Emit structured logs and metrics for all provisioning and migration events.

---

## Who Can Use This Service?
- **Internal platform operators** (via admin UI or automation scripts) to onboard new tenants.
- **Automated CI/CD pipelines** to provision tenants as part of environment setup.
- **Other microservices** (read-only) to fetch tenant metadata.

## What Does This Service Connect To?
- **PostgreSQL** (for platform metadata and tenant DB/schema creation).
- **Downstream microservices** (e.g., backend-service, auth-service) via internal HTTP calls to trigger per-tenant Flyway migrations.

---

## Code Flow (Provisioning)
1. **API Call:** `POST /api/tenants` with tenant details.
2. **Validation:** Request is validated for ID, name, storage mode, and SLA tier.
3. **Metadata Row:** Tenant row is inserted with status `PROVISIONING`.
4. **Storage Creation:**
   - `StorageProvisionAction` creates schema or database, sets `jdbcUrl`.
   - Status transitions to `MIGRATING`.
5. **Migration Orchestration:**
   - `MigrationInvokeAction` calls each downstream service's internal migration endpoint (e.g., backend-service `/internal/tenants/{id}/migrate`).
   - On success, status is set to `ACTIVE` and `lastMigrationVersion` is recorded.
   - On failure, status is set to `MIGRATION_ERROR` (or `PROVISION_ERROR` if early failure).
6. **Retry:** If migration fails, `POST /api/tenants/{id}/retry-migration` can be called to re-attempt migrations.

---

## High-Level Design (HLD)
- **Separation of Concerns:** Platform-service only manages tenant metadata and infrastructure. Domain data and migrations are owned by each microservice.
- **Extensible Action Chain:** Provisioning is implemented as a chain of actions (`TenantProvisionAction`), allowing easy insertion of new steps (e.g., audit, notification).
- **Status-Driven Workflow:** All operations are status-driven, enabling robust error handling and retry logic.
- **Internal-Only Orchestration:** All downstream migration calls are internal (not exposed to external clients).

## Low-Level Design (LLD)
- **Entities:** `Tenant` (metadata), `TenantDto`, `ProvisionTenantRequest`.
- **Actions:**
  - `StorageProvisionAction`: Creates schema or DB.
  - `MigrationInvokeAction`: Calls downstream migration endpoints.
- **Service Layer:**
  - `TenantProvisioningServiceImpl`: Orchestrates the full flow, manages status, and handles retries.
- **API Layer:**
  - `TenantController`: Exposes endpoints for provision, get, and retry.
- **Config:**
  - `PlatformTenantProperties`: Feature flags and settings.
- **Exception Handling:**
  - Custom exceptions for clear error reporting.

---

## Example Endpoints
| Method | Path | Purpose |
|--------|------|---------|
| POST | /api/tenants | Provision new tenant |
| GET  | /api/tenants/{id} | Retrieve tenant metadata |
| POST | /api/tenants/{id}/retry-migration | Retry downstream migrations |

---

## Security & Access
- Only internal users and trusted automation should call provisioning endpoints.
- Downstream migration endpoints are internal-only and should not be exposed externally.
- Future: Secure internal calls with mTLS or signed JWTs.

---

## Extensibility & Future Work
- Add support for async event-driven migration orchestration (Kafka).
- Implement tenant deletion and cleanup.
- Add per-service migration status tracking for multi-service expansion.
- Harden internal API security.

---

## Changelog
- 2025-11-16: Major cleanup and documentation rewrite. Service now focused solely on tenant metadata, storage orchestration, and migration coordination.

