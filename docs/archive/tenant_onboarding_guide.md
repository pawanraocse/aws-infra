# Tenant Provisioning Strategy - Hybrid B2B + B2C Model

---

## Overview

Support **TWO MODELS** in the same platform:

- **B2C:** Individual users with personal tenants (self-service)
- **B2B:** Enterprise customers with shared tenant (admin-controlled)

---

## Architecture: Two-Tier Tenant Model

```
┌─────────────────────────────────────────┐
│         TENANT CREATION                 │
├─────────────────────────────────────────┤
│                                         │
│  B2C Path              B2B Path         │
│  ↓                     ↓                │
│  Self-Service          Admin-Created    │
│  Auto-provision        Manual           │
│  1 user/tenant         Many users/tenant│
│                                         │
└─────────────────────────────────────────┘
```

---

## Tenant Types

### Type 1: Personal Tenant (B2C)

**Characteristics:**
- Created automatically on signup
- One user per tenant
- TenantId = user identifier
- Self-service onboarding

**Example:**
```json
{
  "id": "user_john_doe_x7k9",
  "name": "John Doe's Workspace",
  "type": "PERSONAL",
  "userCount": 1,
  "slaTier": "FREE"
}
```

### Type 2: Organization Tenant (B2B)

**Characteristics:**
- Created by admin only
- Multiple users per tenant
- TenantId = organization identifier
- Controlled onboarding

**Example:**
```json
{
  "id": "acme-corp",
  "name": "Acme Corporation",
  "type": "ORGANIZATION",
  "userCount": 50,
  "slaTier": "ENTERPRISE"
}
```

---

## User-to-Tenant Relationship

### B2C: One-to-One
```
User john@gmail.com → Tenant "user_john_x7k9"
```

### B2B: Many-to-One
```
User admin@acme.com  ↘
User user1@acme.com   → Tenant "acme-corp"
User user2@acme.com  ↗
```

---

## Flow 1: B2C Self-Service Signup

### Steps

**1. User Signs Up**
```
POST /auth/signup
{
  "email": "john@gmail.com",
  "password": "SecurePass123!",
  "accountType": "PERSONAL"
}
```

**2. Cognito Creates User**
- User status: UNCONFIRMED
- Custom attributes need to be set

**3. User Confirms Email**
- User clicks verification link
- Status changes to CONFIRMED

**4. Post-Confirmation Lambda Triggers**
```javascript
// Auto-generate unique tenant for B2C user
const tenantId = `user_${Date.now()}_${randomString()}`;

await createTenant({
  id: tenantId,
  name: `${userEmail}'s Workspace`,
  type: 'PERSONAL',
  slaTier: 'FREE'
});

await updateUserAttribute({
  'custom:tenantId': tenantId,
  'custom:tenantType': 'PERSONAL'
});
```

**5. User Logs In**
- JWT contains tenantId
- User accesses their own isolated tenant

---

## Flow 2: B2B Admin-Controlled

### Steps

**1. Admin Creates Organization Tenant**
```bash
# Admin API call (authenticated with super-admin role)
curl -X POST http://localhost:8083/platform/api/tenants \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -d '{
    "id": "acme-corp",
    "name": "Acme Corporation",
    "type": "ORGANIZATION",
    "slaTier": "ENTERPRISE"
  }'
```

**2. Admin Creates Organization Users**
```bash
# Admin creates users with pre-set tenantId
aws cognito-idp admin-create-user \
  --user-pool-id us-east-1_6RGxkqTmA \
  --username admin@acme.com \
  --user-attributes \
    Name=custom:tenantId,Value=acme-corp \
    Name=custom:tenantType,Value=ORGANIZATION \
    Name=custom:role,Value=tenant-admin \
  --profile personal
```

**3. Users Receive Invitation**
- Email with credentials
- Temporary password
- Forced password change on first login

**4. Users Log In**
- All users share same tenant: `acme-corp`
- Collaboration enabled

---

## Decision Matrix: How to Route User

### Signup Form

```html
<form>
  <input type="email" name="email" />
  <input type="password" name="password" />
  
  <!-- User chooses account type -->
  <select name="accountType">
    <option value="PERSONAL">Personal Account (Free)</option>
    <option value="ORGANIZATION">Business Account</option>
  </select>
</form>
```

### Backend Logic

```javascript
if (accountType === 'PERSONAL') {
  // B2C Flow
  // Allow self-service signup
  // Auto-create tenant
  // User = single owner
  
} else if (accountType === 'ORGANIZATION') {
  // B2B Flow
  // Require admin approval
  // OR send to sales team
  // Admin creates tenant manually
  // Admin invites users
}
```

---

## Lambda Logic: Smart Tenant Creation

### Post-Confirmation Lambda (Updated)

```javascript
export const handler = async (event) => {
  const { userAttributes } = event;
  const email = userAttributes.email;
  
  // Check if user already has tenantId (B2B - set by admin)
  const existingTenantId = userAttributes['custom:tenantId'];
  
  if (existingTenantId) {
    // B2B user - tenant already exists
    console.log(`User ${email} assigned to existing tenant: ${existingTenantId}`);
    // Verify tenant exists
    const tenant = await fetchTenant(existingTenantId);
    if (!tenant) {
      throw new Error(`Tenant ${existingTenantId} does not exist`);
    }
    return event;
  }
  
  // B2C user - create new personal tenant
  const tenantId = `user_${Date.now()}_${generateId()}`;
  
  await createTenant({
    id: tenantId,
    name: `${email}'s Workspace`,
    type: 'PERSONAL',
    slaTier: 'FREE',
    storageMode: 'DATABASE'
  });
  
  // Update user with new tenantId
  await updateCognitoUser(email, {
    'custom:tenantId': tenantId,
    'custom:tenantType': 'PERSONAL'
  });
  
  console.log(`Created personal tenant for ${email}: ${tenantId}`);
  return event;
};
```

---

## User Attributes Schema

```javascript
{
  "email": "user@example.com",
  "custom:tenantId": "user_123_x7k9",        // Required
  "custom:tenantType": "PERSONAL",           // PERSONAL | ORGANIZATION
  "custom:role": "owner",                    // owner | tenant-admin | user
  "custom:maxTenants": "1"                   // B2C=1, B2B admin=unlimited
}
```

---

## Security Rules

### B2C Users (Personal Tenants)

✅ **Can:**
- Access only their tenant
- Modify their own data
- Delete their account (and tenant)

❌ **Cannot:**
- Create additional tenants
- Invite other users
- Access other tenants

### B2B Tenant Admins

✅ **Can:**
- Invite users to their organization tenant
- Manage users in their tenant
- Configure tenant settings

❌ **Cannot:**
- Create new organization tenants
- Access other organization tenants
- Change tenant type

### Super Admins

✅ **Can:**
- Create organization tenants
- Assign users to any tenant
- View all tenants
- Configure global settings

---

## Database Schema Updates

### Tenant Table
```sql
ALTER TABLE tenant ADD COLUMN tenant_type VARCHAR(32) DEFAULT 'PERSONAL';
ALTER TABLE tenant ADD COLUMN owner_email VARCHAR(255);
ALTER TABLE tenant ADD COLUMN max_users INTEGER DEFAULT 1;

-- B2C tenant
INSERT INTO tenant (id, name, tenant_type, max_users, sla_tier)
VALUES ('user_john_x7k9', 'John''s Workspace', 'PERSONAL', 1, 'FREE');

-- B2B tenant
INSERT INTO tenant (id, name, tenant_type, max_users, sla_tier)
VALUES ('acme-corp', 'Acme Corporation', 'ORGANIZATION', 100, 'ENTERPRISE');
```

---

## Pricing Tiers

| Type | Free | Standard | Enterprise |
|------|------|----------|------------|
| **B2C** | 1 user | N/A | N/A |
| **B2B** | 5 users | 50 users | Unlimited |
| **Storage** | 1GB | 50GB | 500GB |
| **Support** | Community | Email | 24/7 Phone |

---

## Implementation Steps

### Phase 1: B2C (Immediate)
1. Update Lambda to auto-create personal tenants
2. Add `custom:tenantType` attribute
3. Allow self-service signup for PERSONAL accounts
4. Test end-to-end B2C flow

### Phase 2: B2B (Near-term)
1. Add admin API for organization tenant creation
2. Add user invitation flow
3. Implement tenant-admin role
4. Test multi-user B2B flow

### Phase 3: Hybrid (Future)
1. Allow B2C→B2B upgrade (convert personal to org)
2. User can invite team members (upgrade prompt)
3. Billing integration for paid plans

---

## Migration Path: Personal → Organization

**Use Case:** B2C user wants to invite team

```javascript
// Upgrade flow
POST /api/tenants/{tenantId}/upgrade

{
  "newType": "ORGANIZATION",
  "newName": "John's Company",
  "plan": "STANDARD"
}

// Backend:
// 1. Verify user is owner
// 2. Update tenant type
// 3. Increase max_users limit
// 4. Enable invitation feature
// 5. Return success
```

---

## Summary

| Aspect | B2C Personal | B2B Organization |
|--------|--------------|------------------|
| **Signup** | Self-service ✅ | Admin-created ✅ |
| **Tenant Creation** | Automatic | Manual |
| **Users per Tenant** | 1 | Many |
| **Tenant Pattern** | `user_<id>` | `company-name` |
| **SLA Tier** | FREE | STANDARD/ENTERPRISE |
| **Invitation** | ❌ Disabled | ✅ Enabled |
| **Upgrade Path** | To Organization | To higher tier |

**Both models coexist** in the same platform with different onboarding flows!
