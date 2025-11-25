# Tenant Onboarding Implementation Guide - Unified B2B & B2C

---

## Architecture: Unified Backend Process

**Key Insight:** Both B2B and B2C use the **same tenant creation API**, just with different parameters.

```
UI Signup Request → Backend Validation → Tenant Creation API → User Creation → Done
```

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

#### Step 1.1: Update Tenant Entity

**File:** `platform-service/src/main/java/com/learning/platformservice/tenant/entity/Tenant.java`

**Add fields:**
```java
@Column(name = "tenant_type", nullable = false)
private String tenantType; // "PERSONAL" or "ORGANIZATION"

@Column(name = "owner_email")
private String ownerEmail; // For B2C - the user's email

@Column(name = "max_users", nullable = false)
private Integer maxUsers = 1; // B2C=1, B2B=unlimited or tier-based
```

#### Step 1.2: Update Flyway Migration

**File:** `platform-service/src/main/resources/db/migration/V2__add_tenant_type.sql`

```sql
ALTER TABLE tenant ADD COLUMN tenant_type VARCHAR(32) NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE tenant ADD COLUMN owner_email VARCHAR(255);
ALTER TABLE tenant ADD COLUMN max_users INTEGER NOT NULL DEFAULT 1;

CREATE INDEX idx_tenant_type ON tenant(tenant_type);
CREATE INDEX idx_tenant_owner ON tenant(owner_email);
```

#### Step 1.3: Update Tenant DTO

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
