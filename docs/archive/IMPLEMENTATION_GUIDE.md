# Multi-Tenant B2B/B2C Implementation Guide
## Structured Day-by-Day Plan with SOLID Principles

**Version:** 1.0  
**Duration:** 12-15 days  
**Updated:** 2025-11-25

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture Principles](#architecture-principles)
3. [Implementation Phases](#implementation-phases)
4. [Daily Implementation Plan](#daily-implementation-plan)
5. [Code Standards & Patterns](#code-standards--patterns)
6. [Testing Strategy](#testing-strategy)
7. [Rollout Plan](#rollout-plan)

---

## Project Overview

### Goal
Implement unified B2B (organization) and B2C (personal) tenant onboarding with automated database provisioning, SSO support, and production-grade features.

### Current State
- ✅ Excellent action-based tenant provisioning framework
- ✅ Database-per-tenant infrastructure
- ✅ Cognito authentication
- ✅ Flyway migrations

### What We're Adding
- Multi-tenant type support (PERSONAL/ORGANIZATION)
- SSO/IDP configuration
- B2C/B2B signup orchestration
- Runtime tenant isolation
- Production-ready features

---

## Architecture Principles

### SOLID Principles Applied

#### 1. Single Responsibility Principle (SRP)
```
✅ Already Applied:
- TenantProvisioningService: Only orchestrates provisioning
- StorageProvisionAction: Only creates storage
- MigrationInvokeAction: Only triggers migrations

✅ We'll Maintain:
- SignupController: Only handles signup requests
- TenantContext: Only manages thread-local tenant ID
- TenantDataSourceRouter: Only routes to correct DB
```

#### 2. Open/Closed Principle (OCP)
```
✅ Already Applied:
- Action pipeline: Add new actions without modifying orchestrator
  for (TenantProvisionAction action : actions) { action.execute(ctx); }

✅ We'll Extend:
- New actions can be added by implementing TenantProvisionAction
- NO modification to TenantProvisioningServiceImpl
- Spring's @Order determines execution sequence
```

#### 3. Liskov Substitution Principle (LSP)
```
✅ All actions implement TenantProvisionAction interface
✅ Can swap implementations without breaking orchestrator
```

#### 4. Interface Segregation Principle (ISP)
```
✅ We'll Define:
- TenantProvisionAction: execute(context)
- TenantMigrationService: migrate(tenantId, config)
- TenantRoutingService: getDataSource(tenantId)

✅ Clients only depend on what they use
```

#### 5. Dependency Inversion Principle (DIP)
```
✅ Already Applied:
- High-level TenantProvisioningService depends on TenantProvisionAction abstraction
- Low-level StorageProvisionAction implements abstraction

✅ We'll Apply:
- Controllers depend on Service interfaces, not implementations
- Services use repository/client interfaces
```

### Modern Patterns We'll Use

1. **Strategy Pattern**: Action pipeline (already in use)
2. **Factory Pattern**: DataSource creation per tenant
3. **Template Method**: Base migration controller with hooks
4. **Circuit Breaker**: Resilience4j for external calls
5. **Cache-Aside**: DataSource caching with Caffeine
6. **Command Pattern**: Tenant provisioning actions

---

## Implementation Phases

### Phase 1: Foundation (Days 1-5) 🔥 CRITICAL
**Goal:** Get basic B2C/B2B onboarding working

- Database schema updates
- Platform-service enhancements
- Backend tenant migration endpoint
- Auth-service signup orchestration

**Deliverable:** Can create personal and organization tenants via API

---

### Phase 2: Runtime Isolation (Days 6-9) 🔥 CRITICAL
**Goal:** Requests route to correct tenant database

- Gateway tenant extraction
- Backend tenant routing
- DataSource management
- E2E testing

**Deliverable:** Multi-tenant data isolation working end-to-end

---

### Phase 3: Production Features (Days 10-12) ⭕ IMPORTANT
**Goal:** Production-ready security and resilience

- Rate limiting
- Audit logging
- Circuit breakers
- Metrics

**Deliverable:** Production-grade reliability

---

### Phase 4: Advanced Features (Days 13-15) ⚪ NICE-TO-HAVE
**Goal:** Enterprise features

- Tenant lifecycle management
- Usage tracking
- Health monitoring

**Deliverable:** Enterprise-ready platform

---

## Daily Implementation Plan

---

### **DAY 1: Database Schema Foundation**

**Priority:** 🔥 CRITICAL  
**Duration:** 6-8 hours  
**Dependencies:** None

#### Tasks

##### 1.1 Update Tenant Entity (2 hours)

**File:** `platform-service/.../entity/Tenant.java`

**Principles Applied:**
- SRP: Entity only represents data
- OCP: New fields can be added without breaking existing code

**Implementation:**
```java
@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant {
    // Existing fields (DO NOT MODIFY)
    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String status;
    // ... existing 8 fields ...
    
    // NEW: Multi-tenancy type fields
    @Column(name = "tenant_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantType tenantType = TenantType.PERSONAL; // PERSONAL or ORGANIZATION
    
    @Column(name = "owner_email")
    private String ownerEmail;
    
    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 1;
    
    // NEW: SSO/IDP configuration
    @Column(name = "sso_enabled")
    private Boolean ssoEnabled = false;
    
    @Column(name = "idp_type")
    @Enumerated(EnumType.STRING)
    private IdpType idpType; // COGNITO_DEFAULT, SAML, OIDC, GOOGLE, AZURE_AD
    
    @Column(name = "idp_metadata_url")
    private String idpMetadataUrl;
    
    @Column(name = "idp_entity_id")
    private String idpEntityId;
    
    @Column(name = "idp_config_json", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private Map<String, Object> idpConfigJson;
    
    // NEW: Security & Compliance
    @Column(name = "encryption_key_id")
    private String encryptionKeyId;
    
    @Column(name = "data_residency")
    private String dataResidency; // US, EU, APAC
    
    // NEW: Performance & Scalability
    @Column(name = "db_shard")
    private String dbShard = "shard-1";
    
    @Column(name = "read_replica_url")
    private String readReplicaUrl;
    
    @Column(name = "connection_pool_min")
    private Integer connectionPoolMin = 2;
    
    @Column(name = "connection_pool_max")
    private Integer connectionPoolMax = 10;
    
    // NEW: Lifecycle Management
    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;
    
    @Column(name = "subscription_status")
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;
    
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
    
    @Column(name = "archived_to_s3")
    private Boolean archivedToS3 = false;
}
```

**Create Enums:**
```java
// platform-service/.../entity/TenantType.java
public enum TenantType {
    PERSONAL,
    ORGANIZATION
}

// platform-service/.../entity/IdpType.java
public enum IdpType {
    COGNITO_DEFAULT,
    SAML,
    OIDC,
    GOOGLE,
    AZURE_AD,
    OKTA,
    PING
}

// platform-service/.../entity/SubscriptionStatus.java
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    SUSPENDED,
    CANCELLED
}
```

##### 1.2 Update Database Migration (3 hours)

**File:** `platform-service/.../db/migration/V1__init_platform_service.sql`

**Action:** Replace existing V1 migration

**Implementation:**
```sql
-- V1: Enhanced schema for multi-tenant platform with B2B/B2C and SSO support
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    -- Core identity
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
    idp_type VARCHAR(64),
    idp_metadata_url TEXT,
    idp_entity_id VARCHAR(512),
    idp_config_json JSONB,
    
    -- Security & Compliance
    encryption_key_id VARCHAR(255),
    data_residency VARCHAR(64),
    
    -- Performance & Scalability
    db_shard VARCHAR(64) DEFAULT 'shard-1',
    read_replica_url TEXT,
    connection_pool_min INTEGER DEFAULT 2,
    connection_pool_max INTEGER DEFAULT 10,
    
    -- Lifecycle Management
    trial_ends_at TIMESTAMPTZ,
    subscription_status VARCHAR(32) DEFAULT 'TRIAL',
    archived_at TIMESTAMPTZ,
    archived_to_s3 BOOLEAN DEFAULT FALSE,
    
    -- Timestamps  
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_tenant_type ON tenant(tenant_type);
CREATE INDEX idx_tenant_owner ON tenant(owner_email);
CREATE INDEX idx_tenant_status ON tenant(status);
CREATE INDEX idx_tenant_sso ON tenant(sso_enabled) WHERE sso_enabled = TRUE;
CREATE INDEX idx_tenant_shard ON tenant(db_shard);
CREATE INDEX idx_tenant_subscription ON tenant(subscription_status);
CREATE INDEX idx_tenant_trial ON tenant(trial_ends_at) WHERE subscription_status = 'TRIAL';

-- Audit log for compliance (GDPR, HIPAA, SOC2)
CREATE TABLE IF NOT EXISTS tenant_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    request_id VARCHAR(64),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant ON tenant_audit_log(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_action ON tenant_audit_log(action);
CREATE INDEX idx_audit_user ON tenant_audit_log(user_id, timestamp DESC);
CREATE INDEX idx_audit_timestamp ON tenant_audit_log(timestamp DESC);

-- Usage metrics for cost allocation
CREATE TABLE IF NOT EXISTS tenant_usage_metrics (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    metric_date DATE NOT NULL,
    api_calls BIGINT DEFAULT 0,
    storage_mb BIGINT DEFAULT 0,
    data_transfer_mb BIGINT DEFAULT 0,
    compute_hours DECIMAL(10,2) DEFAULT 0,
    UNIQUE(tenant_id, metric_date)
);

CREATE INDEX idx_usage_tenant_date ON tenant_usage_metrics(tenant_id, metric_date DESC);
CREATE INDEX idx_usage_date ON tenant_usage_metrics(metric_date DESC);
```

##### 1.3 Update DTO (1 hour)

**File:** `platform-service/.../dto/ProvisionTenantRequest.java`

**Principles Applied:**
- Immutability: Using Java record
- Validation: Bean validation annotations

**Implementation:**
```java
public record ProvisionTenantRequest(
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}$", 
             message = "Tenant id must be 3-64 chars alphanum, underscore or hyphen")
    String id,
    
    @NotBlank(message = "Name is required")
    String name,
    
    @Pattern(regexp = "^(SCHEMA|DATABASE)$", 
             message = "storageMode must be SCHEMA or DATABASE")
    String storageMode,
    
    @Pattern(regexp = "^(STANDARD|PREMIUM|ENTERPRISE)$", 
             message = "slaTier must be STANDARD, PREMIUM or ENTERPRISE")
    String slaTier,
    
    // NEW FIELDS
    @NotNull(message = "Tenant type is required")
    TenantType tenantType, // PERSONAL or ORGANIZATION
    
    @Email(message = "Valid owner email is required")
    String ownerEmail,
    
    @Min(value = 1, message = "Max users must be at least 1")
    @Max(value = 10000, message = "Max users cannot exceed 10000")
    Integer maxUsers
) {
    // Factory methods for convenience
    public static ProvisionTenantRequest forPersonal(String id, String email) {
        return new ProvisionTenantRequest(
            id,
            email + "'s Workspace",
            "DATABASE",
            "STANDARD",
            TenantType.PERSONAL,
            email,
            1
        );
    }
    
    public static ProvisionTenantRequest forOrganization(
        String id, 
        String name, 
        String adminEmail, 
        String tier
    ) {
        int maxUsers = switch(tier) {
            case "STANDARD" -> 50;
            case "PREMIUM" -> 200;
            case "ENTERPRISE" -> 10000;
            default -> 50;
        };
        
        return new ProvisionTenantRequest(
            id,
            name,
            "DATABASE",
            tier,
            TenantType.ORGANIZATION,
            adminEmail,
            maxUsers
        );
    }
}
```

##### 1.4 Testing (2 hours)

```bash
# Clean and rebuild
cd platform-service
mvn clean package -DskipTests

# Apply migration locally
docker-compose up postgres -d
# Run application to apply Flyway migration

# Verify schema
psql -h localhost -U postgres -d platform
\d tenant
\d tenant_audit_log
\d tenant_usage_metrics

# Test data
INSERT INTO tenant (id, name, status, storage_mode, sla_tier, tenant_type, owner_email, max_users)
VALUES ('test-personal', 'Test User', 'ACTIVE', 'DATABASE', 'STANDARD', 'PERSONAL', 'test@example.com', 1);

INSERT INTO tenant (id, name, status, storage_mode, sla_tier, tenant_type, owner_email, max_users)
VALUES ('test-org', 'Test Organization', 'ACTIVE', 'DATABASE', 'ENTERPRISE', 'ORGANIZATION', 'admin@testorg.com', 100);

SELECT id, name, tenant_type, max_users, owner_email FROM tenant;
```

**Day 1 Deliverable:** ✅ Enhanced database schema ready

---

### **DAY 2: Platform Service Enhancements**

**Priority:** 🔥 CRITICAL  
**Duration:** 6-8 hours  
**Dependencies:** Day 1 complete

#### Tasks

##### 2.1 Update TenantProvisioningServiceImpl (2 hours)

**File:** `platform-service/.../service/TenantProvisioningServiceImpl.java`

**Changes:** Minimal - existing code already handles new fields via setters

**Add:** Logging for new fields
```java
@Override
public TenantDto provision(ProvisionTenantRequest request) {
    // ... existing validation ...
    
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setName(request.name());
    tenant.setStatus(TenantStatus.PROVISIONING.name());
    tenant.setStorageMode(request.storageMode());
    tenant.setSlaTier(request.slaTier());
    
    // NEW: Set tenant type and limits
    tenant.setTenantType(request.tenantType());
    tenant.setOwnerEmail(request.ownerEmail());
    tenant.setMaxUsers(request.maxUsers());
    
    // Set trial period for new tenants
    if (request.tenantType() == TenantType.ORGANIZATION) {
        tenant.setTrialEndsAt(OffsetDateTime.now().plusDays(30));
        tenant.setSubscriptionStatus(SubscriptionStatus.TRIAL);
    }
    
    tenant.setCreatedAt(OffsetDateTime.now());
    tenant.setUpdatedAt(OffsetDateTime.now());
    
    tenantRepository.save(tenant);
    
    // ... existing action execution ...
    
    log.info("tenant_provisioned tenantId={} type={} owner={} maxUsers={}", 
        tenantId, request.tenantType(), request.ownerEmail(), request.maxUsers());
    
    // ... rest remains same...
}
```

##### 2.2 Add Auth-Service WebClient (2 hours)

**File:** `platform-service/.../config/WebClientConfig.java`

**Principles Applied:**
- DIP: Depend on WebClient abstraction
- SRP: Separate WebClient beans for each service

**Implementation:**
```java
@Configuration
public class WebClientConfig {
    
    @Bean
    @Qualifier("backendWebClient")
    public WebClient backendWebClient(
        @Value("${services.backend-service.url:http://backend-service:8082}") String baseUrl
    ) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
    
    @Bean
    @Qualifier("authWebClient")  // NEW
    public WebClient authWebClient(
        @Value("${services.auth-service.url:http://auth-service:8081}") String baseUrl
    ) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

##### 2.3 Extend MigrationInvokeAction (3 hours)

**File:** `platform-service/.../action/MigrationInvokeAction.java`

**Principles Applied:**
- OCP: Can add more services without modifying core logic
- Strategy Pattern: Each service migration is a strategy

**Implementation:**
```java
@Component
@RequiredArgsConstructor
@Slf4j
@Order(50)
public class MigrationInvokeAction implements TenantProvisionAction {
    
    private final WebClient backendWebClient;
    private final WebClient authWebClient;  // NEW
    
    @Override
    public void execute(TenantProvisionContext context) throws TenantProvisioningException {
        String tenantId = context.getTenant().getId();
        
        // Build tenant DB config to share with services
        TenantDbConfig dbConfig = buildDbConfig(context);
        
        // Service migration strategies
        List<ServiceMigrationStrategy> strategies = List.of(
            new BackendServiceMigration(backendWebClient),
            new AuthServiceMigration(authWebClient)  // NEW
        );
        
        String lastVersion = null;
        for (ServiceMigrationStrategy strategy : strategies) {
            try {
                MigrationResult result = strategy.migrate(tenantId, dbConfig);
                lastVersion = result.lastVersion();
                log.info("✅ Migration succeeded: service={} tenant={} version={}", 
                    strategy.serviceName(), tenantId, lastVersion);
            } catch (Exception e) {
                log.error("❌ Migration failed: service={} tenant={} error={}", 
                    strategy.serviceName(), tenantId, e.getMessage());
                throw new TenantProvisioningException(
                    tenantId, 
                    strategy.serviceName() + " migration failed: " + e.getMessage(), 
                    e
                );
            }
        }
        
        context.setLastMigrationVersion(lastVersion);
    }
    
    private TenantDbConfig buildDbConfig(TenantProvisionContext context) {
        Tenant tenant = context.getTenant();
        return new TenantDbConfig(
            tenant.getJdbcUrl(),
            tenant.getDbUserSecretRef(),
            SimpleCryptoUtil.decrypt(tenant.getDbUserPasswordEnc())
        );
    }
    
    // Strategy interface
    private interface ServiceMigrationStrategy {
        String serviceName();
        MigrationResult migrate(String tenantId, TenantDbConfig config);
    }
    
    // Backend service strategy
    @RequiredArgsConstructor
    private static class BackendServiceMigration implements ServiceMigrationStrategy {
        private final WebClient webClient;
        
        @Override
        public String serviceName() { return "backend-service"; }
        
        @Override
        public MigrationResult migrate(String tenantId, TenantDbConfig config) {
            return webClient.post()
                .uri("/internal/tenants/{tenantId}/migrate", tenantId)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(MigrationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
        }
    }
    
    // Auth service strategy (NEW)
    @RequiredArgsConstructor
    private static class AuthServiceMigration implements ServiceMigrationStrategy {
        private final WebClient webClient;
        
        @Override
        public String serviceName() { return "auth-service"; }
        
        @Override
        public MigrationResult migrate(String tenantId, TenantDbConfig config) {
            return webClient.post()
                .uri("/internal/tenants/{tenantId}/migrate", tenantId)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(MigrationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
        }
    }
    
    // Shared DTOs
    public record TenantDbConfig(String jdbcUrl, String username, String password) {}
    public record MigrationResult(String lastVersion) {}
}
```

**Day 2 Deliverable:** ✅ Platform service ready to call multiple services for migrations

---

### **DAY 3: Backend Tenant Migration Endpoint**

**Priority:** 🔥 CRITICAL  
**Duration:** 6-8 hours  
**Dependencies:** Day 2 complete

#### Tasks

##### 3.1 Create TenantMigrationController (4 hours)

**File:** `backend-service/.../tenant/TenantMigrationController.java`

**Principles Applied:**
- SRP: Only handles tenant migration requests
- DIP: Depends on DataSource abstraction

**Implementation:**
```java
@RestController
@RequestMapping("/internal/tenants")
@Slf4j
public class TenantMigrationController {
    
    @PostMapping("/{tenantId}/migrate")
    public ResponseEntity<MigrationResult> runMigrations(
        @PathVariable String tenantId,
        @RequestBody @Valid TenantDbConfig dbConfig
    ) {
        log.info("Starting tenant migration: tenantId={}", tenantId);
        
        try {
            // Create dedicated DataSource for this tenant
            DataSource tenantDataSource = createTenantDataSource(dbConfig);
            
            // Configure Flyway for tenant database
            Flyway flyway = Flyway.configure()
                .dataSource(tenantDataSource)
                .locations("classpath:db/tenant-template")
                .table(tenantId + "_schema_history") // Tenant-specific history table
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .load();
            
            // Execute migrations
            MigrateResult result = flyway.migrate();
            
            String version = result.targetSchemaVersion != null 
                ? result.targetSchemaVersion 
                : "baseline";
            
            log.info("✅ Tenant migration completed: tenantId={} migrations={} version={}", 
                tenantId, result.migrationsExecuted, version);
            
            return ResponseEntity.ok(new MigrationResult(
                true,
                result.migrationsExecuted,
                version
            ));
            
        } catch (Exception e) {
            log.error("❌ Tenant migration failed: tenantId={} error={}", 
                tenantId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MigrationResult(false, 0, null));
        }
    }
    
    private DataSource createTenantDataSource(TenantDbConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(5); // Small pool for migrations
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setPoolName("migration-" + UUID.randomUUID());
        
        return new HikariDataSource(hikariConfig);
    }
    
    // DTOs
    public record TenantDbConfig(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password
    ) {}
    
    public record MigrationResult(
        boolean success,
        int migrationsExecuted,
        String lastVersion
    ) {}
}
```

##### 3.2 Create Tenant Template Migration (2 hours)

**File:** `backend-service/.../resources/db/tenant-template/V1__tenant_initial_schema.sql`

**Implementation:**
```sql
-- V1: Initial schema for tenant database
-- This schema is applied to each tenant's dedicated database

-- Entries table (no tenant_id column needed - DB isolation)
CREATE TABLE IF NOT EXISTS entries (
    id BIGSERIAL PRIMARY KEY,
    meta_key VARCHAR(255) NOT NULL UNIQUE,
    meta_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255)
);

-- Indexes for performance
CREATE INDEX idx_entries_key ON entries(meta_key);
CREATE INDEX idx_entries_created_at ON entries(created_at DESC);
CREATE INDEX idx_entries_created_by ON entries(created_by);

-- Comments for documentation
COMMENT ON TABLE entries IS 'Key-value entries specific to this tenant';
COMMENT ON COLUMN entries.meta_key IS 'Unique key within this tenant';
COMMENT ON COLUMN entries.meta_value IS 'Value associated with the key';
```

##### 3.3 Testing (2 hours)

```bash
# Build and start backend-service
cd backend-service
mvn clean package -DskipTests
docker-compose up backend-service -d

# Test migration endpoint
curl -X POST http://localhost:8082/internal/tenants/test-tenant/migrate \
  -H "Content-Type: application/json" \
  -d '{
    "jdbcUrl": "jdbc:postgresql://localhost:5432/db_test_tenant",
    "username": "test_tenant_user",
    "password": "password"
  }'

# Verify migration
psql -h localhost -U postgres -d db_test_tenant
\d entries
SELECT * FROM test_tenant_schema_history;
```

**Day 3 Deliverable:** ✅ Backend service can run migrations on tenant databases

---

### **DAY 4-5: Auth Service Signup Orchestration**

**Priority:** 🔥 CRITICAL  
**Duration:** 12-16 hours  
**Dependencies:** Days 1-3 complete

#### Tasks

##### 4.1 Create SignupController (8 hours)

**File:** `auth-service/.../controller/SignupController.java`

**Principles Applied:**
- SRP: Only handles signup orchestration
- DIP: Depends on service abstractions

**Implementation:**
```java
@RestController
@RequestMapping("/api/signup")
@Slf4j
@RequiredArgsConstructor
public class SignupController {
    
    private final CognitoIdentityProvider

Client cognitoClient;
    private final CognitoProperties cognitoProperties;
    private final WebClient platformWebClient;
    
    /**
     * B2C Personal Signup Flow
     * 1. Generate tenant ID
     * 2. Provision tenant via platform-service
     * 3. Create Cognito user with custom:tenantId
     * 4. Return success
     */
    @PostMapping("/personal")
    public ResponseEntity<SignupResponse> signupPersonal(@RequestBody @Valid PersonalSignupRequest request) {
        String email = request.email();
        log.info("B2C signup initiated: email={}", email);
        
        try {
            // 1. Generate unique tenant ID
            String tenantId = generateTenantId(email);
            
            // 2. Provision tenant
            TenantDto tenant = provisionTenant(
                ProvisionTenantRequest.forPersonal(tenantId, email)
            );
            
            // 3. Create Cognito user with tenant context
            createCognitoUser(
                email,
                request.password(),
                request.name(),
                tenantId,
                TenantType.PERSONAL,
                "tenant-user" // Default role for personal tenant
            );
            
            log.info("✅ B2C signup completed: email={} tenantId={}", email, tenantId);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(
                    true,
                    "Signup successful. Please verify your email.",
                    tenantId
                ));
                
        } catch (Exception e) {
            log.error("❌ B2C signup failed: email={} error={}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new SignupResponse(false, e.getMessage(), null));
        }
    }
    
    /**
     * B2B Organization Signup Flow
     * 1. Validate company domain
     * 2. Generate tenant ID from company name
     * 3. Provision organization tenant
     * 4. Create admin Cognito user
     * 5. Send admin notification
     */
    @PostMapping("/organization")
    public ResponseEntity<SignupResponse> signupOrganization(@RequestBody @Valid OrganizationSignupRequest request) {
        String adminEmail = request.adminEmail();
        String companyName = request.companyName();
        
        log.info("B2B signup initiated: company={} admin={}", companyName, adminEmail);
        
        try {
            // 1. Validate corporate email (optional)
            validateCorporateEmail(adminEmail);
            
            // 2. Generate tenant ID from company name
            String tenantId = slugify(companyName);
            
            // 3. Provision organization tenant
            TenantDto tenant = provisionTenant(
                ProvisionTenantRequest.forOrganization(
                    tenantId,
                    companyName,
                    adminEmail,
                    request.tier()
                )
            );
            
            // 4. Create admin user
            createCognitoUser(
                adminEmail,
                request.password(),
                request.adminName(),
                tenantId,
                TenantType.ORGANIZATION,
                "tenant-admin" // Admin role
            );
            
            log.info("✅ B2B signup completed: company={} tenantId={} admin={}", 
                companyName, tenantId, adminEmail);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(
                    true,
                    "Organization created successfully. Admin user has been notified.",
                    tenantId
                ));
                
        } catch (Exception e) {
            log.error("❌ B2B signup failed: company={} error={}", companyName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new SignupResponse(false, e.getMessage(), null));
        }
    }
    
    // Helper: Call platform-service to provision tenant
    private TenantDto provisionTenant(ProvisionTenantRequest request) {
        return platformWebClient.post()
            .uri("/platform/api/tenants")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TenantDto.class)
            .timeout(Duration.ofSeconds(60)) // Tenant provisioning can take time
            .block();
    }
    
    // Helper: Create Cognito user with custom attributes
    private void createCognitoUser(
        String email, 
        String password, 
        String name,
        String tenantId,
        TenantType tenantType,
        String role
    ) {
        // Create user
        AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
            .userPoolId(cognitoProperties.getUserPoolId())
            .username(email)
            .userAttributes(
                AttributeType.builder().name("email").value(email).build(),
                AttributeType.builder().name("name").value(name).build(),
                AttributeType.builder().name("custom:tenantId").value(tenantId).build(),
                AttributeType.builder().name("custom:tenantType").value(tenantType.name()).build(),
                AttributeType.builder().name("custom:role").value(role).build()
            )
            .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
            .messageAction(MessageActionType.SUPPRESS) // We'll send custom email
            .build();
        
        cognitoClient.adminCreateUser(createRequest);
        
        // Set permanent password
        cognitoClient.adminSetUserPassword(b -> b
            .userPoolId(cognitoProperties.getUserPoolId())
            .username(email)
            .password(password)
            .permanent(true)
        );
    }
    
    // Helper: Generate tenant ID for personal accounts
    private String generateTenantId(String email) {
        String username = email.split("@")[0];
        String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return "user_" + sanitized + "_" + timestamp;
    }
    
    // Helper: Slugify company name for tenant ID
    private String slugify(String companyName) {
        return companyName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
    
    // Helper: Validate corporate email (optional)
    private void validateCorporateEmail(String email) {
        List<String> freeProviders = List.of("gmail.com", "yahoo.com", "hotmail.com");
        String domain = email.substring(email.indexOf("@") + 1);
        
        if (freeProviders.contains(domain.toLowerCase())) {
            throw new IllegalArgumentException(
                "Please use a corporate email address for organization signup"
            );
        }
    }
    
    // DTOs
    public record PersonalSignupRequest(
        @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String name
    ) {}
    
    public record OrganizationSignupRequest(
        @NotBlank String companyName,
        @Email String adminEmail,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String adminName,
        @Pattern(regexp = "STANDARD|PREMIUM|ENTERPRISE") String tier
    ) {}
    
    public record SignupResponse(
        boolean success,
        String message,
        String tenantId
    ) {}
}
```

##### 4.2 Configure Platform WebClient (2 hours)

**File:** `auth-service/.../config/WebClientConfig.java`

```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient platformWebClient(
        @Value("${services.platform-service.url:http://platform-service:8083}") String baseUrl
    ) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

##### 4.3 Testing (6 hours)

```bash
# Test B2C personal signup
curl -X POST http://localhost:8081/api/signup/personal \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123!",
    "name": "John Doe"
  }'

# Test B2B organization signup
curl -X POST http://localhost:8081/api/signup/organization \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "Acme Corporation",
    "adminEmail": "admin@acme.com",
    "password": "AdminPass123!",
    "adminName": "Jane Admin",
    "tier": "ENTERPRISE"
  }'

# Verify in Cognito
aws cognito-idp admin-get-user \
  --user-pool-id <POOL_ID> \
  --username john@example.com \
  --profile personal

# Verify tenant created
psql -h localhost -U postgres -d platform
SELECT id, name, tenant_type, owner_email FROM tenant;
```

**Days 4-5 Deliverable:** ✅ Complete signup flows for B2C and B2B

---

### **DAY 6-7: Gateway Tenant Context Filter**

**Priority:** 🔥 CRITICAL  
**Duration:** 12-16 hours  
**Dependencies:** Days 1-5 complete

#### Tasks

##### 6.1 Create TenantContextFilter (6 hours)

**File:** `gateway-service/.../filter/TenantContextFilter.java`

**Principles Applied:**
- SRP: Only extracts and propagates tenant ID
- Security: Sanitizes incoming headers to prevent spoofing

**Implementation:**
```java
@Component
@Order(-100) // Execute early in filter chain
@Slf4j
public class TenantContextFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 1. Sanitize: Remove any X-Tenant-Id headers from client
        ServerHttpRequest sanitizedRequest = request.mutate()
            .headers(headers -> {
                headers.remove("X-Tenant-Id");
                headers.remove("X-User-Id");
                headers.remove("X-Tenant-Type");
                headers.remove("X-Authorities");
            })
            .build();
        
        // 2. Extract JWT token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                // 3. Decode JWT and extract custom attributes
                DecodedJWT jwt = JWT.decode(token);
                
                String tenantId = jwt.getClaim("custom:tenantId").asString();
                String userId = jwt.getSubject();
                String tenantType = jwt.getClaim("custom:tenantType").asString();
                String role = jwt.getClaim("custom:role").asString();
                
                if (tenantId == null || tenantId.isBlank()) {
                    log.warn("Missing tenantId in JWT: userId={}", userId);
                    return unauthorized(exchange, "Missing tenant context");
                }
                
                // 4. Enrich request with trusted headers
                ServerHttpRequest enrichedRequest = sanitizedRequest.mutate()
                    .header("X-Tenant-Id", tenantId)
                    .header("X-User-Id", userId)
                    .header("X-Tenant-Type", tenantType)
                    .header("X-Authorities", role)
                    .build();
                
                ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(enrichedRequest)
                    .build();
                
                log.debug("Tenant context extracted: tenantId={} userId={} type={}", 
                    tenantId, userId, tenantType);
                
                return chain.filter(modifiedExchange);
                
            } catch (JWTDecodeException e) {
                log.error("Invalid JWT token: error={}", e.getMessage());
                return unauthorized(exchange, "Invalid token");
            }
        }
        
        // No auth header - allow through for public endpoints
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = String.format("{\"error\": \"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        
        return response.writeWith(Mono.just(buffer));
    }
}
```

##### 6.2 Add JWT Dependency (1 hour)

**File:** `gateway-service/pom.xml`

```xml
<dependency>
    <groupId>com.auth0</groupId>
    <artifactId>java-jwt</artifactId>
    <version>4.4.0</version>
</dependency>
```

##### 6.3 Testing (6 hours)

```bash
# 1. Get JWT token
TOKEN=$(curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"SecurePass123!"}' \
  | jq -r '.accessToken')

# 2. Test header propagation through gateway
curl -X GET http://localhost:8080/api/entries \
  -H "Authorization: Bearer $TOKEN" \
  -v  # Check X-Tenant-Id in forwarded request

# 3. Test header sanitization (should be removed)
curl -X GET http://localhost:8080/api/entries \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: malicious-tenant" \  # Should be stripped
  -v

# 4. Test without token (public endpoints)
curl -X GET http://localhost:8080/health \
  -v  # Should work without auth
```

**Days 6-7 Deliverable:** ✅ Gateway extracts tenant context from JWT and propagates to services

---

### **DAY 8-9: Backend Tenant Routing Infrastructure**

**Priority:** 🔥 CRITICAL  
**Duration:** 12-16 hours  
**Dependencies:** Days 1-7 complete

#### Tasks

##### 8.1 Create Tenant Context (ThreadLocal) (2 hours)

**File:** `backend-service/.../tenant/TenantContext.java`

**Principles Applied:**
- SRP: Only manages thread-local tenant ID
- Thread Safety: ThreadLocal ensures no cross-contamination

**Implementation:**
```java
public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }
    
    public static String getTenantId() {
        String tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }
    
    public static void clear() {
        currentTenant.remove();
    }
}
```

##### 8.2 Create Tenant Interceptor (3 hours)

**File:** `backend-service/.../filter/TenantInterceptor.java`

```java
@Component
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {
        String tenantId = request.getHeader("X-Tenant-Id");
        
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
            log.debug("Tenant context set: tenantId={}", tenantId);
        } else {
            log.warn("Missing X-Tenant-Id header: uri={}", request.getRequestURI());
            // For internal endpoints, might not need tenant
            if (!request.getRequestURI().startsWith("/internal/")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {
        TenantContext.clear();
        log.debug("Tenant context cleared");
    }
}

// Register interceptor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private TenantInterceptor tenantInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
            .addPathPatterns("/api/**")  // Only for API endpoints
            .excludePathPatterns("/internal/**");  // Exclude internal endpoints
    }
}
```

##### 8.3 Create DataSource Router (4 hours)

**File:** `backend-service/.../config/TenantDataSourceRouter.java`

```java
public class TenantDataSourceRouter extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        try {
            return TenantContext.getTenantId();
        } catch (IllegalStateException e) {
            return "default"; // Fallback for non-tenant operations
        }
    }
}
```

##### 8.4 Create DataSource Cache (4 hours)

**File:** `backend-service/.../tenant/TenantDataSourceService.java`

```java
@Service
@Slf4j
public class TenantDataSourceService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private final LoadingCache<String, DataSource> dataSourceCache = Caffeine.newBuilder()
        .maximumSize(100)  // Cache up to 100 tenant DataSources
        .expireAfterAccess(1, TimeUnit.HOURS)
        .removalListener((String key, DataSource ds, RemovalCause cause) -> {
            log.info("Evicting DataSource: tenantId={} cause={}", key, cause);
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        })
        .build(this::createDataSource);
    
    public DataSource getDataSource(String tenantId) {
        return dataSourceCache.get(tenantId);
    }
    
    public void evict(String tenantId) {
        dataSourceCache.invalidate(tenantId);
        log.info("DataSource evicted manually: tenantId={}", tenantId);
    }
    
    private DataSource createDataSource(String tenantId) {
        log.info("Creating DataSource for tenant: {}", tenantId);
        
        // Get tenant DB info from platform-service
        TenantDbInfo dbInfo = restTemplate.getForObject(
            "http://platform-service:8083/internal/tenants/" + tenantId + "/db-info",
            TenantDbInfo.class
        );
        
        if (dbInfo == null) {
            throw new IllegalStateException("Tenant not found: " + tenantId);
        }
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbInfo.jdbcUrl());
        config.setUsername(dbInfo.username());
        config.setPassword(dbInfo.password());
        config.setMinimumIdle(dbInfo.connectionPoolMin() != null ? dbInfo.connectionPoolMin() : 2);
        config.setMaximumPoolSize(dbInfo.connectionPoolMax() != null ? dbInfo.connectionPoolMax() : 10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("tenant-" + tenantId);
        
        return new HikariDataSource(config);
    }
    
    public record TenantDbInfo(
        String jdbcUrl,
        String username,
        String password,
        Integer connectionPoolMin,
        Integer connectionPoolMax
    ) {}
}
```

##### 8.5 Configure DataSource Routing (3 hours)

**File:** `backend-service/.../config/DataSourceConfig.java`

```java
@Configuration
public class DataSourceConfig {
    
    @Autowired
    private TenantDataSourceService dataSourceService;
    
    @Bean
    @Primary
    public DataSource dataSource() {
        TenantDataSourceRouter router = new TenantDataSourceRouter();
        router.setLenientFallback(true);
        router.afterPropertiesSet();
        return router;
    }
    
    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        // Custom initializer that uses TenantDataSourceService
        return new DataSourceInitializer() {
            @Override
            public void initialize() {
                ((TenantDataSourceRouter) dataSource).setTargetDataSources(
                    createTargetDataSources()
                );
            }
            
            private Map<Object, Object> createTargetDataSources() {
                Map<Object, Object> dataSources = new HashMap<>();
                // DataSources will be created lazily by TenantDataSourceService
                return dataSources;
            }
        };
    }
}
```

##### 8.6 Add Platform-Service Internal Endpoint (2 hours)

**File:** `platform-service/.../api/TenantInternalController.java`

```java
@RestController
@RequestMapping("/internal/tenants")
public class TenantInternalController {
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @GetMapping("/{tenantId}/db-info")
    public ResponseEntity<TenantDbInfo> getTenantDbInfo(@PathVariable String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        
        return ResponseEntity.ok(new TenantDbInfo(
            tenant.getJdbcUrl(),
            tenant.getDbUserSecretRef(),
            SimpleCryptoUtil.decrypt(tenant.getDbUserPasswordEnc()),
            tenant.getConnectionPoolMin(),
            tenant.getConnectionPoolMax()
        ));
    }
    
    public record TenantDbInfo(
        String jdbcUrl,
        String username,
        String password,
        Integer connectionPoolMin,
        Integer connectionPoolMax
    ) {}
}
```

##### 8.7 Testing (4 hours)

```bash
# 1. Create two tenants for testing
# Tenant 1: user_john_123
# Tenant 2: acme-corp

# 2. Add test data to each tenant database
psql -h localhost -U postgres -d db_user_john_123
INSERT INTO entries (meta_key, meta_value, created_by) 
VALUES ('key1', 'John''s value', 'john@example.com');

psql -h localhost -U postgres -d db_acme_corp
INSERT INTO entries (meta_key, meta_value, created_by) 
VALUES ('key1', 'Acme''s value', 'admin@acme.com');

# 3. Test tenant isolation
# Login as John
JOHN_TOKEN=$(...)

curl -X GET http://localhost:8080/api/entries \
  -H "Authorization: Bearer $JOHN_TOKEN"
# Should return only John's data

# Login as Acme admin
ACME_TOKEN=$(...)

curl -X GET http://localhost:8080/api/entries \
  -H "Authorization: Bearer $ACME_TOKEN"
# Should return only Acme's data

# 4. Verify DataSource caching
# Check logs for "Creating DataSource for tenant" - should only happen once per tenant

# 5. Test DataSource eviction after 1 hour inactivity
```

**Days 8-9 Deliverable:** ✅ Complete tenant routing with database isolation

---

### **DAY 10-12: Production Features**

**Priority:** ⭕ IMPORTANT  
**Duration:** 18-24 hours  
**Dependencies:** Days 1-9 complete

#### Tasks

##### 10.1 Rate Limiting (6 hours)

**File:** `gateway-service/.../filter/TenantRateLimitFilter.java`

```java
@Component
@Order(-50)
@Slf4j
public class TenantRateLimitFilter implements GlobalFilter {
    
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        
        if (tenantId == null) {
            return chain.filter(exchange);  // No rate limiting for unauthenticated
        }
        
        // Get tenant tier from header or default to STANDARD
        String tier = exchange.getRequest().getHeaders()
            .getFirst("X-Tenant-Tier");
        
        int requestsPerMinute = getRequestLimit(tier);
        
        RateLimiter limiter = rateLimiters.computeIfAbsent(
            tenantId,
            k -> RateLimiter.of(k, RateLimiterConfig.custom()
                .limitForPeriod(requestsPerMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMillis(100))
                .build())
        );
        
        if (limiter.acquirePermission()) {
            return chain.filter(exchange);
        } else {
            log.warn("Rate limit exceeded: tenantId={} tier={}", tenantId, tier);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }
    
    private int getRequestLimit(String tier) {
        return switch (tier) {
            case "FREE" -> 60;
            case "STANDARD" -> 600;
            case "PREMIUM" -> 6000;
            case "ENTERPRISE" -> 60000;
            default -> 100;
        };
    }
}
```

##### 10.2 Audit Logging (6 hours)

**File:** `backend-service/.../audit/AuditInterceptor.java`

```java
@Component
@Slf4j
public class AuditInterceptor implements HandlerInterceptor {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {
        try {
            String tenantId = TenantContext.getTenantId();
            String userId = request.getHeader("X-User-Id");
            String action = request.getMethod() + " " + request.getRequestURI();
            
            AuditLog log = AuditLog.builder()
                .tenantId(tenantId)
                .userId(userId)
                .action(action)
                .resourceType(extractResourceType(request.getRequestURI()))
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .requestId(request.getHeader("X-Request-Id"))
                .timestamp(Instant.now())
                .build();
            
            auditLogRepository.save(log);
            
        } catch (Exception e) {
            log.error("Audit logging failed: {}", e.getMessage());
            // Don't fail request if audit fails
        }
    }
    
    private String extractResourceType(String uri) {
        // /api/entries/123 -> entries
        String[] parts = uri.split("/");
        return parts.length > 2 ? parts[2] : "unknown";
    }
}
```

##### 10.3 Circuit Breaker (6 hours)

**File:** `platform-service/.../client/PlatformServiceClient.java`

```java
@Service
public class PlatformServiceClient {
    
    private final RestTemplate restTemplate;    private final Cache<String, TenantDbInfo> localCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();
    
    @CircuitBreaker(name = "platform-service", fallbackMethod = "getTenantDbInfoFallback")
    @Retry(name = "platform-service")
    public TenantDbInfo getTenantDbInfo(String tenantId) {
        TenantDbInfo info = restTemplate.getForObject(
            "http://platform-service:8083/internal/tenants/" + tenantId + "/db-info",
            TenantDbInfo.class
        );
        
        // Cache successful response
        if (info != null) {
            localCache.put(tenantId, info);
        }
        
        return info;
    }
    
    // Fallback: use cached value
    public TenantDbInfo getTenantDbInfoFallback(String tenantId, Exception ex) {
        log.warn("Platform service unavailable, using cached data: tenantId={}", tenantId);
        TenantDbInfo cached = localCache.getIfPresent(tenantId);
        
        if (cached == null) {
            throw new ServiceUnavailableException("Platform service unavailable and no cached data");
        }
        
        return cached;
    }
}

// application.yml
resilience4j:
  circuitbreaker:
    instances:
      platform-service:
        slidingWindowSize: 100
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 10
  retry:
    instances:
      platform-service:
        maxAttempts: 3
        waitDuration: 1s
```

**Days 10-12 Deliverable:** ✅ Production-ready features active

---

### **DAY 13-15: Advanced Features**

**Priority:** ⚪ NICE-TO-HAVE  
**Duration:** 18-24 hours  
**Dependencies:** Days 1-12 complete

#### Tasks

##### 13.1 Tenant Lifecycle Management (8 hours)

**File:** `platform-service/.../lifecycle/TenantLifecycleService.java`

```java
@Service
@Slf4j
public class TenantLifecycleService {
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private S3Client s3Client;
    
    @Autowired
    private TenantProvisioner tenantProvisioner;
    
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void archiveExpiredTrials() {
        log.info("Running trial expiration check");
        
        OffsetDateTime cutoff = OffsetDateTime.now();
        
        List<Tenant> expiredTrials = tenantRepository.findBySubscriptionStatusAndTrialEndsAtBefore(
            SubscriptionStatus.TRIAL,
            cutoff
        );
        
        for (Tenant tenant : expiredTrials) {
            try {
                archiveTenant(tenant);
                log.info("✅ Archived expired trial: tenantId={}", tenant.getId());
            } catch (Exception e) {
                log.error("❌ Failed to archive tenant: tenantId={} error={}", 
                    tenant.getId(), e.getMessage(), e);
            }
        }
    }
    
    private void archiveTenant(Tenant tenant) {
        // 1. Export tenant data
        byte[] exportData = exportTenantData(tenant);
        
        // 2. Upload to S3
        String key = "tenant-archives/" + tenant.getId() + "/" + 
            OffsetDateTime.now().toString() + "/archive.zip";
        
        s3Client.putObject(PutObjectRequest.builder()
            .bucket("tenant-archives")
            .key(key)
            .build(),
            RequestBody.fromBytes(exportData));
        
        // 3. Drop tenant database
        tenantProvisioner.dropTenantDatabase(tenant.getId());
        
        // 4. Update tenant status
        tenant.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        tenant.setArchivedAt(OffsetDateTime.now());
        tenant.setArchivedToS3(true);
        tenant.setUpdatedAt(OffsetDateTime.now());
        
        tenantRepository.save(tenant);
    }
    
    private byte[] exportTenantData(Tenant tenant) {
        // TODO: Call all services to export their data
        // For now, placeholder
        return new byte[0];
    }
}
```

##### 13.2 Tenant Metrics (6 hours)

**File:** `backend-service/.../metrics/TenantMetricsFilter.java`

```java
@Component
public class TenantMetricsFilter implements Filter {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        String tenantId = ((HttpServletRequest) request).getHeader("X-Tenant-Id");
        String tier = ((HttpServletRequest) request).getHeader("X-Tenant-Tier");
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            chain.doFilter(request, response);
        } finally {
            if (tenantId != null) {
                // Record latency
                sample.stop(Timer.builder("http.request.duration")
                    .tag("tenant", tenantId)
                    .tag("tier", tier != null ? tier : "unknown")
                    .tag("method", ((HttpServletRequest) request).getMethod())
                    .register(meterRegistry));
                
                // Count API calls
                meterRegistry.counter("api.calls",
                    "tenant", tenantId,
                    "tier", tier != null ? tier : "unknown"
                ).increment();
            }
        }
    }
}
```

##### 13.3 Health Checks (4 hours)

**File:** `platform-service/.../health/TenantHealthIndicator.java`

```java
@Component("tenantHealth")
public class TenantHealthIndicator implements HealthIndicator {
    
    @Autowired
    private TenantRepository tenantRepository;
    
    @Autowired
    private TenantProvisioner tenantProvisioner;
    
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Count active tenants
            long activeTenants = tenantRepository.countByStatus("ACTIVE");
            details.put("activeTenants", activeTenants);
            
            // Count failed tenants
            long failedTenants = tenantRepository.countByStatusIn(
                List.of("PROVISION_ERROR", "MIGRATION_ERROR")
            );
            details.put("failedTenants", failedTenants);
            
            // Sample tenant DB connectivity
            List<Tenant> sampleTenants = tenantRepository
                .findByStatus("ACTIVE")
                .stream()
                .limit(5)
                .toList();
            
            int healthyCount = 0;
            for (Tenant tenant : sampleTenants) {
                if (checkTenantDbConnectivity(tenant)) {
                    healthyCount++;
                }
            }
            
            details.put("sampleDbHealth",healthyCount + "/" + sampleTenants.size());
            
            // Determine overall health
            if (failedTenants == 0 && healthyCount == sampleTenants.size()) {
                return Health.up().withDetails(details).build();
            } else if (failedTenants > 0 || healthyCount < sampleTenants.size()) {
                return Health.degraded().withDetails(details).build();
            } else {
                return Health.down().withDetails(details).build();
            }
            
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private boolean checkTenantDbConnectivity(Tenant tenant) {
        try {
            // Simple connectivity check
            tenantProvisioner.testConnection(tenant.getJdbcUrl());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

**Days 13-15 Deliverable:** ✅ Enterprise-ready platform with lifecycle management

---

## Testing Strategy

### Unit Testing

**Platform-Service:**
```java
@SpringBootTest
class TenantProvisioningServiceImplTest {
    
    @Test
    void shouldProvisionPersonalTenant_WhenValidRequest() {
        // Given
        var request = ProvisionTenantRequest.forPersonal("test", "test@example.com");
        
        // When
        var result = service.provision(request);
        
        // Then
        assertThat(result.tenantType()).isEqualTo(TenantType.PERSONAL);
        assertThat(result.maxUsers()).isEqualTo(1);
    }
}
```

**Backend-Service:**
```java
@SpringBootTest
class TenantContextTest {
    
    @Test
    void shouldSetAndGetTenantId() {
        // Given
        String tenantId = "test-tenant";
        
        // When
        TenantContext.setTenantId(tenantId);
        
        // Then
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
        
        // Cleanup
        TenantContext.clear();
    }
}
```

### Integration Testing

**E2E Tenant Provisioning:**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TenantProvisioningIT {
    
    @Test
    void shouldProvisionTenantEndToEnd() {
        // 1. Call signup API
        var response = restTemplate.postForEntity(
            "/api/signup/personal",
            new PersonalSignupRequest("test@example.com", "Pass123!", "Test User"),
            SignupResponse.class
        );
        
        // 2. Verify tenant created
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tenantId = response.getBody().tenantId();
        
        // 3. Verify database exists
        assertThat(databaseExists("db_" + tenantId)).isTrue();
        
        // 4. Verify Cognito user
        var user = cognitoClient.adminGetUser(r -> r
            .userPoolId(userPoolId)
            .username("test@example.com")
        );
        
        assertThat(user.userAttributes())
            .anyMatch(attr -> attr.name().equals("custom:tenantId") 
                && attr.value().equals(tenantId));
    }
}
```

### Performance Testing

```bash
# Load test signup endpoint
ab -n 1000 -c 10 -p signup.json -T application/json \
  http://localhost:8081/api/signup/personal

# Load test with tenant isolation
ab -n 10000 -c 100 -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/entries
```

---

## Rollout Plan

### Phase 1: Development Environment
- Deploy to local Docker Compose
- Run full test suite
- Manual QA testing

### Phase 2: Staging Environment
- Deploy to AWS staging
- Create 10 test tenants (5 personal, 5 organization)
- Run E2E tests
- Performance testing
- Security audit

### Phase 3: Production Rollout
- Feature flag for new signup flows
- Gradual rollout: 10% → 50% → 100%
- Monitor metrics closely
- Rollback plan ready

### Monitoring Checklist
- [ ] Tenant provisioning success rate
- [ ] Database creation duration
- [ ] Migration execution time
- [ ] API latency per tenant
- [ ] Error rates
- [ ] Resource utilization

---

## Final Checklist

### Phase 1: Foundation ✅
- [ ] Database schema updated
- [ ] Platform-service enhanced
- [ ] Backend migration endpoint
- [ ] Auth signup controller
- [ ] All unit tests passing

### Phase 2: Runtime Isolation ✅
- [ ] Gateway tenant filter
- [ ] Backend tenant routing
- [ ] DataSource caching
- [ ] E2E tests passing

### Phase 3: Production Features ✅
- [ ] Rate limiting active
- [ ] Audit logging working
- [ ] Circuit breakers configured
- [ ] Metrics dashboard

### Phase 4: Advanced Features ✅
- [ ] Lifecycle management
- [ ] Health monitoring
- [ ] Tenant metrics
- [ ] Documentation complete

---

## Success Criteria

1. **Functional:**
   - B2C users can self-signup
   - B2B organizations can be created
   - Tenants are fully isolated
   - Migrations run automatically

2. **Performance:**
   - Provisioning completes in < 60 seconds
   - API latency < 200ms p95
   - Supports 1000+ tenants

3. **Security:**
   - No tenant data leakage
   - Rate limiting prevents abuse
   - Audit trail for compliance

4. **Reliability:**
   - 99.9% uptime
   - Circuit breakers prevent cascading failures
   - Graceful degradation

---

**🎉 Implementation Guide Complete!**

This guide provides a complete, production-ready roadmap for implementing multi-tenant B2B/B2C onboarding with SOLID principles, modern patterns, and enterprise features.

Start with Day 1 and follow sequentially. Each day builds on the previous, with clear deliverables and testing steps.

Good luck with your implementation!
