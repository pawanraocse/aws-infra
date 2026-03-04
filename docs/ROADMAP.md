# 🗺️ Product Roadmap: The SaaS Factory

**Mission:** Build once, launch multiple. A decoupled foundation for infinite SaaS projects.
**Last Updated:** 2026-02-10

---

## ✅ Completed Phases

### Phase 1: Self-Service Signup Portal 
**Status:** COMPLETE (2025-11-30)

- ✅ Personal signup (B2C) with auto-verification
- ✅ Organization signup (B2B) with admin creation
- ✅ Database-per-tenant isolation
- ✅ Automated tenant provisioning
- ✅ Custom Cognito attribute `custom:tenantId` (role in DB, NOT JWT)
- ✅ Immediate login post-signup

### Phase 2: Organization Admin Portal
**Status:** COMPLETE (2025-12)

- **User Management:** Invite, view roster, resend/revoke, join flow
- **Role Management:** Create/edit roles, permission viewer, RBAC enforcement
- **Dashboard:** Stats cards, company profile, quick actions

### Phase 3: Native Email Verification
**Status:** COMPLETE (2025-12-06)

- ✅ Cognito native verification (signUp API)
- ✅ PostConfirmation Lambda sets `custom:tenantId` and `custom:role`
- ✅ Frontend VerifyEmailComponent with 6-digit code

### Phase 3.1: Delete Account Feature
**Status:** COMPLETE (2025-12-14)

- ✅ Soft-delete with DELETING → DELETED status
- ✅ Multi-account safety checks (only delete Cognito user if last active membership)
- ✅ `TenantStatusValidationFilter` blocks inactive tenants

### Phase 3.2: Roles & Permissions Redesign
**Status:** COMPLETE (2025-12-21)

- ✅ Redesigned RBAC with `access_level` (admin/editor/viewer)
- ✅ `RoleLookupService` replaces `X-Role` header injection
- ✅ `@RequirePermission` integration

### Phase 3.3: Forgot Password
**Status:** COMPLETE (2025-12-21)

- ✅ Native Cognito flow (Code + New Password)
- ✅ `ForgotPasswordService` with secret hash support

### Phase 3.4: Multi-Account Per Email
**Status:** COMPLETE (2025-12-22)

- ✅ Support for 1 Personal + Multiple Org memberships on same email
- ✅ `CognitoUserRegistrar` handles existing user registration

### Phase 3.5: Production Hardening & Email-First Login
**Status:** COMPLETE (Dec 2025)

- ✅ **Email-First Login:** Tenant selection flow, `user_tenant_memberships` table
- ✅ **Integration Tests:** Comprehensive suite for auth, RBAC, isolation
- ✅ **Redis Cache:** Distributed caching with Redisson (`@Profile("!test")`)
- ✅ **AWS SES:** Configurable SES integration for production emails
- ✅ **Stripe Billing:** Checkout, Customer Portal, Webhooks, Subscription Tiers
- ✅ **Technical Debt:** Removed `X-Role`, added Company Name Collision checks

### Phase 0: Template Hardening
**Status:** COMPLETE (Dec 2025)

- ✅ **Template Scripting:** `spawn-project.sh` for automated cloning
- ✅ **Terraform Modularization:** Refactored into reusable modules
- ✅ **Dynamic Config:** Gateway endpoint for frontend config (SSM)
- ✅ **API-Key Support:** B2B integration authentication (`X-API-Key`)
- ✅ **Code Quality:** OTEL/X-Ray Observability, Lint fixes

### Phase 4: Enterprise SSO Integration
**Status:** COMPLETE (2026-01-03)

- ✅ **SAML/OIDC:** Multi-provider support (Okta, Azure, Google)
- ✅ **JIT Provisioning:** Auto-create users on login
- ✅ **Group Mapping:** Map IdP groups to SaaS roles
- ✅ **Admin UI:** SSO Configuration and Group Mapping pages

### Phase 5: Fine-Grained Permissions (OpenFGA)
**Status:** COMPLETE (2026-01-06)

- ✅ **OpenFGA Integration:** SDK, Docker container, Store-per-tenant
- ✅ **Authorization Model:** User/Org/Project/Folder/Document hierarchy (ReBAC)
- ✅ **Permission API:** Share, Revoke, List Access, Check endpoints
- ✅ **Resilience Patterns:**
  - Retry logic (3 attempts, exponential backoff)
  - Circuit breaker (50% threshold, 30s recovery)
  - Health indicator (`/actuator/health/openfga`)
- ✅ **Rate Limiting:** Per-user, per-endpoint (10 req/sec)
- ✅ **Security Validations:** User exists check, owner authorization
- ✅ **Frontend UI:** Permission Manager Component (Angular)
- ✅ **Audit Logging:** `PermissionAuditLogger` for all decisions
- ✅ **Gateway Routing:** Optimized routing for `/auth/api/v1/resource-permissions`
- ✅ **Refactored SSO Service:** Extracted `CognitoProviderManager`, `SsoAttributeMappingBuilder`

---

## ✅ Completed Phases (continued)

### Phase 8: AWS Deployment Infrastructure
**Status:** COMPLETE (2026-01-06)
*(Formerly Phase 0.5: AWS Deployment Ready)*

**Objective:** Make the template production-deployable to AWS with Terraform modules.

#### 8.1 Terraform Modules ✅
- ✅ `modules/vpc` - VPC, subnets, NAT Gateway, Flow Logs
- ✅ `modules/rds` - PostgreSQL/Aurora, Secrets Manager, SSM Parameters
- ✅ `modules/elasticache` - Redis cluster with replication
- ✅ `modules/ecr` - Docker image registry with lifecycle policies
- ✅ `modules/ecs-cluster` - ECS Fargate cluster with Container Insights
- ✅ `modules/ecs-service` - Generic, reusable ECS service module
- ✅ `modules/alb` - Application Load Balancer with HTTPS
- ✅ `modules/amplify` - Angular frontend hosting
- ✅ `modules/bastion` - Secure bastion host for DB access

#### 8.2 Deployment Environments ✅
- ✅ **Budget (`terraform/envs/budget/`)**: EC2 + Docker Compose, Managed RDS + ElastiCache (~$15-30/mo)
- ✅ **Production (`terraform/envs/production/`)**: ECS Fargate, RDS, ElastiCache, ALB (~$150/mo)
- ✅ One-shot deployment scripts: `deploy-budget.sh`, `deploy-production.sh`
- ✅ CI/CD: `.github/workflows/deploy-production.yml`

#### 8.3 Documentation ✅
- ✅ README.md updated with deployment guides
- ✅ Prerequisites section with step-by-step setup
- ✅ Module README files for all 9 Terraform modules

---

## 🔮 Future Horizons

### Phase 9: Future Roadmap & Backlog
*(Consolidated Scale, Advanced Features, and Deferred Items)*

#### 9.1 Scale & Performance (Q3-Q4 2026)
- ✅ **9.1.1 gRPC Internal Mesh:** Hybrid architecture (REST Gateway, gRPC Mesh). `RemotePermissionEvaluator` and `RemoteRoleLookupService` now use gRPC (HTTP/2 + Protobuf) with automatic REST fallback. Feature-flagged via `app.grpc.enabled`. Auth-service exposes gRPC on port 9091.
- ✅ **9.1.2 Async Provisioning:** SQS-based tenant creation for org tenants. Auth-service sends `ProvisionTenantEvent` to SQS; platform-service consumes and runs action chain (DB + Flyway + audit). Feature-flagged via `app.async-provision.enabled`. LocalStack for local dev, Terraform module for production. DLQ with 3-retry policy.
- ✅ **9.1.3 Async Deletion:** SNS/SQS fanout for tenant cleanup. `TenantDeletionService` publishes `TenantDeletedEvent` to SNS after soft-delete; `TenantCleanupConsumer` subscribes via SQS and drops dedicated ORG tenant DBs. Feature-flagged via `app.async-deletion.enabled`. Terraform provisions SNS topic, cleanup queue + DLQ, IAM policy, and subscription.
- 📅 **9.1.4 Sharding:** Multiple RDS instances (deferred)
- 📅 **9.1.5 Multi-Region:** Data residency compliance (deferred)

#### 9.2 Advanced Features (2027+)
- **GraphQL API**
- **Event-Driven Architecture** (Kafka/SNS)
- **Billing Engine** (Usage-based)
- **Mobile SDKs** (iOS/Android)
- **Tenant Analytics**

#### 9.3 Security & Infra Backlog
- **Internal Auth:** Shared secret validation for service-to-service calls
- **API Key Limits:** Per-key rate limiting
- **API Key Analytics:** Usage dashboards for key metrics
- **Network Isolation:** K8s NetworkPolicies
- **Chaos Engineering:** Fault injection testing
- **OIDC Client Secrets:** Move secrets from DB to AWS Secrets Manager
- **Cache Migration:** Migrate Account Deletion status from Caffeine to Redis

#### 9.4 Frontend Modularization
- Abstract Orgs/Members/Auth into Angular library (Deferred from Phase 0)

### Phase 10: Upcoming Features (Requested Jan 2026)
**Status:** PLANNED

#### 10.1 Dedicated Payment Service
- **Objective:** Decouple billing from platform-service.
- **Features:**
    - Support for multiple providers: Stripe AND Razorpay.
    - Abstraction layer for payment intent/checkout.
    - Webhook handling for both providers.

#### 10.2 Share Link Feature
- **Objective:** Public sharing capability for resources.
- **Features:**
    - "Share" button on UI (e.g., Login page or internal resources).
    - Public access token generation.
    - Anonymous access handling.

#### 10.3 Hybrid Multi-Tenancy (Personal Users)
- **Objective:** Improve scalability for free/personal tier users.
- **Problem:** Database-per-tenant is not scalable for millions of single-user tenants.
- **Solution:**
    - **Personal Users:** Single shared database with discriminator column (Schema-per-tenant or Logical Separation).
    - **Organizations:** Continue using Database-per-tenant for isolation.
    - Update `TenantContext` to handle hybrid routing strategies.

---

## 🌟 Strategic Vision: Project Spawning

Using this template as the root, we will launch:
1.  **Project: ImageKit Advanced** - AI-native image processing
2.  **Project: Pure DAM** - Digital Asset Management
3.  **Project: [Your Next Idea]**

*See [HLD.md](../HLD.md) for architecture details.*
