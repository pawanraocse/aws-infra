# ADR 0001: Gateway-Centric Identity & Authorization Enforcement

Date: 2025-11-12
Status: Accepted
Deciders: Architecture Team
Tags: security, authentication, authorization, multi-tenant, gateway

## Context
We are building a multi-tenant SaaS platform with several Spring Boot microservices (auth-service, backend-service, future domain services) fronted by a Spring Cloud Gateway. Authentication uses AWS Cognito (OIDC), and tenants are identified via claims (e.g., cognito:groups or custom attributes). Initially, some logic (JWT parsing, tenant fallback) started drifting into backend services, risking duplication, inconsistent security boundaries, and harder future expansion (internal service tokens, platform service).

Key drivers:
- Avoid code duplication of JWT validation & permission logic across multiple backend services.
- Enforce a single security boundary where identity is normalized (userId, tenantId, authorities) and headers are trusted downstream.
- Prepare for future internal token minting (service-to-service) without refactoring multiple services.
- Fail closed on tenant derivation (no silent fallback to "default").

## Decision
All external identity and authorization enforcement is centralized in the gateway. The gateway:
1. Validates Cognito JWT (authN).
2. Extracts and validates tenant (from groups/claims) with no default fallback.
3. Sanitizes spoofable inbound headers before enrichment.
4. Populates trusted headers: X-User-Id, X-Tenant-Id, X-Authorities (optional), plus correlation (X-Request-Id).
5. Returns structured JSON errors (standard error schema) for missing/invalid tenant or auth failures.

Auth-service remains the source of truth for token acquisition and domain-specific auth exceptions, but does not perform cross-service authorization decisions for now (permission service deferred). Backend and future services trust headers and only enforce mandatory tenant presence (no JWT re-validation). Method-level security is minimized; any residual @PreAuthorize is for internal domain rules, not identity enforcement.

## Rationale
- Consistency: A single code path for authN/Z reduces divergence and regression risk.
- Performance: JWT validation occurs once; downstream services avoid redundant cryptographic checks.
- Maintainability: Changes (e.g., new claim mapping, refreshed JWKS URL) occur in one place.
- Extensibility: Internal token strategy (HMAC or signed JWT for service-to-service) can be layered onto gateway output without refactoring multiple services.
- Security: Header sanitization prevents client spoofing of identity/tenant; fail-closed tenant handling eliminates silent data leakage across tenants.

## Alternatives Considered
1. Per-Service JWT Validation
   - Pros: Each service fully independent.
   - Cons: Duplication, higher latency, more places to patch security issues.
2. Shared Library for JWT Parsing
   - Pros: Central code, reused.
   - Cons: Still multiple validation points; rollout complexity; library version drift.
3. Sidecar Auth Proxy Per Service
   - Pros: Isolation per service boundary.
   - Cons: Operational overhead; more moving parts; complexity unjustified at current scale.
4. Backend Derives Tenant With Default Fallback
   - Pros: Simplifies initial integration for tokens lacking tenant groups.
   - Cons: Data isolation risk; potential cross-tenant access; masking configuration errors.

Chosen approach outperforms alternatives on maintainability, future extensibility, and security posture.

## Consequences
Positive:
- Unified logging & error schema simplifies observability.
- Fast addition of new backend services (no auth boilerplate).
- Easier introduction of internal token / platform-service.

Trade-offs / Risks:
- Single point of enforcement—gateway misconfiguration impacts all services (mitigated with robust tests & feature flags).
- Requires stronger monitoring on gateway (latency, 403/401 rate, tenant conflict metrics).

## Implementation Summary (NT-01..NT-14)
- Removed default tenant fallback (NT-01).
- Header sanitization + correlation filters (NT-02, NT-03, NT-12, NT-13).
- Structured logging (logstash JSON) across gateway & auth-service.
- Standard ErrorResponse schema unified.
- Backend enforces mandatory X-Tenant-Id only (NT-11), no SecurityContext construction from headers.
- /auth/tokens corrected (distinct access vs id tokens) (NT-07).

## Feature Flags / Rollback
- security.gateway.fail-on-missing-tenant
- security.gateway.sanitize-headers
- security.backend.allow-missing-tenant
- logging.enhanced
- (Deferred) security.gateway.hmac.enabled

## Metrics (Planned)
- gateway.requests.total
- gateway.tenant.missing.count
- gateway.tenant.conflict.count
- auth.tokens.issued.total
- backend.tenant.invalid.count

## Future Work
- Internal signed service token (gateway → backend) replacing raw identity headers.
- Central permission service (group → fine-grained permission resolution).
- Shared module (NT-22) for error/tenant constants & DTO reuse.
- ADR 0002 (planned): Internal token & trust model.

## References
- next_task.md (NT task plan)
- CURRENT_STATUS.md (Hardening Changes section)
- copilot-index.md (Security & Hardening summary)

## Decision Outcome
Accepted and implemented. Subsequent services will integrate with gateway-provided identity headers and MUST NOT parse JWT directly unless explicitly authorized for internal/security tooling.

