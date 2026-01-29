# Multi-Tenancy & Data Architecture

**Version:** 9.0 (Hybrid Isolation + Single Source of Truth Migrations)
**Last Updated:** 2026-01-29

This document explains the data isolation and migration strategy used in the SaaS Foundation.

---

## 🏢 Hybrid Isolation Strategy

| Tenant Type | Storage Mode | Database | Isolation |
|-------------|--------------|----------|-----------|
| **Organization** | `DATABASE` | Dedicated `t_{tenant_id}` | Physical (separate DB) |
| **Personal** | `SHARED` | Shared `personal_shared` | Logical (`tenant_id` column) |

**Benefits:**
- ✅ **Enterprise Security:** Orgs get dedicated databases
- ✅ **Cost Efficient:** Personal users share one database
- ✅ **Consistent Schema:** Same schema everywhere (with `tenant_id`)
- ✅ **Simple Migrations:** Single source of truth per service

---

## 🗄️ Database Architecture

### Platform Database (`cloud-infra`)
**Owner:** Platform Service + Payment Service
| Table | Purpose |
|-------|---------|
| `tenant` | Global tenant registry |
| `billing_account` | Stripe billing data |

### Personal Shared Database (`personal_shared`)
**Owner:** Auth Service + Backend Service
| Table | Service |
|-------|---------|
| `roles`, `permissions`, `users`, `user_roles` | Auth |
| `entries` | Backend |

All tables include `tenant_id` for logical isolation.

### Organization Databases (`t_{tenant_id}`)
Same schema as `personal_shared`, created on org signup.

---

## 🔄 Flyway Migration Strategy

### Single Source of Truth
Each service has ONE migration folder used for ALL databases:

| Service | Migration Path | History Table |
|---------|---------------|---------------|
| auth-service | `db/migration/` | `flyway_auth_history` |
| backend-service | `db/migration/` | `flyway_backend_history` |
| platform-service | `db/migration/` | `flyway_schema_history` |

### When Migrations Run

| Trigger | Database | Mechanism |
|---------|----------|-----------|
| **Service startup** | `personal_shared` | `FlywayConfig` bean |
| **Org provisioning** | `t_{tenant_id}` | `TenantInternalController` |

### Why Separate History Tables?
Multiple services run Flyway on the same `personal_shared` database. Separate history tables (`flyway_auth_history`, `flyway_backend_history`) prevent:
- Version conflicts between services
- Concurrency issues during startup
- Rollback confusion

---

## 🔌 Database Routing

### TenantDataSourceRouter
Routes requests to the correct database based on tenant's storage mode:

```java
protected Object determineCurrentLookupKey() {
    String tenantId = TenantContext.getCurrentTenant();
    TenantDbConfig config = tenantRegistry.get(tenantId);
    
    if (config.storageMode() == SHARED) {
        return PERSONAL_SHARED_KEY;  // Route to personal_shared
    }
    return tenantId;  // Route to dedicated DB
}
```

### Flow
1. Request with `X-Tenant-Id: abc123`
2. `TenantContextFilter` sets tenant in ThreadLocal
3. `TenantDataSourceRouter` determines target database
4. Connection established to correct DB

---

## 🚀 Provisioning Flow

### Personal Signup (SHARED mode)
1. Create tenant record in `cloud-infra.tenant` with `storage_mode=SHARED`
2. Database already exists (`personal_shared` created at deployment)
3. User starts using the app immediately

### Organization Signup (DATABASE mode)
1. Create tenant record in `cloud-infra.tenant` with `storage_mode=DATABASE`
2. Create physical database `t_{tenant_id}`
3. Create DB user with permissions
4. Trigger migrations:
   - `POST auth-service/internal/tenants/{id}/migrate`
   - `POST backend-service/internal/tenants/{id}/migrate`
5. Organization ready to use

---

## 📊 Tenant Tiers

| Tier | Max Users | Storage |
|------|-----------|---------|
| **STANDARD** | 50 | 10 GB |
| **PREMIUM** | 200 | 50 GB |
| **ENTERPRISE** | Unlimited | Unlimited |
