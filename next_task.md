# Next Tasks & Roadmap

**Last Updated:** 2025-11-24
**Status:** Active

---

## 1. High Priority (Immediate Focus)

### 1.1 Platform Service Hardening (Multi-Tenancy)
*   **[ ] PS-11: Database-per-Tenant Implementation**
    *   Implement logic to create a dedicated DB for new tenants (if `platform.storage.mode=DATABASE`).
    *   Split Flyway migrations into `db/platform` (master) and `db/tenant` (domain).
    *   Update Provisioning Service to trigger tenant migrations.
*   **[ ] PS-12: Policy Engine**
    *   Implement `PolicyService` to manage Roles and Permissions in the Master DB.
    *   Expose API for policy resolution.
*   **[ ] PS-13: Internal Token Issuance**
    *   Implement endpoint to exchange external Cognito token for signed Internal Service Token.
    *   Add JWK endpoint for downstream validation.

### 1.2 Frontend Development (Angular)
*   **[ ] FE-01: Entry Management UI**
    *   Create Entry List component (with pagination).
    *   Create Entry Form component (Create/Edit).
    *   Integrate with Backend Service APIs.
*   **[ ] FE-02: Tenant Management UI (Admin)**
    *   Create Admin Dashboard for Tenant provisioning.
    *   Integrate with Platform Service APIs.

### 1.3 Backend Service Logic
*   **[ ] BE-01: Dynamic DataSource Routing**
    *   Implement `AbstractRoutingDataSource` to switch DB connections based on `X-Tenant-Id`.
    *   Integrate with Platform Service to fetch Tenant DB credentials (cached).

---

## 2. Medium Priority

### 2.1 Security & Authorization
*   **[ ] AUTH-01: Gateway Internal Token Exchange**
    *   Update Gateway to call Platform Service for Internal Token.
    *   Replace `Authorization` header downstream with Internal Token.
*   **[ ] AUTH-02: Backend Token Validation**
    *   Update Backend to validate Internal Token signature instead of trusting headers.

### 2.2 Observability
*   **[ ] OBS-01: Structured Logging**
    *   Ensure all services emit JSON logs with `traceId`, `spanId`, `tenantId`.
*   **[ ] OBS-02: Metrics**
    *   Expose Prometheus metrics for Tenant Provisioning and API latency.

---

## 3. Low Priority / Future

*   **[ ] INFRA-01:** CI/CD Pipeline (GitHub Actions).
*   **[ ] INFRA-02:** AWS EKS Deployment Scripts.
*   **[ ] FE-03:** Advanced UI features (Search, Filtering).

---

## 4. Completed Tasks (Reference)
*   ✅ Project Setup & Microservices Scaffold.
*   ✅ Auth Service with Cognito OIDC.
*   ✅ Gateway with JWT Validation.
*   ✅ Basic Tenant Provisioning (Schema Mode).
