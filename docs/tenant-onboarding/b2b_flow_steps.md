# B2B Tenant Onboarding Flow - Step by Step

---

## The Complete B2B Flow

```
Sales → Admin Action → System Automation → Customer Access
```

---

## Step-by-Step Process

### **Step 1: Sales Closes Deal with Enterprise Customer**
👤 **Actor:** Sales Team  
📋 **Action:** Create ticket/request for new tenant  
📝 **Details:** Company name, admin email, SLA tier  

---

### **Step 2: Platform Admin Creates Tenant**
👤 **Actor:** Platform Admin (Super Admin)  
📋 **Action:** Call Platform Service API or run script

```bash
POST /platform/api/tenants
{
  "id": "acme-corp",
  "name": "Acme Corporation",
  "type": "ORGANIZATION",
  "slaTier": "ENTERPRISE"
}
```

**What happens internally:**
- ✅ Platform-service creates tenant row in database
- ✅ Platform-service creates PostgreSQL database: `db_acme-corp`
- ✅ Platform-service runs **Flyway migrations** on `db_acme-corp`
  - Creates tables for **backend-service**
  - Creates tables for **all other services**
- ✅ Platform-service returns success

**Status:** Tenant is now `ACTIVE` and ready for users

---

### **Step 3: Platform Admin Creates Tenant Admin User**
👤 **Actor:** Platform Admin (Super Admin)  
📋 **Action:** Create Cognito user with tenantId

```bash
aws cognito-idp admin-create-user \
  --username admin@acme.com \
  --user-attributes \
    custom:tenantId=acme-corp \
    custom:role=tenant-admin \
  --temporary-password "TempPass123!"
```

**What happens:**
- ✅ Cognito creates user account
- ✅ User gets `custom:tenantId = "acme-corp"` (FIXED, cannot change)
- ✅ User gets `custom:role = "tenant-admin"`
- ✅ User status: FORCE_CHANGE_PASSWORD

---

### **Step 4: Admin Sends Credentials to Customer**
👤 **Actor:** Platform Admin  
📋 **Action:** Email credentials to customer

```
Email Template:
--------------
Welcome to Our Platform!

Your organization: Acme Corporation
Login URL: https://app.example.com/login
Username: admin@acme.com
Temporary Password: TempPass123!

You will be required to change your password on first login.
```

---

### **Step 5: Customer Admin First Login**
👤 **Actor:** Customer (Tenant Admin)  
📋 **Action:** Login and change password

1. Navigate to login page
2. Enter username + temp password
3. Forced to create new password
4. Login successful
5. JWT token contains: `"tenantId": "acme-corp"`

---

### **Step 6: Tenant Admin Invites Team Members**
👤 **Actor:** Customer (Tenant Admin)  
📋 **Action:** Invite users to organization

**Option A: Via platform UI**
```
Dashboard → Users → Invite User
Email: user1@acme.com
Role: user
```

**Option B: Admin creates more users**
```bash
aws cognito-idp admin-create-user \
  --username user1@acme.com \
  --user-attributes custom:tenantId=acme-corp
```

**What happens:**
- ✅ New users get same `tenantId = "acme-corp"`
- ✅ All users share same tenant database
- ✅ Collaboration enabled

---

### **Step 7: Team Members Login**
👤 **Actor:** Customer Team Members  
📋 **Action:** Login with credentials

- Users receive invitation email
- Login with temp password
- Change password
- Access shared tenant: `acme-corp`

---

## Who Does What?

| Action | Actor | System Component |
|--------|-------|------------------|
| **Create Tenant** | Platform Admin | Platform-service API |
| **Create Database** | Automatic | Platform-service (TenantProvisioner) |
| **Run Flyway Migrations** | Automatic | Platform-service (during tenant creation) |
| **Create Admin User** | Platform Admin | AWS Cognito (admin-create-user) |
| **Invite Team Members** | Tenant Admin OR Platform Admin | Cognito |
| **Assign tenantId** | Automatic | Set during user creation (immutable) |

---

## Timeline

```
Day 0: Sales closes deal
  ↓
Day 1: Admin creates tenant (5 min)
       Database + migrations automatic
  ↓
Day 1: Admin creates first user (2 min)
  ↓  
Day 1: Email sent to customer (instant)
  ↓
Day 2: Customer logs in
  ↓
Day 2: Customer invites team
  ↓
Day 3: Team starts using platform
```

**Total setup time:** ~10 minutes of admin work

---

## Critical Points

### ✅ Tenant Creation (Step 2)
- **WHO:** Platform Admin manually
- **WHEN:** After sales approval
- **RESULT:** Database created, migrations run, tenant ACTIVE

### ✅ Flyway Migrations (Step 2 - automatic)
- **WHO:** Platform-service automatically
- **WHEN:** During tenant creation
- **WHAT:** Runs migrations for backend-service and all other services
- **WHERE:** On new tenant database `db_acme-corp`

### ✅ Admin User Creation (Step 3)
- **WHO:** Platform Admin manually
- **WHEN:** Immediately after tenant creation
- **HOW:** Direct Cognito API call

---

## Key Insight

**Everything after Step 2 is automatic:**

```
Admin calls API once
  ↓
Platform-service:
  1. Creates tenant metadata
  2. Creates PostgreSQL database
  3. Runs ALL Flyway migrations ✅
  4. Returns success
  
Done! Tenant is ready for users.
```

**Admin only needs to:**
1. Call tenant creation API (Step 2)
2. Create first admin user in Cognito (Step 3)
3. Send credentials to customer (Step 4)

**Everything else is automated!**

---

## Example: Complete Flow in Commands

```bash
# Admin creates tenant
curl -X POST http://platform-service:8083/platform/api/tenants \
  -d '{"id":"acme-corp","name":"Acme Corporation","storageMode":"DATABASE","slaTier":"ENTERPRISE"}'

# Platform-service automatically:
# - Creates db_acme-corp
# - Runs Flyway migrations for all services
# - Returns 201 Created

# Admin creates first user
aws cognito-idp admin-create-user \
  --user-pool-id us-east-1_6RGxkqTmA \
  --username admin@acme.com \
  --user-attributes Name=custom:tenantId,Value=acme-corp

# Done! Customer can now login and invite team.
```

---

## FAQ

**Q: When are Flyway migrations run?**  
A: Automatically during tenant creation (Step 2) by platform-service

**Q: Who creates the tenant database?**  
A: Platform-service automatically when tenant is created

**Q: Can users create their own tenant in B2B?**  
A: No, only Platform Admin can create organization tenants

**Q: Can tenant admin invite users?**  
A: Yes, but they share the same tenant (no new tenant created)

**Q: What if tenant creation fails?**  
A: Tenant status = PROVISION_ERROR, admin can retry via API
