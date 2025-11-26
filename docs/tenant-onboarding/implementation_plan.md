# Tenant Onboarding Implementation Guide - Unified B2B & B2C

**Status:** 🔄 Incremental Enhancement (Building on Existing Foundation)

---

## Current State (From Gap Analysis)

### ✅ What Already Exists:

**Platform-Service:**
- ✅ Complete tenant provisioning framework (`TenantProvisioningServiceImpl`)
- ✅ Action-based pipeline architecture
- ✅ `StorageProvisionAction` - creates DB, user, encrypts password
- ✅ `MigrationInvokeAction` - calls backend-service migrations
- ✅ Status transitions (PROVISIONING → MIGRATING → ACTIVE)
- ✅ Retry mechanism for failed migrations
- ✅ Metrics instrumentation

**Auth-Service:**
- ✅ Cognito integration (login, signup, logout)
- ✅ User creation with AdminCreateUser
- ✅ Password management

**Database:**
- ✅ Flyway migrations
- ✅ Basic tenant table schema

### 🔧 What Needs Enhancement:

**Platform-Service:**
- 📝 Extend Tenant entity (+18 fields for B2B/B2C/SSO)
- 📝 Update V1 migration (+audit log, usage metrics tables)
- 📝 Update ProvisionTenantRequest DTO (+3 fields)
- 📝 Extend MigrationInvokeAction (add auth-service)

**Backend-Service:**
- 📝 **CREATE** TenantMigrationController
- 📝 **CREATE** Tenant routing infrastructure

**Auth-Service:**
- 📝 **CREATE** SignupController (B2C/B2B orchestration)

**Production Features:**
- 📝 Rate limiting, audit logging, metrics (see PRODUCTION_READINESS.md)

---

## Architecture: Unified Backend Process

**Key Insight:** Both B2B and B2C use the **same tenant creation API**, just with different parameters.

```
UI Signup Request → Backend Validation → Tenant Creation API → User Creation → Done
```

---

## Authentication & SSO Strategy

### Multi-Provider Support

**B2C (Personal Accounts):**
- ✅ Email/Password (Cognito native)
- ✅ Social logins via Cognito:
  - Google
  - Facebook
  - Apple
  - Amazon
- ✅ Passwordless (magic links, OTP)

**B2B (Organization Accounts):**
- ✅ Email/Password (default)
- ✅ **SSO/SAML** - Enterprise IdP integration:
  - Azure Active Directory
  - Okta
  - Ping Identity
  - OneLogin
  - Google Workspace
- ✅ **OIDC** - OpenID Connect providers
- ✅ Cognito Identity Pool Federation

### Database Design for SSO

The tenant schema supports federation:

```sql
-- SSO Configuration per tenant
sso_enabled BOOLEAN DEFAULT FALSE,
idp_type VARCHAR(64), -- COGNITO_DEFAULT, SAML, OIDC, GOOGLE, AZURE_AD
idp_metadata_url TEXT,
idp_entity_id VARCHAR(512),
idp_config_json JSONB -- Flexible for provider-specific settings
```

**Example B2B SSO config:**
```json
{
  "provider": "AZURE_AD",
  "tenant_id": "acme-corp-azure",
  "client_id": "xxxxx",
  "metadata_url": "https://login.microsoftonline.com/.../FederationMetadata.xml",
  "auto_provision_users": true,
  "default_role": "user"
}
```

### Implementation Approach: Backend-First

**Phase 1: Strong API Foundation** (Current Focus)
1. Build tenant provisioning API
2. Implement signup endpoints (B2C + B2B)
3. Create tenant management APIs
4. SSO configuration endpoints
5. Comprehensive API testing

**Phase 2: Frontend with AWS Amplify** (Later)
1. Use AWS Amplify for pre-built auth UI
2. Cognito Hosted UI for quick start
3. Custom UI with Amplify Auth components
4. SSO redirect flows
5. Social login buttons

**Why Backend-First?**
- ✅ Testable via API (Postman, curl)
- ✅ Platform-agnostic (works with any frontend)
- ✅ Easier to debug and iterate
- ✅ Can add multiple frontends later (web, mobile, etc.)

---

## Flow Comparison

| Step | B2B | B2C |
|------|-----|-----|
| **1. Request** | Admin submits org request | User submits personal signup |
| **2. Approval** | Optional (can be manual) | Auto-approved |
| **3. Tenant Creation** | `POST /api/tenants` (type=ORG) | `POST /api/tenants` (type=PERSONAL) |
| **4. User Creation** | Admin creates users | Auto-create single user |
| **5. Notification** | Email to org admin | Email to user |

**Common:** Both call the same Platform Service API!

---

## Implementation Steps

### Phase 1: Backend Foundation (Platform Service)

**Note:** Platform-service already has excellent provisioning framework. We're **extending** it, not rebuilding.

#### Step 1.1: 📝 UPDATE Tenant Entity

**Existing File:** `platform-service/src/main/java/com/learning/platformservice/tenant/entity/Tenant.java`

**Current State:** 11 fields (id, name, status, storageMode, jdbcUrl, etc.)

**Add these fields:**
```java
@Column(name = "tenant_type", nullable = false)
private String tenantType; // "PERSONAL" or "ORGANIZATION"

@Column(name = "owner_email")
private String ownerEmail; // For B2C - the user's email

@Column(name = "max_users", nullable = false)
private Integer maxUsers = 1; // B2C=1, B2B=unlimited or tier-based

// SSO/IDP Support (for B2B)
@Column(name = "sso_enabled")
private Boolean ssoEnabled = false; // Enable SSO for organization

@Column(name = "idp_type")
private String idpType; // "COGNITO_DEFAULT", "SAML", "OIDC", "GOOGLE", "AZURE_AD"

@Column(name = "idp_metadata_url")
private String idpMetadataUrl; // SAML metadata URL or OIDC discovery endpoint

@Column(name = "idp_entity_id")
private String idpEntityId; // SAML Entity ID or OIDC Client ID
```

#### Step 1.2: Update V1 Flyway Migration (Not V2!)

**File:** `platform-service/src/main/resources/db/migration/V1__init_platform_service.sql`

**Update to include all fields from the start:**
```sql
-- V1: Initial schema for platform-service with multi-tenant and SSO support
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    
    -- Storage configuration
    storage_mode VARCHAR(32) NOT NULL,
    jdbc_url TEXT,
    db_user_secret_ref TEXT,
    db_user_password_enc TEXT,
    
    -- Tenant type and limits
    tenant_type VARCHAR(32) NOT NULL DEFAULT 'PERSONAL', -- PERSONAL or ORGANIZATION
    owner_email VARCHAR(255),
    max_users INTEGER NOT NULL DEFAULT 1,
    
    -- SLA and versioning
    sla_tier VARCHAR(32) NOT NULL,
    last_migration_version VARCHAR(64),
    
    -- SSO/IDP configuration (for B2B)
    sso_enabled BOOLEAN DEFAULT FALSE,
    idp_type VARCHAR(64), -- COGNITO_DEFAULT, SAML, OIDC, GOOGLE, AZURE_AD, PING
    idp_metadata_url TEXT,
    idp_entity_id VARCHAR(512),
    idp_config_json JSONB, -- Flexible config for provider-specific settings
    
    -- Security & Compliance
    encryption_key_id VARCHAR(255), -- KMS key for tenant-specific encryption
    data_residency VARCHAR(64), -- US, EU, APAC, etc. for compliance
    
    -- Scalability & Performance
    db_shard VARCHAR(64) DEFAULT 'shard-1', -- Database shard identifier
    read_replica_url TEXT, -- Read-only replica for large tenants
    connection_pool_min INTEGER DEFAULT 2,
    connection_pool_max INTEGER DEFAULT 10,
    
    -- Lifecycle Management
    trial_ends_at TIMESTAMPTZ,
    subscription_status VARCHAR(32), -- TRIAL, ACTIVE, SUSPENDED, CANCELLED
    archived_at TIMESTAMPTZ,
    archived_to_s3 BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_type ON tenant(tenant_type);
CREATE INDEX idx_tenant_owner ON tenant(owner_email);
CREATE INDEX idx_tenant_status ON tenant(status);
CREATE INDEX idx_tenant_sso ON tenant(sso_enabled) WHERE sso_enabled = TRUE;
CREATE INDEX idx_tenant_shard ON tenant(db_shard);
CREATE INDEX idx_tenant_subscription ON tenant(subscription_status);

-- Audit log for compliance
CREATE TABLE IF NOT EXISTS tenant_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(255),
    action VARCHAR(64) NOT NULL, -- CREATE, UPDATE, DELETE, LOGIN, EXPORT, etc.
    resource_type VARCHAR(64),
    resource_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(64),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant ON tenant_audit_log(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_action ON tenant_audit_log(action);
CREATE INDEX idx_audit_user ON tenant_audit_log(user_id);

-- Usage metrics for cost allocation
CREATE TABLE IF NOT EXISTS tenant_usage_metrics (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    metric_date DATE NOT NULL,
    api_calls INTEGER DEFAULT 0,
    storage_mb INTEGER DEFAULT 0,
    data_transfer_mb INTEGER DEFAULT 0,
    compute_hours DECIMAL(10,2) DEFAULT 0,
    UNIQUE(tenant_id, metric_date)
);

CREATE INDEX idx_usage_tenant_date ON tenant_usage_metrics(tenant_id, metric_date DESC);
```

**Why V1 instead of V2?**
- Project in early stage - no production data
- Cleaner to have complete schema from start
- Avoids migration complexity
- Future-proof for SSO/IDP requirements

---

## Tenant Database Migration Architecture

### Distributed Migration Pattern (Service Endpoints)

**Each service owns its tenant schema and exposes a migration endpoint.**

```
Platform-Service           Backend-Service         Auth-Service
     |                           |                       |
     |--CREATE DATABASE--------->|                       |
     |                           |                       |
     |--POST /internal/tenant/   |                       |
     |   {tenantId}/migrate----->|                       |
     |                           |--Run Flyway---------->|
     |                           |   (tenant-template)   |
     |<---------200 OK-----------|                       |
     |                           |                       |
     |--POST /internal/tenant/                           |
     |   {tenantId}/migrate------------------------------>|
     |                           |                       |--Run Flyway
     |                           |                       |  (tenant-template)
     |<---------200 OK------------------------------------|
```

### Service Structure

Each service maintains:
```
<service>/
├── db/migration/          # Service's own database migrations
│   └── V1__*.sql
└── db/tenant-template/    # Tenant database migrations ⭐
    └── V1__tenant_initial_schema.sql
```

**Example:**
```
backend-service/
├── db/migration/
│   ├── V1__create_tenant_registry.sql  # Backend's own DB
│   └── V2__create_entries_table.sql
└── db/tenant-template/                  # For tenant DBs
    └── V1__tenant_initial_schema.sql   # Creates entries table
```

---

#### Step 1.3: Add Tenant Migration Endpoint to Each Service

**Purpose:** Each service runs its own Flyway migrations on tenant databases

##### Backend-Service: Tenant Migration Controller

**File:** `backend-service/src/main/java/com/learning/backendservice/tenant/TenantMigrationController.java`

```java
@RestController
@RequestMapping("/internal/tenant")
@Slf4j
public class TenantMigrationController {
    
    @PostMapping("/{tenantId}/migrate")
    public ResponseEntity<MigrationResult> runMigrations(
        @PathVariable String tenantId,
        @RequestBody TenantDbConfig dbConfig
    ) {
        log.info("Running migrations for tenant: {}", tenantId);
        
        try {
            // Create DataSource for tenant database
            HikariDataSource dataSource = HikariDataSource.builder()
                .jdbcUrl(dbConfig.getJdbcUrl())
                .username(dbConfig.getUsername())
                .password(dbConfig.getPassword())
                .build();
            
            // Run Flyway on tenant-template scripts
            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/tenant-template")
                .table(tenantId + "_schema_history") // Separate history per tenant
                .baselineOnMigrate(true)
                .load();
            
            MigrateResult result = flyway.migrate();
            
            log.info("Migrations completed for tenant {}: {} migrations applied", 
                tenantId, result.migrationsExecuted);
            
            return ResponseEntity.ok(new MigrationResult(
                true,
                result.migrationsExecuted,
                result.targetSchemaVersion
            ));
            
        } catch (Exception e) {
            log.error("Migration failed for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(500)
                .body(new MigrationResult(false, 0, null));
        }
    }
}
```

**DTO:**
```java
public record TenantDbConfig(
    String jdbcUrl,
    String username,
    String password
) {}

public record MigrationResult(
    boolean success,
    int migrationsExecuted,
    String schemaVersion
) {}
```

##### Apply to All Services

**Services that need tenant migrations:**
- ✅ `backend-service` - entries table
- ✅ `auth-service` - user sessions, tokens (if needed)
- ✅ Any future services with tenant-specific data

**Services that DON'T need tenant migrations:**
- ❌ `platform-service` - only manages tenant metadata
- ❌ `eureka-server` - service discovery
- ❌ `gateway-service` - routing only

---

#### Step 1.4: 📝 UPDATE Platform-Service Orchestration

**Existing File:** `platform-service/src/main/java/com/learning/platformservice/tenant/action/MigrationInvokeAction.java`

**Current State:** ✅ Already calls backend-service for migrations

**What It Does Now:**
```java
@Component
@Order(50)
public class MigrationInvokeAction implements TenantProvisionAction {
    private final WebClient backendWebClient;
    
    public void execute(TenantProvisionContext context) {
        // Currently only calls backend-service
        String lastVersion = backendWebClient.post()
            .uri("/internal/tenants/{tenantId}/migrate", tenantId)
            .retrieve()
            .bodyToMono(MigrationResult.class)
            .timeout(Duration.ofSeconds(30))
            .block();
    }
}
```

**Enhancement Needed:** Add auth-service migration call

**Updated Implementation:**

```java
@Component
@Slf4j
public class MigrationInvokeAction implements TenantProvisionAction {
    
    private final RestTemplate restTemplate;
    
    // Service URLs that need tenant migrations
    private static final List<String> TENANT_SERVICES = List.of(
        "http://backend-service:8082",
        "http://auth-service:8081"
        // Add more services as needed
    );
    
    @Override
    public void execute(Tenant tenant) {
        log.info("Running migrations for tenant: {}", tenant.getId());
        
        // Build tenant DB config
        TenantDbConfig dbConfig = new TenantDbConfig(
            tenant.getJdbcUrl(),
            decryptUsername(tenant.getDbUserSecretRef()),
            decryptPassword(tenant.getDbUserPasswordEnc())
        );
        
        // Call each service to run migrations
        for (String serviceUrl : TENANT_SERVICES) {
            try {
                String endpoint = serviceUrl + "/internal/tenant/" + tenant.getId() + "/migrate";
                
                ResponseEntity<MigrationResult> response = restTemplate.postForEntity(
                    endpoint,
                    dbConfig,
                    MigrationResult.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Migrations succeeded for service: {}", serviceUrl);
                } else {
                    throw new TenantProvisionException(
                        "Migration failed for service: " + serviceUrl
                    );
                }
                
            } catch (Exception e) {
                log.error("❌ Migration failed for service {}: {}", serviceUrl, e.getMessage());
                throw new TenantProvisionException("Migration failed", e);
            }
        }
        
        log.info("All migrations completed for tenant: {}", tenant.getId());
    }
}
```

---

### Complete Tenant Provisioning Flow

```
1. POST /platform/api/tenants { id: "acme-corp", ... }
   ↓
2. Platform DB: INSERT INTO tenant (status='PROVISIONING')
   ↓
3. DatabaseCreationAction: CREATE DATABASE db_acme_corp
   ↓
4. MigrationInvokeAction:
   ├─> POST http://backend-service:8082/internal/tenant/acme-corp/migrate
   │   └─> Backend runs: db/tenant-template/V1__tenant_initial_schema.sql
   │       └─> Creates entries table in db_acme_corp
   │   
   └─> POST http://auth-service:8081/internal/tenant/acme-corp/migrate
       └─> Auth runs: db/tenant-template/V1__tenant_auth_schema.sql
           └─> Creates auth tables in db_acme_corp
   ↓
5. Platform DB: UPDATE tenant SET status='ACTIVE'
   ↓
6. Return 201 Created
```

---

#### Step 1.5: Update Tenant DTO

**File:** `platform-service/src/main/java/com/learning/platformservice/tenant/dto/ProvisionTenantRequest.java`

```java
public record ProvisionTenantRequest(
    String id,
    String name,
    String storageMode,
    String slaTier,
    String tenantType,    // NEW: "PERSONAL" or "ORGANIZATION"
    String ownerEmail,    // NEW: For B2C
    Integer maxUsers      // NEW: Default based on type
) {}
```

#### Step 1.4: Tenant Creation API (Already Exists!)

**Endpoint:** `POST /platform/api/tenants`

**No changes needed** - just call with different parameters!

---

### Phase 2: Signup API (Backend Service)

#### Step 2.1: Create Signup Controller

**File:** `backend-service/src/main/java/com/learning/backendservice/api/SignupController.java`

**Purpose:** Handle signup requests from UI

**Endpoints:**

```java
@RestController
@RequestMapping("/api/signup")
public class SignupController {
    
    // B2C Personal Signup
    @PostMapping("/personal")
    public ResponseEntity<SignupResponse> signupPersonal(@RequestBody PersonalSignupRequest request) {
        // 1. Validate request
        // 2. Generate unique tenantId
        // 3. Call platform-service to create tenant
        // 4. Create Cognito user with tenantId
        // 5. Send verification email
        // 6. Return success
    }
    
    // B2B Organization Request
    @PostMapping("/organization")
    public ResponseEntity<SignupResponse> requestOrganization(@RequestBody OrgSignupRequest request) {
        // 1. Validate request
        // 2. Create pending request (if approval needed)
        // 3. OR directly create tenant (if auto-approved)
        // 4. Notify admin
        // 5. Return status
    }
}
```

---

### Phase 3: B2C Personal Signup Flow

#### UI Form

```html
<form action="/api/signup/personal" method="POST">
  <input type="email" name="email" required>
  <input type="password" name="password" required>
  <input type="text" name="displayName">
  <button>Create Personal Account</button>
</form>
```

#### Backend Process

**Step 3.1: Receive Request**
```json
POST /api/signup/personal
{
  "email": "john@gmail.com",
  "password": "SecurePass123!",
  "displayName": "John Doe"
}
```

**Step 3.2: Generate Tenant ID**
```java
String tenantId = "user_" + System.currentTimeMillis() + "_" + randomString(6);
// Result: "user_1732523652_a8f3k9"
```

**Step 3.3: Create Tenant (Platform Service)**
```java
POST http://platform-service:8083/platform/api/tenants
{
  "id": "user_1732523652_a8f3k9",
  "name": "John Doe's Workspace",
  "storageMode": "DATABASE",
  "slaTier": "FREE",
  "tenantType": "PERSONAL",
  "ownerEmail": "john@gmail.com",
  "maxUsers": 1
}
```

**Platform-service automatically:**
- Creates tenant row
- Creates database `db_user_1732523652_a8f3k9`
- Runs Flyway migrations
- Returns 201 Created

**Step 3.4: Create Cognito User**
```java
adminCreateUser(
  username: "john@gmail.com",
  tempPassword: <generated>,
  attributes: {
    email: "john@gmail.com",
    custom:tenantId: "user_1732523652_a8f3k9",
    custom:tenantType: "PERSONAL",
    custom:role: "owner"
  }
)
```

**Step 3.5: Send Verification Email**
```
Subject: Verify your account

Welcome John!

Click here to verify: https://app.com/verify?token=...
```

**Step 3.6: User Verifies → Login → Access Personal Tenant**

---

### Phase 4: B2B Organization Signup Flow

#### UI Form

```html
<form action="/api/signup/organization" method="POST">
  <input type="text" name="companyName" required>
  <input type="email" name="adminEmail" required>
  <input type="tel" name="phone">
  <select name="plan">
    <option value="STANDARD">Standard (50 users)</option>
    <option value="ENTERPRISE">Enterprise (Unlimited)</option>
  </select>
  <button>Request Organization Account</button>
</form>
```

#### Backend Process

**Step 4.1: Receive Request**
```json
POST /api/signup/organization
{
  "companyName": "Acme Corporation",
  "adminEmail": "admin@acme.com",
  "phone": "+1234567890",
  "plan": "ENTERPRISE"
}
```

**Step 4.2: Create Pending Request (Optional)**

If manual approval needed:
```java
// Save to pending_signups table
INSERT INTO pending_signups (company, email, plan, status, created_at)
VALUES ('Acme Corporation', 'admin@acme.com', 'ENTERPRISE', 'PENDING', NOW());

// Notify super admin
sendEmail(superAdmin, "New org signup request: Acme Corporation");
```

**OR Auto-Approve:**

**Step 4.3: Generate Tenant ID**
```java
String tenantId = companyName.toLowerCase()
    .replaceAll("[^a-z0-9]", "-")
    .replaceAll("-+", "-");
// Result: "acme-corporation"
```

**Step 4.4: Create Tenant (Platform Service)**
```java
POST http://platform-service:8083/platform/api/tenants
{
  "id": "acme-corporation",
  "name": "Acme Corporation",
  "storageMode": "DATABASE",
  "slaTier": "ENTERPRISE",
  "tenantType": "ORGANIZATION",
  "ownerEmail": "admin@acme.com",
  "maxUsers": 999
}
```

**Platform-service automatically:**
- Creates tenant row
- Creates database `db_acme-corporation`
- Runs Flyway migrations
- Returns 201 Created

**Step 4.5: Create Admin User**
```java
adminCreateUser(
  username: "admin@acme.com",
  tempPassword: <generated>,
  attributes: {
    email: "admin@acme.com",
    custom:tenantId: "acme-corporation",
    custom:tenantType: "ORGANIZATION",
    custom:role: "tenant-admin"
  }
)
```

**Step 4.6: Send Welcome Email**
```
Subject: Welcome to Platform - Your Organization is Ready!

Welcome to Acme Corporation!

Your organization account has been created.

Login URL: https://app.com/login
Username: admin@acme.com
Temporary Password: <password>

You will be prompted to change your password on first login.
```

---

## Common Component: Tenant Creation Script

### Unified Script: `create-tenant.sh`

**File:** `scripts/tenant/create-tenant.sh`

```bash
#!/bin/bash

set -euo pipefail

# Usage: ./create-tenant.sh <type> <tenant-id> <name> <owner-email> <sla-tier>
# Example (B2C): ./create-tenant.sh PERSONAL user_123_x7k9 "John's Workspace" john@gmail.com FREE
# Example (B2B): ./create-tenant.sh ORGANIZATION acme-corp "Acme Corp" admin@acme.com ENTERPRISE

TENANT_TYPE=$1
TENANT_ID=$2
TENANT_NAME=$3
OWNER_EMAIL=$4
SLA_TIER=$5

MAX_USERS=1
if [ "$TENANT_TYPE" = "ORGANIZATION" ]; then
  MAX_USERS=999
fi

# Create tenant via API
curl -X POST http://localhost:8083/platform/api/tenants \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"$TENANT_ID\",
    \"name\": \"$TENANT_NAME\",
    \"storageMode\": \"DATABASE\",
    \"slaTier\": \"$SLA_TIER\",
    \"tenantType\": \"$TENANT_TYPE\",
    \"ownerEmail\": \"$OWNER_EMAIL\",
    \"maxUsers\": $MAX_USERS
  }"

echo "✅ Tenant created: $TENANT_ID"
```

**Both flows use this same script!**

---

## Runtime: Tenant Isolation Request Flow

### How Requests Route to Correct Tenant Database

After tenant provisioning, **every API request must route to the correct tenant's database** using the tenantId from JWT.

### Request Flow Architecture

```
Frontend                Gateway Service         Backend Service         Tenant DB
   |                          |                       |                     |
   |--GET /api/entries------->|                       |                     |
   |  Authorization: JWT      |                       |                     |
   |  (tenantId="acme-corp")  |                       |                     |
   |                          |                       |                     |
   |                          |--Extract tenantId     |                     |
   |                          |   from JWT            |                     |
   |                          |                       |                     |
   |                          |--GET /entries-------->|                     |
   |                          |  X-Tenant-Id:         |                     |
   |                          |  acme-corp            |                     |
   |                          |                       |                     |
   |                          |                       |--Get DataSource     |
   |                          |                       |  for acme-corp      |
   |                          |                       |                     |
   |                          |                       |--SELECT * FROM----->|
   |                          |                       |  db_acme_corp       |
   |                          |                       |  .entries           |
   |                          |                       |<---------Results----|
   |                          |<--------Response------|                     |
   |<----------Response-------|                       |                     |
```

### Implementation Components

#### 1. Gateway Service: Tenant Filter

**File:** `gateway-service/src/main/java/com/learning/gatewayservice/filter/TenantContextFilter.java`

```java
@Component
public class TenantContextFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract JWT token from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            // Decode JWT and extract tenantId
            String tenantId = extractTenantId(token);
            
            // Add tenantId as header for downstream services
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-Tenant-Id", tenantId)
                .build();
            
            ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();
            
            return chain.filter(modifiedExchange);
        }
        
        return chain.filter(exchange);
    }
    
    private String extractTenantId(String token) {
        // Decode JWT and get tenantId claim
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getClaim("custom:tenantId").asString();
    }
    
    @Override
    public int getOrder() {
        return -100; // Execute early in filter chain
    }
}
```

#### 2. Backend Service: Tenant Context

**File:** `backend-service/src/main/java/com/learning/backendservice/tenant/TenantContext.java`

```java
public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }
    
    public static String getTenantId() {
        return currentTenant.get();
    }
    
    public static void clear() {
        currentTenant.remove();
    }
}
```

**File:** `backend-service/src/main/java/com/learning/backendservice/filter/TenantInterceptor.java`

```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        // Extract tenantId from header (set by Gateway)
        String tenantId = request.getHeader("X-Tenant-Id");
        
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        } else {
            // Fallback to default tenant for non-authenticated requests
            TenantContext.setTenantId("default");
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                                HttpServletResponse response, 
                                Object handler, 
                                Exception ex) {
        TenantContext.clear();
    }
}
```

#### 3. Backend Service: Dynamic DataSource Routing

**File:** `backend-service/src/main/java/com/learning/backendservice/config/TenantDataSourceRouter.java`

```java
public class TenantDataSourceRouter extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }
}
```

**File:** `backend-service/src/main/java/com/learning/backendservice/config/DataSourceConfig.java`

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource dataSource() {
        TenantDataSourceRouter router = new TenantDataSourceRouter();
        
        // Map of tenantId -> DataSource
        Map<Object, Object> dataSources = new HashMap<>();
        
        // Default tenant dataSource
        dataSources.put("default", createDataSource("db_default"));
        
        // Tenant dataSources loaded dynamically or from platform-service
        // Will be populated at runtime when tenants are accessed
        
        router.setTargetDataSources(dataSources);
        router.setDefaultTargetDataSource(dataSources.get("default"));
        
        return router;
    }
    
    private DataSource createDataSource(String database) {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://localhost:5432/" + database)
            .username("postgres")
            .password("postgres")
            .build();
    }
}
```

#### 4. Lazy DataSource Loading

**File:** `backend-service/src/main/java/com/learning/backendservice/tenant/TenantDataSourceService.java`

```java
@Service
public class TenantDataSourceService {
    
    private final RestTemplate restTemplate;
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();
    
    public DataSource getDataSource(String tenantId) {
        return dataSourceCache.computeIfAbsent(tenantId, this::createDataSource);
    }
    
    private DataSource createDataSource(String tenantId) {
        // Call platform-service to get tenant DB config
        TenantDbInfo dbInfo = restTemplate.getForObject(
            "http://platform-service:8083/internal/tenants/" + tenantId + "/db-info",
            TenantDbInfo.class
        );
        
        return DataSourceBuilder.create()
            .url(dbInfo.getJdbcUrl())
            .username(dbInfo.getUsername())
            .password(dbInfo.getPassword())
            .build();
    }
}
```

### Complete Request Flow Example

```
1. User logs in
   ↓
2. Cognito returns JWT: { "custom:tenantId": "acme-corp" }
   ↓
3. Frontend: GET /api/entries
   Authorization: Bearer <JWT>
   ↓
4. Gateway extracts tenantId from JWT
   ↓
5. Gateway forwards: GET /entries
   X-Tenant-Id: acme-corp
   ↓
6. Backend TenantInterceptor sets TenantContext("acme-corp")
   ↓
7. Backend TenantDataSourceRouter routes to db_acme_corp
   ↓
8. JPA/Hibernate queries db_acme_corp.entries
   ↓
9. Response returned with only acme-corp data
```

### Security Benefits

✅ **Automatic Isolation** - No tenant data leakage  
✅ **No Manual Filtering** - No `WHERE tenant_id = ?` in queries  
✅ **Database-Level Security** - Physical separation  
✅ **Performance** - Each tenant has dedicated resources  
✅ **Simple Queries** - No tenant_id column needed  

---

## Testing Plan

### Test B2C Flow

```bash
# 1. Signup
curl -X POST http://localhost:8082/backend/api/signup/personal \
  -d '{"email":"test@gmail.com","password":"Pass123!","displayName":"Test User"}'

# 2. Verify tenant created
curl http://localhost:8083/platform/api/tenants/user_<id> | jq

# 3. Verify database
psql -h localhost -U postgres -l | grep db_user_

# 4. Login (get JWT)
# JWT should contain: "tenantId": "user_<id>", "tenantType": "PERSONAL"
```

### Test B2B Flow

```bash
# 1. Organization signup request
curl -X POST http://localhost:8082/backend/api/signup/organization \
  -d '{"companyName":"Test Corp","adminEmail":"admin@test.com","plan":"STANDARD"}'

# 2. Verify tenant created
curl http://localhost:8083/platform/api/tenants/test-corp | jq

# 3. Verify database
psql -h localhost -U postgres -l | grep db_test-corp

# 4. Login (get JWT)
# JWT should contain: "tenantId": "test-corp", "tenantType": "ORGANIZATION"
```

---

## Implementation Checklist

### Backend
- [ ] Update Tenant entity with new fields
- [ ] Create Flyway migration V2
- [ ] Update ProvisionTenantRequest DTO
- [ ] Create SignupController in backend-service
- [ ] Implement B2C signup endpoint
- [ ] Implement B2B signup endpoint
- [ ] Add tenant ID generation logic
- [ ] Add Cognito user creation
- [ ] Add email notification service

### Frontend
- [ ] Create B2C signup form
- [ ] Create B2B signup form
- [ ] Add form validation
- [ ] Handle success/error responses
- [ ] Add email verification flow
- [ ] Add password change flow

### Infrastructure
- [ ] Create unified tenant creation script
- [ ] Update platform-service configuration
- [ ] Test database creation
- [ ] Test Flyway migrations
- [ ] Set up email service (AWS SES or similar)

### Testing
- [ ] Unit tests for signup logic
- [ ] Integration tests for tenant creation
- [ ] E2E test for B2C flow
- [ ] E2E test for B2B flow
- [ ] Load test for concurrent signups

---

## Approval Workflow (Optional for B2B)

If you want manual approval for B2B:

### Database Table

```sql
CREATE TABLE signup_requests (
    id SERIAL PRIMARY KEY,
    company_name VARCHAR(255),
    admin_email VARCHAR(255),
    plan VARCHAR(32),
    status VARCHAR(32), -- 'PENDING', 'APPROVED', 'REJECTED'
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    processed_by VARCHAR(255)
);
```

### Admin Approval Endpoint

```java
@PostMapping("/admin/signup-requests/{id}/approve")
public ResponseEntity<?> approveSignup(@PathVariable Long id) {
    // 1. Get request
    // 2. Create tenant (call unified API)
    // 3. Create user
    // 4. Send email
    // 5. Update request status
}
```

---

## Security Considerations

### B2C
- Rate limit signup endpoint (prevent spam)
- Email verification required
- Password strength validation
- CAPTCHA on signup form

### B2B
- Email domain validation (corporate emails only?)
- Manual approval for high-tier plans
- Credit card verification for paid plans
- Admin notification for large requests

---

## Summary

**Key Decision:** Both B2C and B2B use the **same backend tenant creation API**, just different parameters!

```
UI Form → Signup Controller → Platform Service API → Tenant Created
```

**Implementation:** 
1. Add tenant type fields to database
2. Create signup endpoints (B2C + B2B)
3. Both call same Platform Service API
4. Create Cognito users with proper tenantId
5. Send notifications

**No Lambda needed!** Everything controlled through your backend services.

---

## Production Readiness

For production-grade features including security, compliance, performance optimizations, and scalability enhancements, see:

**📄 [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md)**

This covers:
- 🔐 Security: Rate limiting, audit logging, tenant isolation
- ✅ Compliance: GDPR data export, deletion, audit trails
- ⚡ Performance: Connection pools, caching, circuit breakers
- 📊 Observability: Metrics, health checks, usage tracking
- 🚀 Scalability: Database sharding, tenant archival

**Priority Implementation:**
- **Must-Have:** Rate limiting, circuit breakers, audit logging
- **Should-Have:** Metrics, health checks, usage tracking
- **Nice-to-Have:** Sharding, read replicas, multi-region

---

## Implementation Checklist (Based on Gap Analysis)

### Phase 1: Core Multi-Tenancy Enhancement (Week 1-2) 🔥

**Platform-Service Updates:**
- [ ] 📝 Update `Tenant.java` entity (+18 fields)
- [ ] 📝 Update `V1__init_platform_service.sql` (add all new columns + tables)
- [ ] 📝 Update `ProvisionTenantRequest.java` (+3 fields)
- [ ] 📝 Extend `MigrationInvokeAction.java` (add auth-service WebClient)

**Backend-Service New Components:**
- [ ] ✨ CREATE `TenantMigrationController.java` (internal endpoint)
- [ ] ✨ CREATE `TenantContext.java` (ThreadLocal)
- [ ] ✨ CREATE `TenantInterceptor.java` (extract X-Tenant-Id)
- [ ] ✨ CREATE `TenantDataSourceRouter.java` (AbstractRoutingDataSource)
- [ ] ✨ CREATE `TenantDataSourceCache.java` (Caffeine cache)
- [ ] ✨ CREATE `DataSourceConfig.java` (configure routing)

**Auth-Service New Components:**
- [ ] ✨ CREATE `SignupController.java`
- [ ] ✨ CREATE `POST /api/signup/personal` endpoint
- [ ] ✨ CREATE `POST /api/signup/organization` endpoint
- [ ] 📝 Update `AuthServiceImpl.signup()` to add custom Cognito attributes

**Gateway-Service:**
- [ ] ⚠️  CHECK if `TenantContextFilter` exists
- [ ] ✨ CREATE `TenantContextFilter.java` if missing (extract JWT tenantId → header)

**Testing:**
- [ ] E2E test: B2C personal signup flow
- [ ] E2E test: B2B organization signup flow
- [ ] Test tenant database isolation
- [ ] Test JWT with custom:tenantId

### Phase 2: Production Readiness (Week 3-4) ⭕

**Security & Compliance:**
- [ ] ✨ CREATE `TenantRateLimitFilter` in gateway
- [ ] ✨ CREATE `AuditInterceptor` in backend
- [ ] ✨ CREATE `TenantDataController` (export/delete APIs)

**Performance:**
- [ ] 📝 UPDATE `TenantDataSourceConfig` (per-tenant pools)
- [ ] ✨ CREATE `TenantDataSourceCache` with eviction
- [ ] 📝 ADD `@CircuitBreaker` to `PlatformServiceClient`

**Observability:**
- [ ] ✨ CREATE `TenantMetricsFilter`
- [ ] ✨ CREATE `UsageTracker` service
- [ ] ✨ CREATE `TenantHealthIndicator`

### Phase 3: Advanced Features (Week 5+) ⚪

**Lifecycle Management:**
- [ ] ✨ CREATE `TenantLifecycleService` (archival)
- [ ] ✨ CREATE S3 archive functionality

**Scalability:**
- [ ] ✨ CREATE `ShardSelector` service
- [ ] 📝 ADD shard-aware provisioning

---

## Quick Win Priorities

**Can Implement Immediately (Existing Framework Supports):**
1. ✅ Update Tenant entity and migration (1-2 hours)
2. ✅ Update ProvisionTenantRequest DTO (30 min)
3. ✅ Create TenantMigrationController in backend (2-3 hours)
4. ✅ Extend MigrationInvokeAction for auth-service (1 hour)

**Slightly More Complex:**
5. Create SignupController (4-6 hours)
6. Implement tenant routing in backend (6-8 hours)

**Defer to Phase 2:**
- Rate limiting
- Audit logging
- Metrics

---

## Reference Documents

- **[GAP_ANALYSIS.md](./GAP_ANALYSIS.md)** - Detailed code review and findings
- **[PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md)** - Advanced features
- **[HLD.md](../../HLD.md)** - System architecture

**Legend:**
- ✨ CREATE = New component
- 📝 UPDATE = Modify existing
- ⚠️  CHECK = Verify if exists

---

**Implementation Ready!** Start with Phase 1 items using existing framework. 🚀
