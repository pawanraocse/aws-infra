# High-Level Design: SaaS Foundation & Project Template

**Version:** 7.2 (OpenFGA Fine-Grained Access)
**Last Updated:** 2026-01-06
**Core Mission:** A production-ready, ultra-decoupled foundation for launching multi-tenant SaaS applications in days, not months.


---

## 🎯 What Is This Project?

This is a **template system** for building multi-tenant SaaS applications. It provides all the supporting infrastructure and services you need - just plug in your business logic.

### Key Philosophy: The "SaaS Factory"
- **Strict Decoupling:** Core infrastructure (Auth, Platform, Gateway) is completely separate from business logic.
- **Microservice Mimicry:** The `backend-service` is purely a placeholder/mimic to be swapped for any domain (ImageKit, DAM, CRM, etc.).
- **Infrastructure as Code (IaC):** Modular Terraform that can be cloned to spawn isolated environments for new startups.
- **Multi-Tenant First:** Every component is designed for 100% data isolation and per-tenant scalability.
- **Developer-Centric:** Focus on the "Boring" 80% (Auth, Billing, Orgs) so you can focus on the "Earning" 20%.

---

## 🚀 Quick Start Guide

### Prerequisites
- Java 21+, Maven 3.9+
- Docker & Docker Compose
- Node.js 18+ (for frontend)
- AWS CLI configured with credentials
- Terraform 1.9+

### Step 1: Deploy AWS Infrastructure (Cognito)
```bash
cd terraform
terraform init
terraform apply -auto-approve
```
This creates: Cognito User Pool, Lambda triggers, SSM parameters.

### Step 2: Start Backend Services
```bash
# From project root
docker-compose up -d
```
Services available at:
- Gateway: http://localhost:8080
- Frontend: http://localhost:4200
- Eureka Dashboard: http://localhost:8761

### Step 3: Create System Admin
```bash
./scripts/bootstrap-system-admin.sh your-admin@email.com "YourPassword123!"
```

### Step 4: Test the Flow
1. Navigate to http://localhost:4200
2. **Personal Signup (B2C):** Create account → Verify email → Login
3. **Organization Signup (B2B):** Create org → Invite users → Login
4. Access dashboard with your tenant's isolated data

---

## 📋 Service Roles & Responsibilities

This section provides a complete breakdown of what each service does and what it owns.

### Core Services Overview

| Service | Port | Primary Role | Database | OpenFGA | Key Responsibility |
|---------|------|--------------|----------|---------|-------------------|
| **gateway-service** | 8080 | API Gateway | None | ❌ None | Request routing, JWT validation, rate limiting |
| **auth-service** | 8081 | Identity & Authorization | Tenant DBs | ✏️ **WRITE** | User auth, RBAC, permissions, **writes tuples** |
| **platform-service** | 8083 | Tenant Lifecycle | Platform DB | 🏗️ Store Create | Tenant provisioning, billing, creates FGA stores |
| **backend-service** | 8082 | Business Logic (Mimic) | Tenant DBs | 👁️ **READ** | Domain logic, **checks permissions via OpenFGA** |
| **eureka-server** | 8761 | Service Discovery | None | ❌ None | Service registration, health checks |

**OpenFGA Legend:**
- ✏️ **WRITE** = Uses `OpenFgaWriter` to create/delete tuples (permission changes)
- 👁️ **READ** = Uses `OpenFgaReader` to check permissions (access decisions)
- 🏗️ **Store Create** = Creates OpenFGA stores during tenant provisioning

---

### Detailed Service Responsibilities

#### 🚪 gateway-service

| Category | Responsibility | Key Classes/Files |
|----------|----------------|-------------------|
| **Request Routing** | Routes `/auth/**`, `/platform/**`, `/api/**` to correct services | `application.yml` routes |
| **JWT Validation** | Validates Cognito tokens, rejects unauthenticated requests | `JwtAuthFilter` |
| **Tenant Resolution** | Extracts tenant from JWT claims, resolves for SSO users | `TenantResolverFilter` |
| **Header Injection** | Injects `X-Tenant-Id`, `X-User-Id`, `X-Authorities` downstream | `HeaderInjectionFilter` |
| **Rate Limiting** | Per-tenant and per-user rate limits | `RateLimitFilter` |
| **CORS** | Cross-origin request handling | `CorsConfiguration` |
| **API Key Auth** | Validates API keys via platform-service internal API | `ApiKeyAuthenticationFilter` |
| **Health Aggregation** | Aggregates health from all services | Actuator endpoints |

**Databases:** None  
**Does NOT:** Store data, manage users, enforce permissions, run business logic

---

#### 🔐 auth-service

| Category | Responsibility | Key Classes/Files |
|----------|----------------|-------------------|
| **Login/Logout** | Cognito-based authentication, token exchange | `AuthController`, `AuthServiceImpl` |
| **Token Refresh** | Refresh access tokens using Cognito refresh tokens | `AuthController.refreshToken()` |
| **Password Reset** | Forgot password, reset confirmation | `ForgotPasswordService` |
| **User Registration** | Personal & organization signup orchestration | `SignupController`, `SignupServiceImpl` |
| **Email Verification** | Verification codes, resend logic | `EmailVerificationService` |
| **User Management** | CRUD operations for tenant users | `TenantUserController`, `TenantUserService` |
| **Role Management** | Roles table, role hierarchy | `RoleRepository`, `UserRoleService` |
| **Permission Management** | Role-permission mappings | `PermissionService`, `RolePermissionRepository` |
| **User-Role Assignment** | Assign/remove roles from users | `UserRoleService.assignRole()` |
| **ACL Management** | Resource-level access control | `AclController`, `AclService`, `AclEntry` |
| **Invitation System** | Create, send, accept invitations | `InvitationController`, `InvitationService` |
| **Group-Role Mapping** | SSO groups to internal roles | `GroupRoleMappingController` |
| **SSO Configuration** | SAML/OIDC IdP setup per tenant | `SsoConfigurationController`, `SsoConfigurationService` |
| **Account Deletion** | Delete user accounts (GDPR) | `AccountDeletionService` |
| **OpenFGA Tuples** | **ONLY auth-service writes/deletes tuples** | `OpenFgaWriter` (when enabled) |

**Databases:** Per-tenant databases via `TenantDataSourceRouter`  
**Tables Owned:** `roles`, `permissions`, `role_permissions`, `user_roles`, `acl_entries`, `group_role_mappings`, `invitations`, `tenant_users`, `sso_configurations`  
**Does NOT:** Create tenants, manage billing, enforce permissions at runtime (that's backend's job)

---

#### 🏢 platform-service

| Category | Responsibility | Key Classes/Files |
|----------|----------------|-------------------|
| **Tenant Creation** | Create tenant records (PERSONAL, ORGANIZATION) | `TenantController`, `TenantProvisioningService` |
| **Tenant Provisioning** | Database/schema creation, user provisioning | `StorageProvisionAction`, `TenantProvisioner` |
| **Flyway Orchestration** | Triggers migrations on auth-service, backend-service | `MigrationInvokeAction` |
| **OpenFGA Store Creation** | Creates FGA store per tenant during signup | `OpenFgaProvisionAction` |
| **Tenant Status Management** | Activate, suspend, archive tenants | `TenantService.updateStatus()` |
| **Tenant Lookup** | Get tenant by ID, email, list all | `TenantInternalController` |
| **Membership Management** | User-tenant mappings, default workspace | `MembershipController`, `MembershipService` |
| **Tenant Switching** | Set default tenant, list user's tenants | `MembershipService.setDefaultTenant()` |
| **Stripe Integration** | Customer creation, subscriptions | `StripeService`, `BillingController` |
| **Checkout Sessions** | Create Stripe checkout sessions | `BillingController.createCheckoutSession()` |
| **Customer Portal** | Stripe billing portal access | `BillingController.createPortalSession()` |
| **Webhook Processing** | Handle Stripe events (subscription updates) | `WebhookController`, `WebhookService` |
| **IdP Group Sync** | Sync groups from external IdPs | `GroupSyncService` |
| **API Key Management** | Create, validate, revoke, list keys | `ApiKeyController`, `ApiKeyService` |
| **Organization Profile** | Company name, logo, industry settings | `OrganizationController`, `OrganizationService` |
| **Usage Metrics** | Track API calls, storage per tenant | `TenantUsageMetrics` |
| **Audit Logging** | Log tenant lifecycle events | `TenantAuditLog` |

**Databases:** Platform DB (shared across all tenants)  
**Tables Owned:** `tenant`, `user_tenant_memberships`, `stripe_customers`, `webhook_events`, `api_keys`, `idp_groups`, `tenant_audit_log`, `tenant_usage_metrics`, `deleted_accounts`  
**Does NOT:** Write permission tuples, enforce access, manage users within tenant

---

#### 📦 backend-service (REPLACEABLE MIMIC)

| Category | Responsibility | Key Classes/Files |
|----------|----------------|-------------------|
| **Domain Logic** | **YOUR BUSINESS LOGIC GOES HERE** | Replace entirely |
| **Resource CRUD** | Create, read, update, delete domain entities | Controllers, Services, Repositories |
| **Permission Checks** | Enforce `@RequirePermission` annotations | `AuthorizationAspect` |
| **OpenFGA Checks** | Check resource access via `OpenFgaReader` | `fgaReader.check()` |
| **Tenant Data Access** | Automatic tenant isolation via datasource router | `TenantDataSourceRouter` |
| **Flyway Migrations** | Run domain-specific schema migrations | `db/migration/V*.sql` |

**Databases:** Per-tenant databases via `TenantDataSourceRouter`  
**Tables Owned:** Your domain tables (this is a mimic - replace with your actual domain)  
**Does NOT:** Write tuples, create users, manage permissions (that's auth-service's job)

---

#### 🔗 common-infra (Shared Library)

| Category | Components | Purpose |
|----------|------------|---------|
| **Tenant Context** | `TenantContext`, `TenantHolder`, `TenantContextFilter` | Thread-local tenant ID |
| **Datasource Routing** | `TenantDataSourceRouter`, `DynamicDataSource` | Multi-tenant connection pooling |
| **Tenant Registry** | `TenantRegistryService`, `TenantLocalCache` | Cache tenant configs from platform |
| **Authorization** | `@RequirePermission`, `AuthorizationAspect`, `PermissionEvaluator` | Method-level RBAC |
| **OpenFGA Client** | `OpenFgaReader`, `OpenFgaWriter`, `OpenFgaClientWrapper`, `OpenFgaNoOpClient` | Fine-grained permissions |
| **Security** | `ApiKeyAuthenticationFilter`, JWT utilities | Authentication helpers |
| **DTOs** | Request/response objects shared across services | `common-dto` module |

---

#### 📡 eureka-server

| Category | Responsibility |
|----------|----------------|
| **Service Registration** | All services register on startup |
| **Service Discovery** | Clients lookup service instances by name |
| **Health Monitoring** | Heartbeats, automatic de-registration |
| **Load Balancing** | Client-side load balancing via Ribbon/LoadBalancer |

**Databases:** None  
**Does NOT:** Route traffic, validate tokens, any business logic

---

### Complete Ownership Matrix

| What | Owned By | Stored In |
|------|----------|-----------|
| Tenant records | platform-service | Platform DB |
| User-tenant mappings | platform-service | Platform DB |
| Stripe customers | platform-service | Platform DB |
| API keys | platform-service | Platform DB |
| SSO IdP configs | platform-service | Platform DB |
| Roles & Permissions | auth-service | Tenant DBs |
| User-role assignments | auth-service | Tenant DBs |
| ACL entries | auth-service | Tenant DBs |
| Invitations | auth-service | Tenant DBs |
| Tenant users | auth-service | Tenant DBs |
| OpenFGA tuples | auth-service | OpenFGA |
| OpenFGA stores | platform-service | OpenFGA |
| Domain entities | backend-service | Tenant DBs |
| JWT validation | gateway-service | None (stateless) |

---

### OpenFGA Architecture Summary

**Multi-Tenant Store Strategy:** Each tenant gets their own OpenFGA store. Store ID is stored in `tenant.fga_store_id` and looked up dynamically per-request.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         OpenFGA Multi-Tenant Flow                        │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   TENANT SIGNUP                                                          │
│   ─────────────                                                          │
│   1. platform-service creates tenant                                     │
│   2. OpenFgaProvisionAction creates dedicated FGA store                  │
│   3. Store ID saved to tenant.fga_store_id                               │
│                                                                          │
│   RUNTIME (per-request)                                                  │
│   ─────────────────────                                                  │
│   1. TenantContext provides current tenant ID                            │
│   2. OpenFgaClientWrapper looks up fga_store_id from TenantDbConfig      │
│   3. Creates/caches client per store (ConcurrentHashMap)                 │
│   4. Executes FGA operation against tenant's store                       │
│                                                                          │
│   WRITE PATH (auth-service only)          READ PATH (any service)        │
│   ───────────────────────────             ────────────────────           │
│                                                                          │
│   Role Assign/Revoke                      Can User View Folder?          │
│        │                                         │                       │
│        ▼                                         ▼                       │
│   ┌─────────────┐                         ┌─────────────┐               │
│   │ auth-service│                         │backend-svc  │               │
│   │             │                         │             │               │
│   │ OpenFgaWriter                         │ OpenFgaReader                │
│   └──────┬──────┘                         └──────┬──────┘               │
│          │ writeTuple()                          │ check()               │
│          │ (non-throwing)                        │ (fail-safe deny)      │
│          └───────────────┬───────────────────────┘                       │
│                          ▼                                               │
│              ┌───────────────────────┐                                   │
│              │ Tenant's FGA Store    │                                   │
│              │ (store-per-tenant)    │                                   │
│              └───────────────────────┘                                   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 🔐 OpenFGA Fine-Grained Authorization Design

### What is OpenFGA?

OpenFGA is an open-source, high-performance authorization engine that implements **Relationship-Based Access Control (ReBAC)**. It's based on Google Zanzibar, the system that powers authorization for Google Drive, YouTube, and other Google services.

### Why ReBAC over Traditional RBAC?

| Feature | Traditional RBAC | OpenFGA (ReBAC) |
|---------|------------------|-----------------|
| **Model** | User → Role → Permission | User → Relation → Object |
| **Granularity** | Role-level (e.g., "editor") | Object-level (e.g., "editor of folder X") |
| **Sharing** | Typically global | Per-object sharing ("share with Bob") |
| **Hierarchies** | Limited | Native (folders contain documents) |
| **Scale** | Thousands of users | Billions of tuples |
| **Inheritance** | Manual | Automatic (parent permissions flow down) |

### Authorization Model

```mermaid
graph TB
    subgraph "Authorization Model (DSL)"
        User([user])
        Org[organization]
        Project[project]
        Folder[folder]
        Document[document]
        
        User -->|member| Org
        User -->|admin| Org
        Org -->|parent| Project
        User -->|owner| Project
        User -->|editor| Project
        User -->|viewer| Project
        Project -->|parent| Folder
        User -->|owner| Folder
        User -->|editor| Folder
        User -->|viewer| Folder
        Folder -->|parent| Document
        User -->|owner| Document
        User -->|editor| Document
        User -->|viewer| Document
    end
    
    style User fill:#f9f,stroke:#333
    style Org fill:#bbf,stroke:#333
    style Project fill:#bfb,stroke:#333
    style Folder fill:#fbf,stroke:#333
    style Document fill:#ff9,stroke:#333
```

**Model Definition (DSL):**
```dsl
model
  schema 1.1

type user

type organization
  relations
    define admin: [user]
    define member: [user] or admin

type project
  relations
    define parent: [organization]
    define owner: [user] or admin from parent
    define editor: [user] or owner
    define viewer: [user] or editor

type folder
  relations
    define parent: [project, folder]
    define owner: [user] or owner from parent
    define editor: [user] or owner or editor from parent
    define viewer: [user] or editor or viewer from parent
    define can_share: owner or editor

type document
  relations
    define parent: [folder]
    define owner: [user] or owner from parent
    define editor: [user] or owner or editor from parent
    define viewer: [user] or editor or viewer from parent
```

### Multi-Tenant Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    OpenFGA Multi-Tenant Architecture                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐  │
│   │                      platform-service                            │  │
│   │  Tenant Provisioning:                                            │  │
│   │  1. Create tenant record                                         │  │
│   │  2. Provision tenant database                                    │  │
│   │  3. OpenFgaProvisionAction creates FGA store                     │  │
│   │  4. Store `fga_store_id` in tenant table                         │  │
│   └─────────────────────────────────────────────────────────────────┘  │
│                                │                                        │
│                                ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────┐  │
│   │                      OpenFGA Server                              │  │
│   │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │  │
│   │  │ Tenant A Store │  │ Tenant B Store │  │ Tenant C Store │    │  │
│   │  │ store_id: abc  │  │ store_id: def  │  │ store_id: ghi  │    │  │
│   │  │                │  │                │  │                │    │  │
│   │  │ Tuples:        │  │ Tuples:        │  │ Tuples:        │    │  │
│   │  │ user:1#owner   │  │ user:5#admin   │  │ user:8#viewer  │    │  │
│   │  │ folder:x       │  │ project:y      │  │ doc:z          │    │  │
│   │  └────────────────┘  └────────────────┘  └────────────────┘    │  │
│   └─────────────────────────────────────────────────────────────────┘  │
│                                ▲                                        │
│                 ┌──────────────┼──────────────┐                        │
│                 │              │              │                        │
│   ┌─────────────┴───┐  ┌──────┴─────┐  ┌────┴────────────┐           │
│   │  auth-service   │  │backend-svc │  │ other-services  │           │
│   │  OpenFgaWriter  │  │OpenFgaReader│  │ OpenFgaReader  │           │
│   │  (writes tuples)│  │(checks only)│  │ (checks only)  │           │
│   └─────────────────┘  └────────────┘  └────────────────┘           │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Resilience Patterns

The OpenFGA integration includes production-grade resilience:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       OpenFGA Resilience Stack                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐  │
│   │                    OpenFgaResilienceConfig                       │  │
│   │                                                                  │  │
│   │  RETRY (Resilience4j)          CIRCUIT BREAKER (Resilience4j)   │  │
│   │  ─────────────────────         ─────────────────────────────    │  │
│   │  • Max Attempts: 3             • Failure Rate: 50%              │  │
│   │  • Initial Delay: 100ms        • Minimum Calls: 5               │  │
│   │  • Multiplier: 2.0             • Wait Duration: 30s             │  │
│   │  • Max Delay: 1s               • Permitted in Half-Open: 3      │  │
│   │                                                                  │  │
│   │  Retryable: Connection         States:                          │  │
│   │  timeouts, transient           CLOSED → OPEN → HALF_OPEN        │  │
│   │  network failures              (auto-recovery)                   │  │
│   │                                                                  │  │
│   └─────────────────────────────────────────────────────────────────┘  │
│                                │                                        │
│                                ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────┐  │
│   │                   OpenFgaHealthIndicator                         │  │
│   │                                                                  │  │
│   │  Reports to Spring Actuator /actuator/health:                   │  │
│   │                                                                  │  │
│   │  • UP: Circuit CLOSED, FGA reachable                            │  │
│   │  • DEGRADED: Circuit HALF_OPEN (recovering)                     │  │
│   │  • DOWN: Circuit OPEN or FGA unreachable                        │  │
│   │                                                                  │  │
│   │  Metrics exposed:                                                │  │
│   │  • openfga.success.count                                        │  │
│   │  • openfga.failure.count                                        │  │
│   │  • openfga.circuit.state                                        │  │
│   │                                                                  │  │
│   └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│   FAIL-SAFE BEHAVIOR:                                                   │
│   • Read operations (check): Return FALSE on failure (deny access)     │
│   • Write operations: Log error, don't fail caller (non-throwing)      │
│   • When openfga.enabled=false: NoOp client returns false/empty        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Rate Limiting

Permission API endpoints are protected with per-user rate limiting:

| Endpoint | Rate Limit | Configuration |
|----------|------------|---------------|
| `POST /api/v1/resource-permissions/share` | 10 req/sec | Per user |
| `DELETE /api/v1/resource-permissions/revoke` | 10 req/sec | Per user |
| `GET /api/v1/resource-permissions/list` | 10 req/sec | Per user |

**Implementation:** `ApiRateLimiter` using Resilience4j RateLimiter with sliding window.

### API Examples

**Share Access:**
```bash
POST /auth/api/v1/resource-permissions/share
Authorization: Bearer <JWT>

{
  "resourceType": "folder",
  "resourceId": "folder-123",
  "targetUserId": "user-456",
  "relation": "editor"
}
```

**Check Permission:**
```bash
GET /auth/api/v1/resource-permissions/check
  ?userId=user-456
  &resourceType=folder
  &resourceId=folder-123
  &relation=viewer

Response: { "allowed": true }
```

**List Access:**
```bash
GET /auth/api/v1/resource-permissions/list
  ?resourceType=folder
  &resourceId=folder-123

Response: [
  { "userId": "user-123", "relation": "owner" },
  { "userId": "user-456", "relation": "editor" }
]
```

### Security Validations

| Validation | Endpoint | Purpose |
|------------|----------|---------|
| **User Exists** | share | Prevent granting access to non-existent users |
| **Owner Check** | revoke | Only owner/can_share can revoke permissions |
| **Rate Limiting** | all | Prevent API abuse (10 req/sec/user) |

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `OpenFgaClientWrapper` | common-infra | Multi-tenant client with caching |
| `OpenFgaResilienceConfig` | common-infra | Retry + Circuit Breaker |
| `OpenFgaHealthIndicator` | common-infra | Actuator health endpoint |
| `ApiRateLimiter` | common-infra | Per-user rate limiting |
| `ResourcePermissionController` | auth-service | Permission management API |
| `OpenFgaProvisionAction` | platform-service | Store creation at signup |

### Configuration

```yaml
openfga:
  enabled: true                    # Feature flag
  api-url: http://openfga:8080    # OpenFGA server URL
  
# Resilience (application.yml)
resilience4j:
  retry:
    instances:
      openfga:
        max-attempts: 3
        wait-duration: 100ms
  circuitbreaker:
    instances:
      openfga:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

---

## 🔧 Adding Your Own Service

This template is designed to be extended. Here's how to add a new multi-tenant service:

### Step 1: Copy Backend-Service as Starting Point
```bash
cp -r backend-service/ my-new-service/
# Update pom.xml: artifactId, name
# Update application.yml: server.port (e.g., 8084)
```

### Step 2: Add common-infra Dependency
In your `pom.xml`:
```xml
<dependency>
    <groupId>com.learning</groupId>
    <artifactId>common-infra</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 3: Configure Tenant Database Routing
Create a `DataSourceConfig.java`:
```java
@Configuration
public class DataSourceConfig {
    @Bean
    public TenantRegistryService tenantRegistryService(WebClient platformWebClient) {
        return new PlatformServiceTenantRegistry(platformWebClient, new TenantLocalCache());
    }
    
    @Bean
    public DataSource dataSource(TenantRegistryService tenantRegistry, DataSource defaultDataSource) {
        return new TenantDataSourceRouter(tenantRegistry, defaultDataSource);
    }
}
```

### Step 4: Add WebClient for Platform-Service
```java
@Bean
public WebClient platformWebClient(WebClient.Builder builder) {
    return builder.baseUrl("http://platform-service/platform").build();
}
```

### Step 5: Register with Eureka
In `application.yml`:
```yaml
spring.application.name: my-new-service
eureka.client.service-url.defaultZone: http://eureka-server:8761/eureka
```

### Step 6: Add to docker-compose.yml
```yaml
my-new-service:
  build: ./my-new-service
  ports: ["8084:8084"]
  depends_on:
    eureka-server: {condition: service_healthy}
    postgres: {condition: service_healthy}
```

### Step 7: Add Gateway Route
In `gateway-service/application.yml`:
```yaml
- id: my-new-service
  uri: lb://my-new-service
  predicates:
    - Path=/my-new/**
```

**Key Points:**
- All services use `TenantContext` for tenant isolation
- Internal APIs use `/internal/**` paths (no auth required)
- Gateway handles all JWT validation - your service trusts `X-Tenant-Id` header

---

## 🏗️ System Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        WebApp[🌐 Angular Web App<br/>AWS Amplify<br/><b>Template Frontend</b>]
        User[👤 End Users<br/>B2C + B2B]
        Admin[🔧 System Admin]
    end
    
    subgraph "Entry Point"
        ALB[AWS Application Load Balancer]
    end
    
    subgraph "Microservices"
        Gateway[🛡️ Gateway Service<br/>Port 8080<br/><b>Gatekeeper</b>]
        Auth[🔐 Auth Service<br/>Port 8081<br/><b>Identity & Permissions</b>]
        Platform[⚙️ Platform Service<br/>Port 8083<br/><b>Tenant Lifecycle</b>]
        Backend[📦 Backend Service<br/>Port 8082<br/><b>REPLACEABLE MIMIC</b>]
        Eureka[🔍 Eureka Server<br/>Port 8761<br/><b>Service Discovery</b>]
    end
    
    subgraph "AWS Services"
        Cognito[AWS Cognito<br/>User Pools & Identity<br/>B2C + B2B Auth]
        RDS[PostgreSQL RDS<br/>Master + Tenant DBs]
        SecretsManager[AWS Secrets Manager]
        SSM[AWS SSM Parameter Store]
        Amplify[AWS Amplify Hosting<br/>CI/CD for Frontend]
    end
    
    subgraph "Infrastructure (Optional)"
        Redis[Redis Cache]
        DocumentDB[DocumentDB/MongoDB]
    end
    
    User --> WebApp
    Admin --> WebApp
    WebApp --> Cognito
    WebApp --> ALB
    ALB --> Gateway
    
    Gateway --> Auth
    Gateway --> Platform
    Gateway --> Backend
    
    Gateway -.- Eureka
    Auth -.- Eureka
    Platform -.- Eureka
    Backend -.- Eureka
    
    Auth --> Cognito
    Auth --> Platform
    Platform --> RDS
    Backend --> RDS
    
    Platform --> SecretsManager
    Platform --> SSM

    style Backend fill:#2b1f1f,stroke:#ff5c5c,stroke-width:2px
    style Gateway fill:#1f2a3d,stroke:#4da3ff,stroke-width:2px
    style Auth fill:#3a2d1f,stroke:#ffb84d,stroke-width:2px
    style Platform fill:#1f3a29,stroke:#33dd77,stroke-width:2px
    style WebApp fill:#261f3a,stroke:#bb66ff,stroke-width:2px

```

---

## 🔀 Service Routing & Context Paths

Understanding how requests flow through the gateway to backend services is critical for debugging and development.

### Service Configuration Table

| Service | Port | Context Path | Eureka Name | Frontend Route | Internal Route |
|---------|------|--------------|-------------|----------------|----------------|
| **Gateway** | 8080 | `/` (root) | GATEWAY-SERVICE | N/A (entry point) | N/A |
| **Auth Service** | 8081 | `/auth` | AUTH-SERVICE | `/auth-service/**` → `/auth/**` | `/auth/**` |
| **Backend Service** | 8082 | `/` (root) | BACKEND-SERVICE | `/backend-service/**` → `/**` | `/api/**` |
| **Platform Service** | 8083 | `/platform` | PLATFORM-SERVICE | `/platform-service/**` → `/platform/**` | `/platform/**` |
| **Eureka Server** | 8761 | `/` (root) | N/A | N/A (discovery only) | N/A |

### Gateway Route Patterns

The gateway uses two routing patterns:

1. **Frontend Proxy Routes** (`*-service/**`): Rewrites service name prefix to context path
2. **Internal Routes** (context paths): Direct routing for internal services

| Frontend Calls | Gateway Rewrites | Target Service |
|----------------|------------------|----------------|
| `/auth-service/api/v1/auth/login` | `/auth/api/v1/auth/login` | auth-service:8081 |
| `/platform-service/platform/api/v1/...` | `/platform/api/v1/...` | platform-service:8083 |
| `/backend-service/api/v1/entries` | `/api/v1/entries` | backend-service:8082 |

### URL Construction Examples

**From Frontend (via Gateway):**
```
http://localhost:8080/{service-name}/{context-path}/{endpoint}
```

**Examples:**

| Service | Endpoint | Full Gateway URL |
|---------|----------|------------------|
| Auth | Login | `http://localhost:8080/auth-service/api/v1/auth/login` |
| Auth | SSO Complete | `http://localhost:8080/auth-service/api/v1/auth/sso-complete` |
| Platform | SSO Config | `http://localhost:8080/platform-service/platform/api/v1/sso/config` |
| Backend | Entries | `http://localhost:8080/backend-service/api/v1/entries` |

### Direct Service Access (Development Only)

For debugging, you can bypass the gateway (include context path):

| Service | Direct URL Example |
|---------|-------------------|
| Auth | `http://localhost:8081/auth/api/v1/auth/login` |
| Backend | `http://localhost:8082/api/v1/entries` |
| Platform | `http://localhost:8083/platform/internal/tenants/xyz` |

> **⚠️ Important:** Auth-service has context path `/auth`, Platform-service has context path `/platform`. All endpoints are prefixed with the context path.

### Internal API Paths (Service-to-Service)

Internal APIs use `/internal/**` prefix and bypass JWT authentication. **Include context path!**

| From Service | To Service | Internal URL Pattern |
|--------------|------------|---------------------|
| auth-service | platform-service | `http://platform-service:8083/platform/internal/tenants/{id}` |
| auth-service | platform-service | `http://platform-service:8083/platform/internal/users/jit-provision` |
| backend-service | auth-service | `http://auth-service:8081/auth/internal/roles/lookup` |


---

## 🎯 Design Principles

### 1. Gateway-as-Gatekeeper (Security Boundary)
```
┌─────────────────────────────────────────────────────────────────┐
│                    EXTERNAL (Untrusted)                         │
│  Browser → JWT Token → ALB → Gateway Service                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  Gateway Service  │  ← ONLY JWT validator
                    │  • Validates JWT   │
                    │  • Extracts claims │
                    │  • Sets X-* headers│
                    │  • Rate limiting   │
                    └─────────┬─────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    INTERNAL (Trusted Network)                   │
│  Services trust X-Tenant-Id, X-User-Id headers from Gateway     │
│  No JWT validation in internal services                         │
└─────────────────────────────────────────────────────────────────┘
```

**Key Rules:**
- ✅ Gateway is the **ONLY** service that validates JWTs
- ✅ Internal services **NEVER** validate JWTs - they trust Gateway headers
- ✅ Gateway strips incoming `X-*` headers to prevent spoofing
- ✅ Fail-closed: requests without valid JWT are rejected

### 2. Database-per-Tenant Isolation
```
Platform DB (awsinfra)          Tenant DBs (dedicated)
┌──────────────────┐           ┌──────────────────┐
│  tenant table    │           │  tenant_acme_db  │
│  - id            │───────────│  • roles         │
│  - jdbc_url ─────┼───┐       │  • permissions   │
│  - status        │   │       │  • user_roles    │
└──────────────────┘   │       │  • entries       │
                       │       └──────────────────┘
                       │       ┌──────────────────┐
                       └──────►│  tenant_xyz_db   │
                               │  • roles         │
                               │  • permissions   │
                               │  • user_roles    │
                               │  • entries       │
                               └──────────────────┘
```

**Key Rules:**
- ✅ Each tenant gets a **dedicated PostgreSQL database**
- ✅ No `tenant_id` column in tenant tables (database IS the boundary)
- ✅ `TenantDataSourceRouter` switches datasource based on `X-Tenant-Id` header
- ✅ Flyway migrations run per-tenant database

### 3. AWS Infrastructure (Terraform-Managed)

| Component | Purpose | Terraform Resource |
|-----------|---------|-------------------|
| **Cognito User Pool** | User authentication, JWT tokens | `aws_cognito_user_pool` |
| **SSM Parameter Store** | Configuration (Cognito URLs, secrets) | `aws_ssm_parameter` |
| **Secrets Manager** | Tenant DB credentials | `aws_secretsmanager_secret` |
| **RDS PostgreSQL** | Platform + tenant databases | `aws_db_instance` |
| **Lambda** | PostConfirmation trigger for signup | `aws_lambda_function` |

**SSM Parameters (Set by Terraform):**
```
/cloud-infra/dev/cognito/issuer_uri     → https://cognito-idp.{region}.amazonaws.com/{poolId}
/cloud-infra/dev/cognito/jwks_uri       → {issuer}/.well-known/jwks.json
/cloud-infra/dev/cognito/user_pool_id   → us-east-1_xxxxxxxx
/cloud-infra/dev/cognito/client_id      → xxxxxxxxxxxxxxxxxxxxxxxxxx
```

**How Services Use SSM:**
- Gateway reads SSM on startup for JWT validation config
- Auth Service reads SSM for Cognito API calls
- No hardcoded URLs - everything from SSM

### 4. Authentication Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend (Amplify)
    participant C as Cognito
    participant L as Lambda (PostConfirm)
    participant G as Gateway
    participant A as Auth Service
    participant P as Platform Service

    Note over U,P: SIGNUP FLOW
    U->>F: Enter email, password
    F->>A: POST /api/v1/auth/signup
    A->>P: Create tenant (Platform API)
    P->>P: Create tenant DB
    A->>C: signUp (with clientMetadata)
    C->>U: Verification code email
    U->>F: Enter code
    F->>C: confirmSignUp
    C->>L: PostConfirmation trigger
    L->>C: Set custom:tenantId

    Note over U,P: LOGIN FLOW
    U->>F: Enter credentials
    F->>C: Authenticate
    C->>F: JWT with custom claims
    F->>G: API call + JWT
    G->>G: Validate JWT, extract claims
    G->>A: Forward + X-Tenant-Id, X-User-Id
    A->>A: Process with tenant context
```

### 5. Authorization Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant G as Gateway
    participant B as Backend Service
    participant A as Auth Service

    C->>G: GET /api/v1/entries (JWT)
    G->>G: Validate JWT
    G->>B: Forward + X-Tenant-Id, X-User-Id
    B->>B: @RequirePermission("entry:read")
    B->>A: POST /api/v1/permissions/check
    A->>A: Check user_roles + role_permissions
    A->>B: {hasPermission: true}
    B->>B: Execute business logic
    B->>C: Return entries
```

**Key Components:**
- `@RequirePermission` annotation on controllers
- `RemotePermissionEvaluator` calls Auth Service
- `LocalPermissionEvaluator` for Auth Service itself
- Caffeine cache (10 min TTL) for permission checks

### 6. Delete Account Flow

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as Auth Service
    participant P as Platform Service
    participant C as Cognito

    Note over U,C: DELETE ACCOUNT FLOW
    U->>F: Settings → Delete My Account
    F->>F: Show confirmation dialog
    U->>F: Type "DELETE" + Confirm
    F->>A: POST /api/v1/account/delete
    A->>P: DELETE /internal/tenants/{id}
    P->>P: tenant.status = DELETING
    P->>P: Insert deleted_accounts record
    P->>P: memberships.status = REMOVED
    P->>P: tenant.status = DELETED
    Note over P: TODO: Publish to SNS (Phase 6)
    P->>A: 200 OK
    A->>C: adminDeleteUser
    A->>F: 200 OK
    F->>F: authService.logout()
    F->>U: Redirect to login
```

**Key Design Decisions:**
- **Soft-delete:** Tenant DB is NOT dropped (orphaned until async cleanup in Phase 6)
- **Audit trail:** `deleted_accounts` table tracks all deletions for re-registration detection
- **Atomic lock:** `DELETING` status prevents concurrent deletion attempts
- **Future:** SNS/SQS-based async cleanup for multi-DB support (PostgreSQL, MongoDB, S3)

> [!IMPORTANT]
> **Platform-Service Context Path:** Platform-service uses context-path `/platform`. Internal API calls must use:
> - `DELETE /platform/internal/tenants/{id}` (correct)
> - NOT `/internal/tenants/{id}` (will return 404)

**Verified Working (2025-12-14):**
- ✅ Cognito user deleted via `adminDeleteUser`
- ✅ `tenant.status` set to `DELETED`
- ✅ `user_tenant_memberships.status` set to `REMOVED`
- ✅ Audit record inserted into `deleted_accounts`

---


## 📋 Service Responsibilities

### 🛡️ Gateway Service (Port 8080)
**Role:** Gatekeeper - Security boundary for ALL incoming requests

**Responsibilities:**
- ✅ **Authentication Validation** - Verify JWT tokens from Cognito (**Sole Validator**)
- ✅ **Tenant Context Extraction** - Extract tenant ID from token/headers
- ✅ **Header Enrichment** - Inject trusted headers (`X-Tenant-Id`, `X-User-Id`, `X-Authorities`, `X-Email`)
- ✅ **Load Balancing** - Route to healthy service instances via Eureka
- ✅ **Rate Limiting** - Redis-based tenant/IP rate limiting (10 req/s, burst 20)
- ✅ **Request Sanitization** - Strip incoming `X-*` headers to prevent spoofing

**Key Feature:** Fail-closed security - rejects requests without valid tenant context. Acts as the **only** OAuth2 Resource Server in the system.

---

### 🔐 Auth Service (Port 8081)
**Role:** Complete identity and permission management

**Responsibilities:**

#### Authentication
- ✅ **Multi-Provider Login Support:**
  - Email/Password via Cognito
  - SSO (Google, Microsoft, Azure AD)
  - SAML 2.0 (Ping, Okta, etc.)
  - OAuth2/OIDC flows
- ✅ **Signup Pipeline Orchestration:** (See [Signup Pipeline Architecture](#-signup-pipeline-architecture) below)
  - Unified idempotent pipeline for Personal, Organization, and SSO signups
  - 7 ordered actions with retry support and rollback
  - Calls Platform Service for tenant/database provisioning
- ✅ **Account Deletion:**
  - `POST /api/v1/account/delete` with "DELETE" confirmation
  - Calls Platform Service for tenant soft-delete
  - Deletes user from Cognito via `adminDeleteUser`
- ✅ **Session Management** - Token issuance, refresh, logout
- ✅ **MFA Support** - Via Cognito (SMS, TOTP)
- ✅ **Trusts Gateway:** Relies on `X-User-Id` and `X-Tenant-Id` headers. **No local JWT validation.**

#### API Key Authentication (B2B)
For programmatic access without interactive login:

- ✅ **Key Generation:** Users create API keys via `Settings → API Keys`
- ✅ **Secure Storage:** Only SHA-256 hash stored; raw key shown once at creation
- ✅ **Key Format:** `sk_live_` prefix + 32-byte random (256-bit entropy)
- ✅ **Gateway Filter:** `ApiKeyAuthenticationFilter` validates via platform-service
- ✅ **Header Injection:** Valid key → `X-Tenant-Id`, `X-User-Id`, `X-User-Email`
- ✅ **RBAC Inheritance:** API key inherits creator's permissions

**Usage:**
```bash
curl https://api.example.com/your-endpoint \
  -H "X-API-Key: sk_live_..."
```

**Management APIs:**
- `POST /api/v1/api-keys` - Create new key (returns raw key once)
- `GET /api/v1/api-keys` - List keys for tenant
- `DELETE /api/v1/api-keys/{id}` - Revoke key

**Internal API (gateway → platform-service):**
- `GET /internal/api-keys/validate?key=...` - Validate and return context

#### Authorization & Permissions

> **📘 Full Documentation:** See [DESIGN_PERMISSIONS.md](./DESIGN_PERMISSIONS.md) for comprehensive details on the permission system.

**Key Features:**
- **4-Tier Role Hierarchy:** `system-admin` → `admin` → `editor` → `viewer`
- **SSO Group Mapping:** Automatic role assignment based on IdP groups (Okta, Azure AD, Google)
- **Permission-Based Access Control (PBAC):** Resource-action model (e.g., `entry:read`, `group:manage`)
- **ACL Support:** Per-resource fine-grained sharing ("Google Drive" style)

**Permission APIs:**
- `POST /api/v1/permissions/check` - Remote permission validation
- `GET /api/v1/groups/mappings` - Group-role mapping management

**Role Assignment APIs:**
- `GET /api/v1/roles` - List available roles
- `POST /api/v1/roles/assign` - Assign role to user
- `PUT /api/v1/roles/users/{userId}` - Update user's role


**Technology:** AWS Cognito User Pools, Spring Security (OAuth2 Client only), JPA

**Email Verification Flow (B2C Personal Signup):**
1. User signs up → `signUp` API with `clientMetadata` (tenantId)
2. Cognito sends verification **code** via email
3. **Frontend** displays code input (VerifyEmailComponent)
4. User enters code → `confirmSignUp` API with `clientMetadata` (tenantId)
5. **Lambda PostConfirmation trigger** sets `custom:tenantId`
6. User redirected to login with tenant context in JWT

**Key Implementation Details:**
- `clientMetadata` must be passed during BOTH `signUp` AND `confirmSignUp`
- Gateway permits `/auth/signup/verify` without authentication
- Frontend stores `tenantId` in router state between signup and verify pages

**Lambda Functions:**
- `cognito-post-confirmation` - Sets `custom:tenantId` after email verification.
  - **Purpose:** Persists tenant context into the user profile so it's available in the JWT on every login.
  - **Note:** Role is managed in tenant DB (`user_roles` table), not in Cognito.
  - Runtime: Python 3.11
  - Trigger: Cognito PostConfirmation event
  - Permissions: AdminUpdateUserAttributes on User Pool

**Email Verification Troubleshooting:**

> [!WARNING]
> **Common Issue:** If verification codes aren't being sent, check that `auto_verified_attributes` is set to `["email"]` in Cognito User Pool.

| Issue | Cause | Fix |
|-------|-------|-----|
| No verification email received | `auto_verified_attributes` is null | `aws cognito-idp update-user-pool --user-pool-id <ID> --auto-verified-attributes email` |
| Emails not arriving (SES) | SES sandbox mode - only sends to verified emails | Verify recipient: `aws ses verify-email-identity --email-address <email>` |
| Gmail filtering emails | Using `no-reply@verificationemail.com` (Cognito default) | Switch to SES with your own verified domain |

**Check Cognito Configuration:**
```bash
aws cognito-idp describe-user-pool --user-pool-id <ID> \
  --query 'UserPool.{AutoVerified:AutoVerifiedAttributes,EmailConfig:EmailConfiguration}'
```

**Expected Output:**
```json
{
  "AutoVerified": ["email"],
  "EmailConfig": {"EmailSendingAccount": "COGNITO_DEFAULT"}
}
```

**Internal API for Tenant Provisioning/Deletion:**
- During signup, auth-service calls `POST /internal/tenants` to provision the tenant
- During account deletion, auth-service calls `DELETE /internal/tenants/{id}` to soft-delete
- These endpoints bypass authentication (service-to-service calls)
- Located in `TenantInternalController.java` in platform-service

---

### 🔐 Multi-Tenant Login Flow (Email-First with Smart Tenant Selection)

**Purpose:** Allow users who belong to multiple tenants to select which workspace to access during login.

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Email-First Login Flow                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. EMAIL STEP                                                              │
│     User enters email  ────────────►  GET /api/v1/auth/lookup              │
│                                       Returns: List of tenants for email   │
│                                                                             │
│  2. SELECTION STEP (if multiple tenants)                                    │
│     ┌───────────────────────┐  ┌───────────────────────┐                   │
│     │ 🏠 Personal Workspace │  │ 🏢 Acme Corporation   │                   │
│     │     Owner             │  │     Administrator      │                   │
│     │     [Default]         │  │     [SSO]              │                   │
│     └───────────────────────┘  └───────────────────────┘                   │
│                                                                             │
│  3. PASSWORD STEP                                                           │
│     User enters password ────►  Cognito Auth with clientMetadata:          │
│                                 { selectedTenantId: "tenant-xxx" }         │
│                                                                             │
│  4. TOKEN GENERATION                                                        │
│     PreTokenGeneration Lambda ─►  Override custom:tenantId in JWT          │
│                                   with selected tenant                      │
│                                                                             │
│  5. AUTHENTICATED                                                           │
│     JWT contains: { custom:tenantId: "tenant-xxx", ... }                   │
│     User enters selected workspace                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Cognito Lambda Triggers:**

| Lambda | Trigger | Purpose |
|--------|---------|---------|
| **PostConfirmation** | After email verification | Sets `custom:tenantId` during signup |
| **PreTokenGeneration** | Before JWT issued | Overrides `custom:tenantId`, extracts SSO groups, syncs to platform |

**Database Schema (Platform Service):**
```sql
-- Maps users to their tenant memberships
CREATE TABLE user_tenant_memberships (
    id UUID PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,           -- User identifier
    cognito_user_id VARCHAR(255),               -- Set after first login
    tenant_id VARCHAR(64) NOT NULL,             -- FK to tenant
    role_hint VARCHAR(50) DEFAULT 'member',     -- Display hint (owner/admin/member/guest)
    is_owner BOOLEAN DEFAULT false,             -- True if user created tenant
    is_default BOOLEAN DEFAULT false,           -- User's default workspace
    last_accessed_at TIMESTAMPTZ,               -- For sorting by recent
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    status VARCHAR(32) DEFAULT 'ACTIVE',        -- ACTIVE, REMOVED, SUSPENDED
    UNIQUE(user_email, tenant_id)
);
```

**API Endpoints:**

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/api/v1/auth/lookup` | GET | Public | Lookup tenants by email |
| `/api/v1/auth/last-accessed` | PATCH | Public | Update last accessed timestamp |
| `/internal/memberships/by-email` | GET | Internal | Backend lookup for memberships |
| `/internal/memberships` | POST | Internal | Create membership record |

**Frontend State Machine:**
```
                    ┌──────────┐
                    │  email   │ ◄─── Start
                    └────┬─────┘
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
        0 tenants               1 tenant
      [Show Error]            [Auto-select]
                                    │
              ▼                     ▼
        ┌────────────────┐   ┌──────────┐
        │ select-tenant  │◄──│ password │
        └───────┬────────┘   └──────────┘
                │                   ▲
                └───────────────────┘
```

**Security Considerations:**
- Lookup endpoint returns empty for unknown emails (prevents enumeration)
- SSO tenants display message but don't process (Phase 4 feature)
- PreTokenGeneration Lambda only overrides if clientMetadata is provided
- Last accessed timestamp is non-critical (failure doesn't block login)

**Files Created:**
- `terraform/lambdas/cognito-pre-token-generation/` - Lambda source + Terraform module
- `platform-service/.../membership/` - Entity, repository, service, controller
- `auth-service/.../TenantLookupController.java` - Public lookup API
- `frontend/src/app/core/models/` - TypeScript interfaces and enums
- `frontend/src/app/features/auth/login.component.*` - Multi-step UI

---

## 🔄 Signup Pipeline Architecture

> **Single Source of Truth:** Auth-service orchestrates ALL signup flows through a unified, idempotent pipeline.

### Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                   UNIFIED SIGNUP PIPELINE                       │
│                   (auth-service orchestrates ALL)               │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        Personal         Organization     Google SSO
        Signup           Signup           (via Lambda)
              │               │               │
              └───────────────┼───────────────┘
                              ▼
                    SignupPipeline.execute(ctx)
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
   Action 10             Action 20             Action 30...
   (idempotent)          (idempotent)          (idempotent)
```

### Pipeline Actions Matrix

| Order | Action | Personal | Org | Google SSO | Idempotency Check |
|-------|--------|:--------:|:---:|:----------:|-------------------|
| 10 | GenerateTenantId | ✅ | ✅ | ✅ | Skip if `ctx.tenantId` set |
| 20 | ProvisionTenant | ✅ | ✅ | ✅ | `platformClient.tenantExists()` |
| 30 | CreateCognitoUser | ✅ | ✅ | ❌ | `cognitoClient.getUser()` |
| 40 | CreateMembership | ✅ | ✅ | ✅ | `membershipRepo.exists()` |
| 50 | AssignRoles | ✅ | ✅ | ✅ | `userRoleRepo.exists()` |
| 60 | SendVerificationEmail | ✅ | ✅ | ❌ | `ctx.emailSent` flag |
| 70 | CreateOrgSettings | ❌ | ✅ | ❌ | `orgSettingsRepo.exists()` |

### State Machine Diagram

```mermaid
stateDiagram-v2
    [*] --> GenerateTenantId: Start Signup
    
    GenerateTenantId --> ProvisionTenant: tenantId generated
    GenerateTenantId --> ProvisionTenant: [already exists] skip
    
    ProvisionTenant --> CreateCognitoUser: tenant provisioned
    ProvisionTenant --> CreateCognitoUser: [SSO] skip
    
    CreateCognitoUser --> CreateMembership: cognito user created
    CreateCognitoUser --> CreateMembership: [exists] skip
    
    CreateMembership --> AssignRoles: membership created
    
    AssignRoles --> SendVerificationEmail: roles assigned
    AssignRoles --> CreateOrgSettings: [SSO] skip email
    
    SendVerificationEmail --> CreateOrgSettings: email sent
    SendVerificationEmail --> [*]: [Personal] complete
    
    CreateOrgSettings --> [*]: [Org] complete
    
    note right of GenerateTenantId
        Personal: user-{name}-{ts}
        Org: slugified-company-name
        SSO: user-{email}-{ts}
    end note
```

### Flow Comparison

| Aspect | Personal Signup | Organization Signup | Google SSO |
|--------|-----------------|---------------------|------------|
| **Entry Point** | `POST /signup/personal` | `POST /signup/organization` | Lambda → `POST /sso-complete` |
| **Tenant ID** | `user-john-84567` | `acme-corp` | `user-john-84567` |
| **Tenant Type** | PERSONAL | ORGANIZATION | PERSONAL |
| **Initial Role** | viewer | admin | viewer |
| **Cognito User** | Created by auth-service | Created by auth-service | Created by Cognito/Google |
| **Email Verification** | Required | Required | Skipped (Google verified) |
| **Org Settings** | Skipped | Created (tier, limits) | Skipped |
| **Steps Executed** | 10,20,30,40,50,60 | 10,20,30,40,50,60,70 | 10,20,40,50 |

### Personal Signup Sequence

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant A as Auth Service
    participant P as Platform Service
    participant C as Cognito
    
    U->>F: Fill signup form
    F->>A: POST /api/v1/signup/personal
    
    Note over A: Pipeline starts
    A->>A: GenerateTenantId (user-john-12345)
    A->>P: ProvisionTenant
    A->>C: CreateCognitoUser
    A->>P: CreateMembership
    A->>A: AssignRoles (viewer)
    A->>C: SendVerificationEmail
    
    A->>F: {tenantId, message: "Verify email"}
    F->>U: Show verification page
```

### Organization Signup Sequence

```mermaid
sequenceDiagram
    participant U as Admin
    participant F as Frontend
    participant A as Auth Service
    participant P as Platform Service
    participant C as Cognito
    
    U->>F: Fill org signup form
    F->>A: POST /api/v1/signup/organization
    
    Note over A: Pipeline starts (all 7 steps)
    A->>A: GenerateTenantId (acme-corp)
    A->>P: ProvisionTenant (with tier, maxUsers)
    A->>C: CreateCognitoUser
    A->>P: CreateMembership (role: admin)
    A->>A: AssignRoles (admin)
    A->>C: SendVerificationEmail
    A->>P: CreateOrgSettings
    
    A->>F: {tenantId, message: "Verify email"}
```

### Google SSO Sequence (JIT Provisioning)

```mermaid
sequenceDiagram
    participant U as User
    participant F as Frontend
    participant C as Cognito Hosted UI
    participant G as Google
    participant L as Lambda
    participant A as Auth Service
    participant P as Platform Service
    
    U->>F: Click "Sign in with Google"
    F->>C: Redirect to Hosted UI
    C->>G: OAuth redirect
    U->>G: Authenticate
    G->>C: ID token
    
    Note over L: PreTokenGeneration Lambda
    L->>A: POST /sso-complete
    
    Note over A: Pipeline (4 steps only)
    A->>A: GenerateTenantId
    A->>P: ProvisionTenant
    A->>P: CreateMembership
    A->>A: AssignRoles
    Note over A: Skip Cognito user (exists)<br/>Skip email (Google verified)
    
    A->>L: {tenantId, role}
    L->>C: Set custom:tenantId in token
    C->>F: Tokens
```

### Idempotent Action Pattern

```java
public interface SignupAction {
    String getName();
    int getOrder();
    boolean supports(SignupContext ctx);      // Should run for this signup type?
    boolean isAlreadyDone(SignupContext ctx); // Idempotency check
    void execute(SignupContext ctx);
    void rollback(SignupContext ctx);
}

// Pipeline execution
for (SignupAction action : orderedActions) {
    if (action.supports(ctx)) {
        if (action.isAlreadyDone(ctx)) {
            log.info("Skipping {} (already done)", action.getName());
            continue;
        }
        action.execute(ctx);
    }
}
```

### Key Files

| File | Purpose |
|------|---------|
| `auth-service/signup/pipeline/SignupPipeline.java` | Orchestrator |
| `auth-service/signup/pipeline/SignupContext.java` | State tracking |
| `auth-service/signup/pipeline/SignupAction.java` | Action interface |
| `auth-service/signup/actions/*.java` | Action implementations |
| `auth-service/controller/SsoCompletionController.java` | Lambda callback |

### Retry & Error Handling

| Scenario | Behavior |
|----------|----------|
| Network failure mid-pipeline | Retry safe: completed actions skipped |
| Duplicate signup request | Returns existing tenant info |
| Lambda timeout + retry | Idempotent: checks state before each action |
| Partial failure | Rollback executed for completed actions |

---

### B2B vs B2C Feature Differentiation

**Tenant Type Storage:**
- `tenant_type` is stored in **platform DB** (`awsinfra.tenant` table)
- Values: `PERSONAL` (B2C) or `ORGANIZATION` (B2B)

**JWT Contents (What's in the token):**
| Claim | Example | Source | Note |
|-------|---------|--------|------|
| `custom:tenantId` | `user-john-12345` | PostConfirmation / PreTokenGeneration Lambda | **Required** - Used for routing |
| `custom:tenantType` | `PERSONAL` | PreTokenGeneration Lambda | B2C or B2B designation |
| `custom:groups` | `Engineering,Marketing` | PreTokenGeneration Lambda | SSO groups (optional) |
| `email` | `user@example.com` | Cognito standard | |

> [!IMPORTANT]
> **Role is NOT in JWT!** Authorization uses `user_roles` table in tenant DB, queried via Auth Service.

> [!NOTE]
> **TenantType Architecture (v6.0):** `tenant_type` is NOT stored in Cognito or JWT. Frontend looks it up from platform DB after login:
> ```typescript
> // Frontend auth.service.ts
> const tenantType = await this.lookupTenantType(tenantId);  // GET /platform/api/v1/tenants/{id}
> ```
> This ensures platform DB is the single source of truth for tenant metadata.

**Feature Matrix:**
| Feature | PERSONAL (B2C) | ORGANIZATION (B2B) |
|---------|----------------|-------------------|
| Invite users | ❌ Hidden | ✅ Available |
| Team management | ❌ Hidden | ✅ Available |
| Role assignment | ❌ Hidden | ✅ Available |
| Max users | 1 (fixed) | 5-1000 (by tier) |
| Trial period | None | 30 days |
| Billing | Free tier | Subscription |

**Frontend Implementation:**
```typescript
// Check tenant type before showing features
if (tenant.tenantType === 'ORGANIZATION') {
  showTeamManagement();
  showInviteButton();
} else {
  // PERSONAL - hide multi-user features
}
```

**Backend Enforcement:**
```java
// InvitationController.java
if (tenant.getTenantType() == TenantType.PERSONAL) {
    throw new ForbiddenException("Invitations not available for personal accounts");
}
```

---

**Email Configuration Options:**

| Option | Best For | From Address | Daily Limit | Cost |
|--------|----------|--------------|-------------|------|
| **COGNITO_DEFAULT** (current) | Development/Testing | `no-reply@verificationemail.com` | ~50 | FREE |
| **SES with verified identity** | Production | Your custom address | 62,000/month free | FREE then $0.10/1000 |

> [!TIP]
> For production, switch to SES with your own verified domain for better deliverability and branding. See `terraform/main.tf` for the commented SES configuration.

**Authorization Architecture:**
- `PermissionService` - Core permission evaluation logic
- `UserRoleService` - Role assignment/revocation
- `UserStatsService` - User statistics aggregation for admin dashboard
- `AuthServicePermissionEvaluator` - Local evaluator (direct database access)


---

### ⚙️ Platform Service (Port 8083)
**Role:** Control plane for entire tenant lifecycle

**Responsibilities:**

#### Tenant Provisioning
- ✅ **Database Provisioning:**
  - **Database-per-tenant** - Creates dedicated PostgreSQL database for each tenant
- ✅ **Database User Management** - Creates DB credentials, stores in AWS Secrets Manager
- ✅ **Schema Initialization** - Runs Flyway migrations for each tenant
- ✅ **Tenant Registry** - Maintains master metadata (JDBC URLs, status, tier)

#### SSO/Social Login JIT Provisioning

> [!IMPORTANT]
> **All tenant creation flows MUST use `TenantProvisioningService.provision()`** to ensure proper database setup. Never create tenant records directly in the repository.

- ✅ **Personal Accounts (Google Social Login):**
  - Lambda generates `personal-{username}` tenant ID in JWT
  - Callback calls JIT endpoint → `TenantProvisioningService.provision()`
  - Uses `ProvisionTenantRequest.forPersonal(id, email)` factory method
  - Same pipeline as manual signup: creates DB + runs migrations
- ✅ **Organization SSO (SAML/OIDC):**
  - Tenants must be pre-provisioned via admin flow
  - JIT only creates user membership, not tenant

#### Tenant Management
- ✅ **Lifecycle Operations:**
  - Activate, Suspend, Delete tenants
  - **Tenant Deletion:** (`DELETE /api/tenants/{id}`)
    - Hard delete of tenant entry (logically marked `DELETED`)
    - Requires `tenant:delete` permission
    - **Note:** Actual database drop is currently manual/safety-gated
  - Upgrade/downgrade tiers (STANDARD, PREMIUM, ENTERPRISE)
- ✅ **Metadata Storage** - Master database tracks all tenant configurations
- ✅ **Service Coordination** - Notifies backend services of new tenants
- ✅ **Trusts Gateway:** Relies on `X-User-Id` and `X-Tenant-Id` headers. **No local JWT validation.**

#### System Administration
- ✅ **Super Admin Bootstrapping:**
  - Created via `scripts/bootstrap-system-admin.sh`
  - Cognito: `custom:tenantId=system`
  - Database: `system-admin` role with `*:*` permission
- ✅ **Organization Management Profile:**
  - `GET /api/v1/organizations` - Get organization profile for current tenant

  - `PUT /api/v1/organizations` - Update organization profile (company name, industry, size, website, logo)
- ✅ **Tenant Entity Fields:**
  - Core: `id`, `name`, `status`, `tenantType`, `slaTier`
  - Organization Profile: `companyName`, `industry`, `companySize`, `website`, `logoUrl`
  - Limits: `maxUsers`, `subscriptionStatus`, `trialEndsAt`

#### Admin Operations
- ✅ **Internal APIs** - Tenant migration triggers, health checks
- ✅ **Billing Integration** - Stripe subscription management, webhook processing
- 🔜 **Compliance** - Data residency, retention policies

**Databases:**
- **Master DB:** Tenant registry, system configuration
- **Tenant DBs:** Created dynamically per tenant

---

### 💳 Stripe Billing Integration

**Role:** Subscription management, payment processing, and billing portal

The platform includes a complete Stripe integration for B2B subscription billing.

#### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `BillingController` | platform-service | Checkout, portal, status APIs |
| `WebhookController` | platform-service | Stripe webhook receiver |
| `WebhookService` | platform-service | Event processing with idempotency |
| `StripeService` | platform-service | Stripe API wrapper |
| `BillingProperties` | platform-service | Tier configuration |
| `BillingComponent` | frontend | Subscription UI |

#### Subscription Tiers

| Tier | Price | Max Users |
|------|-------|-----------|
| **Starter** | $10/mo | 5 users |
| **Pro** | $20/mo | 50 users |
| **Enterprise** | Custom | Unlimited |

#### Webhook Events Handled

| Event | Action |
|-------|--------|
| `checkout.session.completed` | Set tenant to ACTIVE, save subscription ID |
| `customer.subscription.updated` | Update period end, sync status |
| `customer.subscription.deleted` | Set status to CANCELLED |
| `invoice.paid` | Log payment success |
| `invoice.payment_failed` | Set status to PAST_DUE |

#### Configuration (Environment Variables)

```bash
BILLING_ENABLED=true
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_STARTER=price_...
STRIPE_PRICE_PRO=price_...
STRIPE_PRICE_ENTERPRISE=price_...
```

#### API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/v1/billing/checkout` | Create Stripe Checkout session |
| `POST` | `/api/v1/billing/portal` | Create Customer Portal session |
| `GET` | `/api/v1/billing/status` | Get current subscription status |
| `POST` | `/billing/webhook` | Stripe webhook receiver (no auth) |

#### Security

- Webhooks are **publicly accessible** but secured via Stripe signature verification
- `TenantContextFilter` excludes `/billing/webhook` from tenant header requirement
- Gateway security permits webhook path without JWT

---

### 📦 Backend Service (Port 8082)
**Role:** Domain-specific business logic - **THIS IS THE REPLACEABLE PART**

**Current Implementation:**
- ✅ Example "Entry" CRUD operations
- ✅ Demonstrates multi-tenant data isolation patterns
- ✅ Shows how to use tenant context from headers
- ✅ **Authorization Integration:**
  - `RemotePermissionEvaluator` - Calls auth-service to validate permissions
  - `@RequirePermission` annotations on endpoints (e.g., `entry:read`, `entry:create`)
  - `TenantContextFilter` - Extracts `X-Tenant-Id` from headers
  - `CacheConfiguration` - Caches permission check results (10 min TTL)
- ✅ **Trusts Gateway:** Relies on `X-User-Id` and `X-Tenant-Id` headers. **No local JWT validation.**

**How to Replace:**
1. Keep the multi-tenant data access patterns
2. Keep the authorization infrastructure (`RemotePermissionEvaluator`, `TenantContextFilter`)
3. Replace `Entry` entity with your domain (Order, Product, Task, etc.)
4. Add `@RequirePermission` to your endpoints
5. Register with Eureka using same pattern

**Examples of What You Might Build:**
- `work-service` - Task/project management
- `inventory-service` - Stock/warehouse management
- `crm-service` - Customer relationship management
- `analytics-service` - Reporting and dashboards

**Key Pattern:** Always read tenant from `X-Tenant-Id` header injected by Gateway

---

## 🔧 How to Build Your Service (Replacing Backend-Service)

This section provides a detailed guide for replacing the example `backend-service` with your own domain service.

### Step 1: Copy the Structure
```bash
cp -r backend-service your-service-name
cd your-service-name
```

### Step 2: Update Build Configuration
Update `pom.xml`:
```xml
<artifactId>your-service-name</artifactId>
<name>Your Service Name</name>
```

Update `application.yml`:
```yaml
spring:
  application:
    name: your-service-name
server:
  port: 8082  # Or choose a different port
```

### Step 3: Keep These Files (Critical for Multi-Tenancy)
| File | Purpose |
|------|---------|
| `TenantAwareDatabaseConfig.java` | Routes queries to correct tenant database |
| `TenantContextFilter.java` | Extracts `X-Tenant-Id` from request headers |
| `RemotePermissionEvaluator.java` | Validates permissions against auth-service |
| `CacheConfiguration.java` | Caches permission checks (performance) |

### Step 4: Replace Domain Entities
Delete the example `Entry` entity and create your own:
```java
@Entity
@Table(name = "orders")  // Your domain table
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String customerId;
    private BigDecimal totalAmount;
    // Your domain fields...
}
```

### Step 5: Add Permissions to Your Endpoints
```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping
    @RequirePermission(resource = "order", action = "read")
    public List<Order> getOrders() { ... }
    
    @PostMapping
    @RequirePermission(resource = "order", action = "create")
    public Order createOrder(@RequestBody OrderDto dto) { ... }
}
```

### Step 6: Add Your Permissions to Auth-Service
Add SQL migration in `auth-service/src/main/resources/db/migration/tenant/`:
```sql
-- V100__add_order_permissions.sql
INSERT INTO permissions (id, resource, action, description) VALUES
    (gen_random_uuid(), 'order', 'read', 'View orders'),
    (gen_random_uuid(), 'order', 'create', 'Create orders'),
    (gen_random_uuid(), 'order', 'update', 'Update orders'),
    (gen_random_uuid(), 'order', 'delete', 'Delete orders');

-- Assign to admin role
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'admin', id FROM permissions WHERE resource = 'order';
```

### Step 7: Update Gateway Routes
Add your service to `gateway-service/src/main/resources/application.yml`:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: your-service
          uri: lb://YOUR-SERVICE-NAME
          predicates:
            - Path=/api/v1/orders/**
```

### Step 8: Update Docker Compose
Add your service to `docker-compose.yml`:
```yaml
your-service:
  build: ./your-service-name
  ports:
    - "8084:8084"
  environment:
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
  depends_on:
    eureka-server:
      condition: service_healthy

---

### 🔍 Eureka Server (Port 8761)
**Role:** Service discovery and health monitoring

**Responsibilities:**
- ✅ Service registration - All services register on startup
- ✅ Health checks - Monitors service availability
- ✅ Load balancing - Gateway uses Eureka to find healthy instances
- ✅ Failover - Automatically routes around unhealthy services

---

## 🔒 Security Boundaries (Architecture Principle)

> [!IMPORTANT]
> **Gateway-Only Authentication** - This is a core architectural principle.

### Network Trust Model
```
┌─────────────────────────────────────────────────────────────┐
│                    PUBLIC INTERNET                          │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTPS (TLS)
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   GATEWAY SERVICE                           │
│   • JWT Validation (SOLE validator)                         │
│   • Header Sanitization (strips X-*)                        │
│   • Header Enrichment (injects X-Tenant-Id, X-User-Id)     │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP (Internal Network)
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              PRIVATE NETWORK (VPC / Docker Network)         │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│   │ Auth     │  │ Platform │  │ Backend  │                 │
│   │ Service  │  │ Service  │  │ Service  │                 │
│   └──────────┘  └──────────┘  └──────────┘                 │
│   • Trust Gateway headers                                   │
│   • ZERO auth logic                                         │
│   • TenantContextFilter reads X-Tenant-Id                  │
└─────────────────────────────────────────────────────────────┘
```

### Implementation Rules
1. **Gateway:** ONLY service with `spring-boot-starter-oauth2-resource-server`
2. **Backend Services:** NO `@EnableWebSecurity`, NO `SecurityConfig.java`
3. **TenantContextFilter:** Blocks requests without `X-Tenant-Id` (in production)
4. **Authorization:** Via `RemotePermissionEvaluator` calling Auth Service

---

## 🔐 Security & Authentication Flow

### User Signup Flow
```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Auth
    participant Platform
    participant Cognito
    participant TenantDB

    User->>Gateway: POST /auth/signup/personal
    Gateway->>Auth: Forward signup request
    Auth->>Auth: Generate tenant ID
    Auth->>Platform: POST /api/tenants<br/>(provision new tenant)
    Platform->>TenantDB: CREATE DATABASE tenant_xyz
    Platform->>TenantDB: Run Flyway migrations
    Platform-->>Auth: Tenant ready
    Auth->>Cognito: AdminCreateUser<br/>custom:tenantId=xyz
    Cognito-->>Auth: User created
    Auth-->>Gateway: Signup successful
    Gateway-->>User: 201 Created
```

### User Login & Request Flow
```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Auth
    participant Cognito
    participant Backend
    participant TenantDB

    User->>Gateway: POST /login (email + password)
    Gateway->>Auth: Authenticate
    Auth->>Cognito: AdminInitiateAuth
    Cognito-->>Auth: JWT Token (with custom:tenantId)
    Auth-->>Gateway: JWT Token
    Gateway-->>User: JWT Token

    Note over User,TenantDB: Subsequent API Request

    User->>Gateway: GET /api/entries<br/>Authorization: Bearer JWT
    Gateway->>Gateway: Validate JWT signature
    Gateway->>Gateway: Extract tenantId from token
    Gateway->>Gateway: Inject X-Tenant-Id: xyz header
    Gateway->>Backend: GET /api/entries<br/>X-Tenant-Id: xyz<br/>X-User-Id: user123
    Backend->>Backend: Read X-Tenant-Id header
    Backend->>TenantDB: Connect to tenant_xyz database
    TenantDB-->>Backend: Query results
    Backend-->>Gateway: Response
    Gateway-->>User: Response
```

### Forgot Password Flow
```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant Gateway
    participant Auth
    participant Cognito

    Note over User,Cognito: Step 1: Request Password Reset
    User->>Frontend: Click "Forgot Password?"
    Frontend->>Gateway: POST /auth/api/v1/auth/forgot-password<br/>{email}
    Gateway->>Auth: Forward request
    Auth->>Cognito: ForgotPassword API<br/>(username, clientId, secretHash)
    Cognito->>User: Send 6-digit code via email
    Cognito-->>Auth: CodeDeliveryDetails
    Auth-->>Gateway: 200 OK (generic message)
    Gateway-->>Frontend: Success
    Frontend->>Frontend: Show code + password form

    Note over User,Cognito: Step 2: Reset Password
    User->>Frontend: Enter code + new password
    Frontend->>Gateway: POST /auth/api/v1/auth/reset-password<br/>{email, code, newPassword}
    Gateway->>Auth: Forward request
    Auth->>Cognito: ConfirmForgotPassword API<br/>(code, newPassword, secretHash)
    Cognito-->>Auth: Password reset success
    Auth-->>Gateway: 200 OK
    Gateway-->>Frontend: Success
    Frontend->>Frontend: Redirect to login
```

### Multi-Account Per Email
Users can own multiple tenants (workspaces) using the same email address:
- One personal workspace per email
- Multiple organization workspaces (as owner or member)

```mermaid
sequenceDiagram
    participant User
    participant Auth
    participant Platform
    participant Cognito
    participant TenantDB

    Note over User,TenantDB: Existing User Creates New Organization

    User->>Auth: POST /signup/organization<br/>{email, companyName}
    Auth->>Platform: Provision new tenant (org2)
    Platform->>TenantDB: CREATE DATABASE, migrations
    Platform->>Platform: Create membership record
    Platform-->>Auth: Tenant ready
    
    Auth->>Cognito: AdminGetUser (check exists)
    Cognito-->>Auth: User exists ✓
    
    Note over Auth: Skip Cognito registration<br/>Assign role directly
    
    Auth->>Cognito: Get user sub (userId)
    Auth->>TenantDB: assignRole(userId, "admin", org2)
    
    Auth-->>User: "New workspace created!"
```

**Key Components:**
- `CognitoUserRegistrar.registerIfNotExists()` - Checks if user exists before registration
- `SignupServiceImpl.assignRoleForExistingUser()` - Assigns admin role for existing users
- `MembershipService` - Tracks user-tenant relationships in `user_tenant_memberships`


### Authorization Flow (Permission Check)
```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Backend
    participant Auth
    participant Cache
    participant DB[Auth DB]

    Note over User,DB: User makes a protected API call

    User->>Gateway: GET /api/v1/entries<br/>Authorization: Bearer JWT
    Gateway->>Gateway: Validate JWT
    Gateway->>Gateway: Extract tenantId, userId from token
    Gateway->>Gateway: Inject X-Tenant-Id, X-User-Id headers
    
    Gateway->>Backend: GET /api/v1/entries<br/>X-Tenant-Id: tenant-123<br/>X-User-Id: user-456
    
    Backend->>Backend: TenantContextFilter extracts X-Tenant-Id
    Backend->>Backend: AuthorizationAspect intercepts<br/>@RequirePermission(resource="entry", action="read")
    
    Backend->>Cache: Check cache: user-456:tenant-123:entry:read?
    
    alt Cache Hit
        Cache-->>Backend: true/false (cached result)
    else Cache Miss
        Backend->>Auth: POST /api/v1/permissions/check<br/>{userId: "user-456", tenantId: "tenant-123",<br/>resource: "entry", action: "read"}
        
        Auth->>DB: Get user roles for user-456 in tenant-123
        DB-->>Auth: [user]
        
        Auth->>DB: Check role permissions<br/>role=user, resource=entry, action=read
        DB-->>Auth: true (permission exists)
        
        Auth-->>Backend: {allowed: true}
        Backend->>Cache: Store result (10 min TTL)
    end
    
    alt Permission Denied
        Backend-->>Gateway: 403 Forbidden
        Gateway-->>User: 403 Access Denied
    else Permission Granted
        Backend->>Backend: Proceed with business logic
        Backend-->>Gateway: 200 OK + data
        Gateway-->>User: 200 OK + data
    end
```

**Key Components:**
- **TenantContextFilter** (`common-infra`) - Extracts tenant ID from headers into ThreadLocal
- **AuthorizationAspect** (`common-infra`) - AOP interceptor for `@RequirePermission` annotations
- **RemotePermissionEvaluator** (`backend/platform`) - Calls auth-service to check permissions
- **PermissionService** (`auth-service`) - Core evaluation logic with database queries
- **Caffeine Cache** - 10-minute TTL cache to reduce auth-service calls

**Authorization Decision Logic:**
1. Check if user has `super-admin` role → Grant all permissions (`*:*`)
2. Query `user_roles` table for user's active roles in the tenant
3. For each role, check `role_permissions` and `permissions` tables
4. Return `true` if any role grants the requested `resource:action` permission

**Permission Single Source of Truth:**
```
┌─────────────────────────────────────────────────────────────┐
│                     auth-service                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             PermissionService.hasPermission()        │  │
│  │                 (queries user_roles table)           │  │
│  └──────────────────────────────────────────────────────┘  │
│              ▲                              ▲               │
│     (direct call)                    (via HTTP)             │
│              │                              │               │
│  AuthServicePermissionEvaluator    PermissionController     │
│       (used by auth-service)          /api/v1/permissions   │
│                                             ▲               │
└─────────────────────────────────────────────│───────────────┘
                                              │
                        RemotePermissionEvaluator (HTTP POST)
                        (used by backend/platform services)
```

> [!IMPORTANT]
> All permission checks route through auth-service's `PermissionService` which queries the tenant database. This ensures:
> - **Single source of truth** - Permissions live in DB, not hardcoded
> - **Real-time updates** - Role changes take effect immediately (after cache expires)
> - **Consistent behavior** - All services use same permission logic

---

## 🔐 Roles and Permissions Model

This section provides a comprehensive overview of the Role-Based Access Control (RBAC) system implemented in the platform.

### Role Hierarchy

```mermaid
graph TB
    subgraph "Platform Level"
        SA[🔴 super-admin<br/>Platform Owner]
    end
    
    subgraph "Tenant Level"
        TA[🟠 admin<br/>Organization Admin]
        TE[🟡 editor<br/>Content Manager]
        TV[🔵 viewer<br/>Read-Only]
        TU[🟢 user<br/>Standard User]
        TG[⚪ guest<br/>Minimal Access]
    end
    
    SA -.->|manages| TA
    TA -->|manages| TE
    TA -->|manages| TV
    TA -->|manages| TU
    TA -->|manages| TG
```

### Role Definitions

| Role | Scope | Description | Typical Use Case |
|------|-------|-------------|------------------|
| **super-admin** | Platform | Full system access. Manages all tenants. | System operators, DevOps |
| **admin** | Tenant | Full access within organization. Manage users, roles, settings. | Organization owners, IT admins |
| **editor** | Tenant | Create, edit, delete content. Cannot manage users. | Power users, content managers |
| **viewer** | Tenant | Read-only access to organization data. | External contractors, auditors |
| **user** | Tenant | Standard CRUD on business data. | Regular employees |
| **guest** | Tenant | Minimal read-only access. | Limited external access |

### Permission Model

Permissions follow a **Resource:Action** pattern:

```
<resource>:<action>

Examples:
- entry:read      → Read data entries
- entry:create    → Create new entries
- user:invite     → Invite users to organization
- tenant:delete   → Delete a tenant (super-admin only)
- *:*             → Wildcard (all permissions)
```

### Default Role Permissions

#### super-admin
```
*:*                    → All permissions (wildcard)
```

#### admin
| Resource | Actions |
|----------|---------|
| entry | read, create, update, delete |
| user | read, invite, update, delete |
| role | read, assign, revoke |
| organization | read, manage |
| stats | read |

#### editor
| Resource | Actions |
|----------|---------|
| entry | read, create, update, delete |
| resources | read, create, edit, share |

#### viewer
| Resource | Actions |
|----------|---------|
| entry | read |
| resources | read, view_metadata |

#### user
| Resource | Actions |
|----------|---------|
| entry | read, create, update, delete |

#### guest
| Resource | Actions |
|----------|---------|
| entry | read |

### Database Schema

```mermaid
erDiagram
    roles {
        uuid id PK
        string name
        string description
        enum scope "PLATFORM | TENANT"
        boolean is_default
        timestamp created_at
    }
    
    permissions {
        uuid id PK
        string resource
        string action
        string description
    }
    
    role_permissions {
        uuid role_id FK
        uuid permission_id FK
    }
    
    user_roles {
        uuid id PK
        string user_id
        uuid role_id FK
        timestamp assigned_at
        string assigned_by
    }
    
    roles ||--o{ role_permissions : has
    permissions ||--o{ role_permissions : granted_to
    roles ||--o{ user_roles : assigned
```

### Frontend Route Guards

| Guard | Purpose | Redirects To |
|-------|---------|--------------|
| `authGuard` | Requires authentication | `/auth/login` |
| `guestGuard` | Only for unauthenticated users | `/app` |
| `adminGuard` | Requires `admin` OR `super-admin` | `/app/dashboard` |
| `superAdminGuard` | Requires `super-admin` only | `/app/dashboard` |
| `tenantUserGuard` | Blocks `super-admin` from tenant routes | `/app/admin/dashboard` |

### Super-Admin Specifics

**Super-admin is a special platform-level role with these characteristics:**

1. **No Tenant Context** - Uses `tenantId: "system"` instead of real tenant
2. **Wildcard Permission** - Has `*:*` which matches all permission checks
3. **Platform Management** - Can view/manage all tenants
4. **Separate UI** - Lands on Platform Dashboard, not data entries
5. **Cannot Be Deleted** - Account deletion is hidden for super-admins

**How Super-Admin Bypass Works:**
```java
// In PermissionController.checkPermission()
if ("super-admin".equals(role)) {
    return ResponseEntity.ok(true);  // Grant all permissions
}
```

### Adding New Permissions

1. **Add permission to seed data:**
```sql
INSERT INTO permissions (id, resource, action, description)
VALUES (gen_random_uuid(), 'invoice', 'create', 'Create invoices');
```

2. **Assign to roles:**
```sql
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p
WHERE r.name = 'admin' 
AND p.resource = 'invoice' AND p.action = 'create';
```

3. **Use in code:**
```java
@RequirePermission(resource = "invoice", action = "create")
public ResponseEntity<Invoice> createInvoice(...) { ... }
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/roles` | GET | List all roles (Platform + Tenant) |
| `/api/v1/roles/assign` | POST | Assign role to user |
| `/api/v1/roles/revoke` | POST | Revoke role from user |
| `/api/v1/roles/user/{userId}` | GET | Get user's roles |
| `/api/v1/permissions/check` | POST | Check if user has permission (internal) |
| `/api/v1/permissions` | GET | List all defined permissions |


---

## 🏢 Multi-Tenancy Model


### Isolation Strategy: Database-per-Tenant (Primary)

**Why Database-per-Tenant?**
- ✅ **Maximum Security** - Complete data isolation
- ✅ **Performance** - No query filtering overhead
- ✅ **Scalability** - Move tenants to different RDS instances
- ✅ **Compliance** - Easier data residency/GDPR compliance
- ✅ **Backup/Restore** - Per-tenant operations

**How It Works:**
1. Platform Service creates database: `tenant_<company_slug>`
2. Unique DB credentials stored in AWS Secrets Manager
3. Flyway runs migrations to initialize schema
4. Backend services connect to correct database using `X-Tenant-Id`

### Tenant Tiers & Limits

| Tier | Max Users | Features | Storage | Price |
|------|-----------|----------|---------|-------|
| **STANDARD** | 50 | Basic features | 10 GB | $ |
| **PREMIUM** | 200 | Advanced features + API access | 50 GB | $$ |
| **ENTERPRISE** | 10,000 | Custom + dedicated support | Unlimited | $$$ |

---

## 🗄️ Data Architecture

### Database Allocation by Service

> **CRITICAL:** All tenant-specific data MUST reside in tenant databases. Only the tenant registry exists in the shared platform database.

| Service | Platform DB (`awsinfra`) | Tenant DB (`t_<tenant_id>`) |
|---------|--------------------------|------------------------------|
| **Platform Service** | ✅ `tenant` table<br/>✅ `tenant_credentials`<br/>✅ System config | ❌ None |
| **Auth Service** | ❌ None | ✅ `roles`<br/>✅ `permissions`<br/>✅ `role_permissions`<br/>✅ `user_roles`<br/>✅ `invitations` |
| **Backend Service** | ❌ None | ✅ `entries` (your domain tables) |
| **Gateway Service** | ❌ None (stateless) | ❌ None (stateless) |

**Key Principle:** Database itself is the tenant isolation boundary. No `tenant_id` columns needed in tenant databases.

---

### Platform Database (Shared - `awsinfra`)

**Purpose:** Tenant registry and platform-level configuration ONLY  
**Owner:** Platform Service  
**Location:** Shared RDS instance

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `tenant` | Master tenant registry | id, name, status, tier, tenant_type, jdbc_url, owner_email |

**Key Fields:**
- `id` - Tenant identifier (e.g., `t_acme`, `t_john_doe_xyz`)
- `jdbc_url` - Connection string to tenant-specific database
- `db_user_secret_ref` - AWS Secrets Manager ARN for DB credentials
- `tier` - STANDARD, PREMIUM, ENTERPRISE
- `tenant_type` - PERSONAL or ORGANIZATION

> **Migration file:** `platform-service/src/main/resources/db/migration/V1__initial_schema.sql`

**That's it!** Platform database only contains the tenant registry.

---

### Tenant Databases (Isolated - `t_<tenant_id>`)

**Purpose:** ALL tenant-specific application data  
**Owners:** Auth Service, Backend Service (your domain)  
**Location:** Dedicated PostgreSQL database per tenant (`tenant_<company_slug>`)

Each tenant gets a dedicated database with the following tables:

#### Auth Service Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `roles` | Role definitions | id, name, scope (PLATFORM/TENANT) |
| `permissions` | Resource:action pairs | resource, action |
| `role_permissions` | Role-permission mappings | role_id, permission_id |
| `user_roles` | User role assignments | user_id, role_id, assigned_by |
| `invitations` | User invitations | email, token, status, expires_at |

> **Migration file:** `auth-service/src/main/resources/db/migration/tenant/V1__authorization_schema.sql`

#### Backend Service Tables (Example - Replace with Your Domain)

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `entries` | Example domain entity | title, content, created_by |

> **Migration file:** `backend-service/src/main/resources/db/migration/tenant/V1__initial_schema.sql`

**Key Design Principle:** No `tenant_id` column needed - database-per-tenant provides isolation.

---

### Database Routing Architecture

```mermaid
graph TB
    subgraph "Platform Service"
        PS[Platform Service]
        PS_DS[PlatformDataSource]
    end
    
    subgraph "Auth Service"
        AS[Auth Service]
        AS_Router[TenantDataSourceRouter]
        AS_Registry[AuthServiceTenantRegistry]
    end
    
    subgraph "Backend Service"
        BS[Backend Service]
        BS_Router[TenantDataSourceRouter]
        BS_Registry[PlatformServiceTenantRegistry]
    end
    
    subgraph "Databases"
        Platform_DB[(awsinfra DB<br/>tenant table)]
        Tenant1_DB[(t_acme DB<br/>roles, entries)]
        Tenant2_DB[(t_john_xyz DB<br/>roles, entries)]
    end
    
    PS --> PS_DS
    PS_DS --> Platform_DB
    
    AS --> AS_Router
    AS_Router --> AS_Registry
    AS_Registry -->|GET /internal/tenants/t_acme/db-info| PS
    AS_Router -->|Dynamic routing| Tenant1_DB
    AS_Router -->|Dynamic routing| Tenant2_DB
    
    BS --> BS_Router
    BS_Router --> BS_Registry
    BS_Registry -->|GET /internal/tenants/t_acme/db-info| PS
    BS_Router -->|Dynamic routing| Tenant1_DB
    BS_Router -->|Dynamic routing| Tenant2_DB
    
    style Platform_DB fill:#33dd77,stroke:#1f3a29,stroke-width:2px
    style Tenant1_DB fill:#4da3ff,stroke:#1f2a3d,stroke-width:2px
    style Tenant2_DB fill:#4da3ff,stroke:#1f2a3d,stroke-width:2px
```

**How It Works:**

1. **Request arrives** with JWT token containing `custom:tenantId`
2. **Gateway** extracts `tenantId` from token → Injects `X-Tenant-Id` header
3. **Service receives request** with `X-Tenant-Id: t_acme`
4. **TenantDataSourceRouter** checks tenant ID from `TenantContext` (ThreadLocal)
5. **TenantRegistry** fetches tenant DB config from Platform Service (cached)
6. **DataSource** dynamically routes to correct tenant database
7. **Query executes** in isolated tenant database

**Key Components:**
- `TenantDataSourceRouter` - Extends `AbstractRoutingDataSource`, routes based on `TenantContext`
- `TenantRegistry` - Fetches tenant JDBC URL + credentials from Platform Service
- `LocalCache` - Caches tenant DB configs (Caffeine, 30min TTL)
- `TenantMigrationService` - Runs Flyway migrations on tenant databases

### Migration Orchestration (Each Service Owns Its Schema)

```
┌────────────────────────────────────────────────────────────────────┐
│                    Tenant Provisioning Flow                        │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  Platform-Service (TenantProvisioningServiceImpl)                  │
│        │                                                           │
│        ├─► 1. Create tenant record in awsinfra.tenant table       │
│        │                                                           │
│        ├─► 2. StorageProvisionAction: CREATE DATABASE tenant_xxx  │
│        │                                                           │
│        └─► 3. MigrationInvokeAction: Orchestrate migrations       │
│                    │                                               │
│                    ├─► POST auth-service/internal/migrate          │
│                    │   └─► Auth runs its own Flyway (roles, etc.) │
│                    │                                               │
│                    └─► POST backend-service/internal/migrate       │
│                        └─► Backend runs its Flyway (entries)       │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

**Key Principle: Service Owns Its Schema**
- Each service has its own Flyway scripts in `src/main/resources/db/migration/tenant/`
- Platform-Service **only orchestrates** - it does NOT run other services' migrations
- This allows services to be independently versioned and deployed

**Migration Locations:**
| Service | Location | Tables |
|---------|----------|--------|
| auth-service | `migration/tenant/V1__authorization_schema.sql` | roles, permissions, user_roles |
| backend-service | `migration/tenant/V1__initial_schema.sql` | entries |

---

### Seed Data (Per Tenant)

Each tenant database is initialized with:

```sql
-- Tenant-scoped roles
INSERT INTO roles (id, name, description, scope) VALUES
('admin', 'TENANT_ADMIN', 'Full control over tenant resources', 'TENANT'),
('user', 'TENANT_USER', 'Standard user with CRUD access', 'TENANT'),
('guest', 'TENANT_GUEST', 'Read-only access', 'TENANT');

-- Standard permissions
INSERT INTO permissions (id, resource, action, description) VALUES
('entry-read', 'entry', 'read', 'View entries'),
('entry-create', 'entry', 'create', 'Create entries'),
('entry-update', 'entry', 'update', 'Update entries'),
('entry-delete', 'entry', 'delete', 'Delete entries'),
('user-invite', 'user', 'invite', 'Invite new users'),
('user-manage', 'user', 'manage', 'Full user management');

-- Role-permission mappings
-- admin gets everything
INSERT INTO role_permissions (role_id, permission_id) VALUES
('admin', 'entry-read'),
('admin', 'entry-create'),
('admin', 'entry-update'),
('admin', 'entry-delete'),
('admin', 'user-invite'),
('admin', 'user-manage');

-- user gets CRUD on entries
INSERT INTO role_permissions (role_id, permission_id) VALUES
('user', 'entry-read'),
('user', 'entry-create'),
('user', 'entry-update'),
('user', 'entry-delete');

-- guest gets read-only
INSERT INTO role_permissions (role_id, permission_id) VALUES
('guest', 'entry-read');
```


---

## 🔧 Technology Stack

### Backend Services
- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Security:** Spring Security OAuth2, AWS Cognito SDK
- **Service Discovery:** Spring Cloud Netflix Eureka
- **Database:** PostgreSQL 15+ (RDS)
- **Migrations:** Flyway
- **Build:** Maven


### Frontend (Template Included)
- **Framework:** Angular 20+ (latest)
- **Auth Integration:** AWS Amplify Auth SDK (v6)
  - Client-side login/signup with Cognito
  - **Public Client (SPA):** Uses `generate_secret = false` for secure browser auth
  - Direct username/password authentication
  - Extract custom attributes from ID token (`custom:tenantId`, `custom:tenantType`)
  - Session management with Angular Signals
- **API Client:** Angular HttpClient with functional interceptors
  - Auto-inject JWT tokens in Authorization header
  - Async token retrieval via `fetchAuthSession()`
  - Tenant context from Cognito custom attributes
- **UI Components:** PrimeNG (v20)
  - **Theme:** Aura (Premium Modern Theme)
  - **Design System:** CSS Variables, Glassmorphism, Inter Font
  - **Layout:** PrimeFlex Grid System
  - Card, Table, Dialog, Button, Input components
  - Responsive design with flex layout
  - Form validation with Reactive Forms
- **Routing:** 
  - AuthGuard for protected routes (`/app/*`)
  - GuestGuard for public routes (`/auth/*`)
  - Layout wrapper with navigation menu
- **State Management:** Angular Signals (built-in)
- **Build Tool:** Angular CLI with esbuild
- **Deployment:** 
  - Development: `npm start` (localhost:4200)
  - Production: Static build to `dist/` for AWS Amplify Hosting or S3+CloudFront

**Key Features:**
- ✅ **B2C Signup:** Personal account creation via `POST /auth/signup/personal`
- ✅ **B2B Signup:** Organization creation via `POST /auth/signup/organization`
- ✅ **Dashboard:** User info display with tenant ID and role badges
- ✅ **CRUD Interface:** PrimeNG Table for entries with pagination and lazy loading
- ✅ **Multi-Tenant UI:** Shows user's tenant context in header
- ✅ **Auto-Configuration:** Environment files updated automatically by Terraform deploy script

**Frontend Structure:**
```
frontend/
├── src/
│   ├── app/
│   │   ├── core/
│   │   │   ├── auth.service.ts          # AWS Amplify wrapper
│   │   │   ├── guards/
│   │   │   │   ├── auth.guard.ts        # Protected route guard
│   │   │   │   └── guest.guard.ts       # Public route guard
│   │   │   ├── interceptors/
│   │   │   │   └── auth.interceptor.ts  # JWT injector
│   │   │   └── services/
│   │   │       └── entry.service.ts     # API client
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   │   ├── login.component.ts
│   │   │   │   ├── signup-personal.component.ts
│   │   │   │   └── signup-organization.component.ts
│   │   │   └── dashboard/
│   │   │       └── dashboard.component.ts  # Main app view
│   │   ├── layout/
│   │   │   └── app-layout.component.ts  # Navigation wrapper
│   │   └── app.routes.ts                # Route configuration
│   └── environments/
│       ├── environment.ts               # Production config (auto-updated)
│       └── environment.development.ts   # Dev config (auto-updated)
```


### AWS Services
- **Cognito:** User authentication, MFA, SSO integration
- **RDS PostgreSQL:** Master + Tenant databases
- **Secrets Manager:** Database credentials
- **SSM Parameter Store:** Configuration
- **Application Load Balancer:** HTTPS termination, routing
- **ECS/EKS:** Container orchestration (future)

### Infrastructure as Code
- **Terraform:** AWS resource provisioning
  - VPC, subnets, security groups
  - RDS instances, parameter groups
  - Cognito user pools, app clients
  - IAM roles, policies
  - ALB, target groups

### Observability

#### Distributed Tracing Architecture

The platform uses **OpenTelemetry** with **AWS X-Ray** for distributed tracing across all microservices. This enables end-to-end request tracking, latency analysis, and service dependency visualization.

```mermaid
graph LR
    subgraph Microservices
        G[gateway-service]
        A[auth-service]
        P[platform-service]
        B[backend-service]
    end
    
    subgraph OTEL["OpenTelemetry Collector"]
        R[OTLP Receiver<br/>:4317/:4318]
        BP[Batch Processor]
        X[AWS X-Ray Exporter]
    end
    
    subgraph AWS["AWS Cloud"]
        XR[AWS X-Ray]
        SM[Service Map]
        TR[Traces]
    end
    
    G -->|OTLP/HTTP| R
    A -->|OTLP/HTTP| R
    P -->|OTLP/HTTP| R
    B -->|OTLP/HTTP| R
    
    R --> BP --> X
    X --> XR
    XR --> SM
    XR --> TR
```

#### Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Instrumentation** | Micrometer Tracing + OpenTelemetry Bridge | Auto-instrument Spring Boot requests |
| **Export Protocol** | OTLP (OpenTelemetry Protocol) over HTTP | Send spans to collector |
| **Collector** | AWS Distro for OpenTelemetry (ADOT) | Batch, process, and export traces |
| **Backend** | AWS X-Ray | Store, visualize, and analyze traces |

#### Configuration

**Service Configuration (`application.yml`):**

```yaml
management:
  tracing:
    enabled: ${TRACING_ENABLED:true}
    sampling:
      probability: ${TRACING_PROBABILITY:1.0}  # 100% sampling in dev, reduce in prod
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

**OpenTelemetry Collector (`otel-collector-config.yaml`):**

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

exporters:
  awsxray:
    region: ${AWS_REGION}

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [awsxray]
```

#### Viewing Traces in AWS X-Ray Console

1. **Navigate to X-Ray Console:**
   - URL: `https://<region>.console.aws.amazon.com/xray/home`
   - Replace `<region>` with your AWS region (e.g., `us-east-1`)

2. **Service Map:**
   - Visual representation of service dependencies
   - Shows latency, error rates, and request counts between services
   - Click on any node to drill into traces

3. **Traces View:**
   - Filter by: Service name, trace ID, time range, response code
   - View complete request path through all microservices
   - Analyze segment timelines for latency bottlenecks

4. **Analytics:**
   - Query traces using filter expressions
   - Example: `service("gateway-service") AND responseTime > 1`

#### Local Development Setup

```bash
# Start all services including OTEL Collector
docker-compose up -d

# Verify collector is healthy
docker ps | grep otel-collector
# Should show: (healthy)

# Check collector logs
docker logs otel-collector | tail -20
# Should show: "Traces" with span counts (no errors)

# Make API requests to generate traces
curl http://localhost:8080/actuator/health

# View traces in X-Ray Console (1-2 minute delay)
```

#### Required AWS Permissions

The OTEL Collector needs these IAM permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords"
      ],
      "Resource": "*"
    }
  ]
}
```

#### Sampling Configuration (Production)

For production, reduce sampling to manage costs:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of requests
```

#### Other Observability Components

- **Logging:** JSON structured logs via Logback (logstash-logback-encoder)
- **Metrics:** Prometheus endpoints exposed at `/actuator/prometheus`
- **Monitoring:** AWS CloudWatch for infrastructure metrics

---

## 🚀 Deployment Architecture

### Development Environment
```yaml
Services: Docker Compose
Database: Local PostgreSQL
Cognito: LocalStack (emulated)
Eureka: Single instance
```

### Production Environment
```yaml
Compute: AWS ECS Fargate or EKS
Load Balancer: AWS ALB with SSL/TLS
Database: RDS Multi-AZ with read replicas
Cognito: AWS Cognito
Cache: ElastiCache Redis (rate limiting, session)
Storage: S3 for static assets
CDN: CloudFront
```

---

## 🔄 How to Use This Template

### For a New Project

1. **Keep As-Is:**
   - Gateway Service
   - Auth Service  
   - Platform Service
   - Eureka Server
   - Terraform modules

2. **Replace:**
   - Backend Service → Your domain service (e.g., `work-service`)
   - Update entity models, repositories, controllers
   - Keep multi-tenant data access patterns

3. **Add New Services (Optional):**
   - `notification-service` - Email/SMS
   - `analytics-service` - Reports
   - `billing-service` - Payments
   - All should register with Eureka and respect `X-Tenant-Id`

4. **Configure:**
   - Update `application.yml` with your AWS credentials
   - Run Terraform to create infrastructure
   - Deploy services to ECS/EKS

---

## 🛠️ Deployment & Configuration Flow

### Automated Infrastructure Deployment

The project includes automated deployment scripts that handle infrastructure provisioning and application configuration in a single workflow.

#### Terraform Deployment Script

**Location:** `scripts/terraform/deploy.sh`

**What it does:**
1. **Deploys AWS Infrastructure** via Terraform:
   - Cognito User Pool with custom attributes (`tenantId`, `role`, `tenantType`)
   - User Pool Client with OAuth2 configuration
   - Lambda triggers for token customization
   - User groups (admin, admin, user)
   - SSM Parameter Store entries for all configuration

2. **Stores Configuration in SSM:**
   - `/cloud-infra/dev/cognito/user_pool_id`
   - `/cloud-infra/dev/cognito/client_id`
   - `/cloud-infra/dev/cognito/client_secret` (SecureString)
   - `/cloud-infra/dev/cognito/issuer_uri`
   - `/cloud-infra/dev/cognito/jwks_uri`
   - `/cloud-infra/dev/cognito/domain`
   - Additional metadata (branding, callbacks, etc.)

3. **Auto-Updates Frontend Environment Files:**
   - Fetches Cognito configuration from Terraform outputs
   - Writes `frontend/src/environments/environment.development.ts`
   - Writes `frontend/src/environments/environment.ts`
   - Frontend gets User Pool ID and Client ID automatically

4. **Creates Local Reference File:**
   - Generates `terraform/cognito-config.env` for manual reference
   - Contains all Cognito configuration values
   - **Note:** Do not commit this file (already in `.gitignore`)

**Usage:**
```bash
cd /path/to/AWS-Infra
./scripts/terraform/deploy.sh
```

**Prerequisites:**
- AWS CLI configured with appropriate credentials
- Terraform installed
- AWS profile set (default: `personal`)

#### Configuration Flow Diagram

```mermaid
graph LR
    A[Run deploy.sh] --> B[Terraform Apply]
    B --> C[Create Cognito Resources]
    C --> D[Store in SSM Parameter Store]
    B --> E[Extract Terraform Outputs]
    E --> F[Update Frontend Environment Files]
    E --> G[Generate cognito-config.env]
    
    D -.->|Source of Truth| H[Backend Services]
    F -.->|Auto-configured| I[Angular Frontend]
    
    style D fill:#33dd77,stroke:#1f3a29,stroke-width:2px
    style F fill:#bb66ff,stroke:#261f3a,stroke-width:2px
    style H fill:#ffb84d,stroke:#3a2d1f,stroke-width:2px
    style I fill:#bb66ff,stroke:#261f3a,stroke-width:2px
```

### Frontend Configuration

The Angular frontend requires Cognito credentials to authenticate users. These are automatically configured by the Terraform deployment script.

#### Automatic Configuration (Recommended)

When you run `./scripts/terraform/deploy.sh`, the frontend environment files are automatically updated with:
- User Pool ID
- Client ID
- AWS Region

**Generated Files:**
- `frontend/src/environments/environment.development.ts` - Used for `npm start`
- `frontend/src/environments/environment.ts` - Used for `npm run build`

**Example Generated Configuration:**
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  cognito: {
    userPoolId: 'us-east-1_jjRFRnxGA',
    userPoolWebClientId: '4apvlvee4rsmnb06ntd49ljvp5',
    region: 'us-east-1'
  }
};
```

#### Manual Configuration (Alternative)

If needed, you can fetch configuration from SSM manually:

```bash
# Get User Pool ID
aws ssm get-parameter \
  --name "/cloud-infra/dev/cognito/user_pool_id" \
  --query 'Parameter.Value' \
  --output text

# Get Client ID
aws ssm get-parameter \
  --name "/cloud-infra/dev/cognito/client_id" \
  --query 'Parameter.Value' \
  --output text
```

Then manually update `frontend/src/environments/environment.development.ts`.

### Backend Services Configuration

Backend services (Auth, Platform, Backend) read configuration from SSM Parameter Store at runtime.

**Spring Boot Integration:**
```yaml
spring:
  cloud:
    aws:
      paramstore:
        enabled: true
        prefix: /cloud-infra
        profile-separator: /
        fail-fast: true
```

**How it works:**
1. Service starts up
2. Reads `/cloud-infra/dev/cognito/*` parameters from SSM
3. Populates Spring environment properties
4. Configures OAuth2 client and resource server

**Key Benefits:**
- ✅ No hardcoded credentials in code
- ✅ Centralized configuration management
- ✅ Easy to update without redeployment
- ✅ Secure storage (SecureString for secrets)
- ✅ IAM-based access control

### Complete Deployment Workflow

```bash
# 1. Deploy AWS Infrastructure (includes frontend config)
./scripts/terraform/deploy.sh

# 2. Start Backend Services
./scripts/start-all.sh

# 3. Start Frontend (in separate terminal)
cd frontend
npm start

# 4. Access Application
# Frontend: http://localhost:4200
# Gateway: http://localhost:8080
```

**First-Time Setup:**
```bash
# Install dependencies
cd frontend && npm install

# Build all services
cd .. && mvn clean install -DskipTests

# Deploy infrastructure
./scripts/terraform/deploy.sh

# Start services
./scripts/start-all.sh

# Start frontend
cd frontend && npm start
```

---

## 🛣️ Request Flow Example

**User wants to create an Entry in their tenant**

1. **User** sends: `POST /api/entries` with JWT token
2. **Gateway** validates JWT, extracts `tenantId=acme` from token
3. **Gateway** adds headers: `X-Tenant-Id: acme`, `X-User-Id: user123`
4. **Gateway** routes to Backend Service via Eureka
5. **Backend** reads `X-Tenant-Id: acme` header
6. **Backend** connects to `tenant_acme` database
7. **Backend** creates entry, sets `created_by=user123`
8. **Backend** returns success
9. **Gateway** forwards response to user

**Security:** No tenant can access another tenant's data - enforced at database level

---

## 📁 Project Structure

```
AWS-Infra/
├── auth-service/           # Identity & permission management
├── platform-service/       # Tenant lifecycle control plane + OpenFGA store provisioning
├── backend-service/        # REPLACE THIS - domain logic mimic
├── gateway-service/        # API gateway & security enforcement
├── eureka-server/          # Service discovery
├── common-dto/             # Shared DTOs across services
├── common-infra/           # Shared multi-tenant infrastructure:
│                           #   - TenantDataSourceRouter, TenantContext
│                           #   - @RequirePermission, AuthorizationAspect
│                           #   - OpenFGA client (optional fine-grained permissions)
├── openfga/                # OpenFGA authorization model (optional add-on)
│   ├── model.fga           #   - DSL model for resource permissions
│   └── README.md           #   - Usage guide
├── terraform/              # Infrastructure as Code
│   ├── modules/
│   │   ├── vpc/
│   │   ├── rds/
│   │   ├── cognito/
│   │   ├── ecs/
│   │   └── openfga/        # OpenFGA ECS deployment (optional)
│   └── environments/
│       ├── dev/
│       └── prod/
├── docker-compose.yml      # Local dev environment (includes openfga container)
└── HLD.md                  # This document
```

---

## 🎓 Key Concepts

### Tenant Context Propagation
Every request carries tenant context through headers:
- `X-Tenant-Id` - Database to connect to
- `X-User-Id` - User making the request
- `X-Authorities` - User permissions/roles

### Fail-Closed Security
- Gateway rejects requests without valid JWT
- Gateway rejects requests without tenant context
- Services trust headers from Gateway (network isolation required)

### Dynamic Tenant Onboarding
- No code deployment needed to add new tenant
- Platform Service provisions on-demand
- Fully automated via signup API

---

## 📚 Additional Documentation

- **[Status Tracking](docs/STATUS.md)** - Current project status and roadmap

---

## ☁️ AWS Deployment Guide

### Deployment Architecture
```mermaid
graph TB
    subgraph "AWS Cloud"
        ALB[Application Load Balancer]
        
        subgraph "ECS Fargate"
            Gateway[Gateway Service]
            Auth[Auth Service]
            Platform[Platform Service]
            Backend[Your Service]
        end
        
        subgraph "Data Layer"
            RDS[(RDS PostgreSQL)]
            Secrets[Secrets Manager]
            SSM[SSM Parameters]
        end
        
        subgraph "Auth"
            Cognito[Cognito User Pool]
            Lambda[PostConfirmation Lambda]
        end
    end
    
    ALB --> Gateway
    Gateway --> Auth
    Gateway --> Platform
    Gateway --> Backend
    Auth --> Cognito
    Platform --> RDS
    Backend --> RDS
    Platform --> Secrets
```

### Infrastructure Setup (Terraform)

**Step 1: Configure remote state** (recommended for production)
```bash
cd terraform
# Edit backend.tf to use S3 backend
terraform init -migrate-state
```

**Step 2: Create infrastructure**
```bash
terraform apply -var="environment=prod"
```

This creates:
- Cognito User Pool with Lambda triggers
- SSM Parameters for service configuration
- (Optional) VPC, RDS, ECS clusters via modules

### ECS Fargate Deployment

**Service configuration:**
```yaml
# Example task definition
family: gateway-service
cpu: 256
memory: 512
containers:
  - name: gateway
    image: ${ECR_REPO}/gateway-service:latest
    portMappings:
      - containerPort: 8080
    environment:
      - name: SPRING_PROFILES_ACTIVE
        value: prod
```

**Recommended instance sizing:**
| Service | vCPU | Memory | Min Instances |
|---------|------|--------|---------------|
| Gateway | 0.25 | 512MB | 2 |
| Auth | 0.25 | 512MB | 2 |
| Platform | 0.25 | 512MB | 1 |
| Backend | 0.25 | 512MB | 2 |

### Cost Estimation (Monthly)

> [!NOTE]
> Estimates based on us-east-1 pricing. Actual costs may vary.

| Resource | Free Tier | After Free Tier |
|----------|-----------|-----------------|
| **Cognito** | 50,000 MAU | $0.0055/MAU after |
| **Fargate** | - | ~$50/month (4 services, min instances) |
| **RDS PostgreSQL** | 750 hrs/month (t3.micro) | ~$15-30/month (t3.small) |
| **ALB** | - | ~$16/month + data |
| **Secrets Manager** | $0.40/secret/month | $0.40/secret |
| **Lambda** | 1M requests/month | $0.20/1M after |

**Estimated total:** $80-150/month for small production setup

### Environment Variables (Production)

```bash
# Required for each service
SPRING_PROFILES_ACTIVE=prod
AWS_REGION=us-east-1

# Cognito (from SSM)
COGNITO_USER_POOL_ID=us-east-1_xxxxx
COGNITO_CLIENT_ID=xxxxx
COGNITO_CLIENT_SECRET=xxxxx

# Database (from Secrets Manager)
SPRING_DATASOURCE_URL=jdbc:postgresql://rds-endpoint:5432/dbname
SPRING_DATASOURCE_USERNAME=xxx
SPRING_DATASOURCE_PASSWORD=xxx
```

---

## 🏭 Production Readiness Features

This section outlines features for enterprise-grade deployments.

### Security & Compliance
| Feature | Description | Status |
|---------|-------------|--------|
| **Tenant Rate Limiting** | Per-tenant request limits based on SLA tier | 🔜 Planned |
| **Audit Logging** | GDPR/HIPAA/SOC2 compliant access logs | 🔜 Planned |
| **Data Export API** | GDPR Right to Data Portability | 🔜 Planned |
| **Soft Delete** | 30-day grace period before permanent deletion | ✅ Implemented |

### Performance Optimization
| Feature | Description | Status |
|---------|-------------|--------|
| **Per-Tenant Connection Pools** | Tier-based pool sizing (FREE: 2-5, ENTERPRISE: 10-20) | 🔜 Planned |
| **DataSource Caching** | Caffeine cache with 1-hour TTL, max 100 tenants | 🔜 Planned |
| **Circuit Breaker** | Resilience4j for platform-service calls | 🔜 Planned |

### Observability
| Feature | Description | Status |
|---------|-------------|--------|
| **Tenant Metrics** | Per-tenant latency, throughput, error rates | 🔜 Planned |
| **Usage Tracking** | API calls, storage, compute hours per tenant | 🔜 Planned |
| **Health Checks** | Active tenant count, DB connectivity checks | 🔜 Planned |

### Lifecycle Management
| Feature | Description | Status |
|---------|-------------|--------|
| **Tenant Archival** | Auto-archive inactive tenants to S3 after 90 days | 🔜 Planned |

---

**Questions?** This template is designed to be self-explanatory. Start with Gateway → Auth → Platform → Your Service.

See **[ROADMAP.md](docs/ROADMAP.md)** for future phases and feature plans.

