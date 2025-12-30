# SSO Testing Guide: Complete Manual Testing Procedures

**Version:** 1.1  
**Date:** 2025-12-29  
**Purpose:** Step-by-step guide for testing all SSO login options

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Setting Up Test IdP Accounts](#2-setting-up-test-idp-accounts)
3. [Test Scenarios](#3-test-scenarios)
4. [Test Cases & Edge Cases](#4-test-cases--edge-cases)
5. [Troubleshooting](#5-troubleshooting)
6. [Quick Reference](#6-quick-reference)

---

## 1. Prerequisites

### 1.1 Running Infrastructure

```bash
# Start all services
cd /Users/pawan.yadav/prototype/AWS-Infra

# Backend services
mvn spring-boot:run -pl eureka-server &
mvn spring-boot:run -pl gateway-service &
mvn spring-boot:run -pl platform-service &
mvn spring-boot:run -pl auth-service &

# Frontend
cd frontend && npm run dev
```

### 1.2 Service Health Verification

```bash
# Verify platform-service
curl http://localhost:8083/platform/actuator/health

# Verify auth-service  
curl http://localhost:8081/actuator/health
```

### 1.3 Required IdP Accounts (All FREE)

| Provider | Type | Signup URL |
|----------|------|------------|
| **Okta** | Developer | https://developer.okta.com/signup/ |
| **Azure AD** | Free/Trial | https://azure.microsoft.com/free/ |
| **Google Workspace** | Developer | https://workspace.google.com/essentials/ |
| **Ping Identity** | Developer | https://www.pingidentity.com/developer |

### 1.4 Cognito Information

```bash
# Get from Terraform outputs
cd terraform
terraform output cognito_user_pool_id    # e.g., us-east-1_aBcDeFgHi
terraform output cognito_region          # e.g., us-east-1
terraform output cognito_domain          # e.g., your-domain.auth.us-east-1.amazoncognito.com
```

### 1.5 Test Organization Setup

1. Login as platform admin
2. Navigate to Admin → Tenants
3. Create "SSO Test Org" with Enterprise tier

---

## 2. Complete Okta SSO Setup Guide

This section covers the complete Okta setup end-to-end.

---

### 2.1 Create Okta Developer Account

1. Go to https://developer.okta.com/signup/
2. Fill in: Work email, Name, Country
3. Activate via email, set password
4. Note your Okta domain: `https://dev-XXXXX.okta.com`

---

### 2.2 Create Test Users in Okta

1. **Directory → People → Add Person**
2. Create test users with emails (e.g., `testuser@yourcompany.com`)
3. Optionally assign to groups for role mapping

---

### 2.3 Create SAML Application in Okta

1. **Applications → Create App Integration → SAML 2.0**
2. Give it a name (e.g., "SaaS Platform SSO")
3. Configure SAML settings:

   | Setting | Value |
   |---------|-------|
   | **Single Sign-On URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **Audience URI (SP Entity ID)** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **Name ID format** | EmailAddress |

4. **Attribute Statements:**
   | Name | Value |
   |------|-------|
   | `email` | `user.email` |
   | `name` | `user.displayName` |

5. Click **Next** → Select "I'm an Okta customer" → **Finish**

---

### 2.4 Get Okta Metadata URL

1. **Applications → Your App → Sign On** tab
2. Scroll to "SAML 2.0" section → **Metadata URL**
3. Copy the URL (format: `https://dev-XXXXX.okta.com/app/YYYYYY/sso/saml/metadata`)

---

### 2.5 Option A: Set Up in Our App (Recommended)

1. Login as tenant admin
2. Navigate to **Settings → SSO Configuration**
3. Select **Okta** as provider
4. Paste the **Metadata URL** from step 2.4
5. Click **Save Configuration** → **Enable SSO**

**To Remove:** Click **Remove Configuration** (cleans up Cognito and database)

---

### 2.6 Option B: Set Up Directly in Cognito Console

**Step 1: Add Identity Provider**
1. AWS Console → Cognito → User Pools → Your Pool
2. **Social and external providers** → **Add identity provider** → **SAML**
3. Configure:
   - **Provider name:** `OKTA-{tenantId}` (e.g., `OKTA-aarohan`)
   - **Metadata document URL:** Paste URL from step 2.4
   - **SAML signing:** Leave unchecked for testing
   - **IdP-initiated:** Select "Require SP-initiated" (recommended)
4. **Map attributes:**
   | User pool attribute | SAML attribute |
   |---------------------|----------------|
   | `email` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |
5. Click **Add identity provider**

**Step 2: Enable for App Client**
1. **App integration** → Your app client → **Hosted UI** → Edit
2. Under **Identity providers**, check your SAML provider
3. Verify callback URL: `http://localhost:4200/auth/callback`
4. Save changes

**Step 3: Test SSO**
- Click **Login with SSO** → Enter org name → Okta login → Returns to app

---

### 2.7 Okta Cognito Values Quick Reference

| What You Need | Where to Find It |
|---------------|------------------|
| **Metadata URL** | Okta → Applications → Your App → Sign On → Metadata URL |
| **Cognito ACS URL** | `https://<domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
| **Cognito Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
| **Cognito Domain** | AWS Console → Cognito → Your Pool → App integration → Domain |
| **User Pool ID** | AWS Console → Cognito → Your Pool → User pool overview |

> **⚠️ IMPORTANT: Provider Name Case Sensitivity**
> 
> Cognito provider names are **case-sensitive**. The provider name in Cognito must **exactly match** what the frontend constructs.
> - Frontend expects: `OKTA-{tenantId}` (uppercase OKTA)
> - If you create `OkTA-aarohan` instead of `OKTA-aarohan`, SSO will fail with "Login pages unavailable"
> - Always use **UPPERCASE** for the IdP type: `OKTA-`, `AZURE-`, `PING-`

---

### 2.2 Azure AD Setup

**Step 1: Register Enterprise Application**
1. Azure Portal → Azure Active Directory → Enterprise applications → New
2. Create your own → "SaaS Platform Test"

**Step 2: Configure SAML SSO**
1. Single sign-on → SAML
2. Basic SAML Configuration:
   - **Identifier (Entity ID):** `urn:amazon:cognito:sp:<user-pool-id>`
   - **Reply URL:** `https://<cognito-domain>/saml2/idpresponse`
3. User Attributes & Claims: email, name, groups

**Step 3: Download Metadata**
- SAML Signing Certificate → Download Federation Metadata XML

---

### 2.3 Google Workspace (OIDC)

**Step 1: Google Cloud Console**
1. https://console.cloud.google.com → Create project

**Step 2: OAuth Consent Screen**
1. APIs & Services → OAuth consent screen
2. User Type: External, Scopes: email, profile, openid

**Step 3: Create OAuth Credentials**
1. Credentials → Create Credentials → OAuth client ID
2. Web application, name: "Cognito SSO"
3. Redirect URI: `https://<cognito-domain>/oauth2/idpresponse`
4. Save **Client ID** and **Client Secret**

---

### 2.4 Ping Identity Setup

**Step 1:** Create PingOne account at https://www.pingidentity.com/en/try-ping.html

**Step 2:** Applications → Add SAML Application
- ACS URL: `https://<cognito-domain>/saml2/idpresponse`
- Entity ID: `urn:amazon:cognito:sp:<user-pool-id>`

**Step 3:** Configure attributes: email, name, memberOf

**Step 4:** Download SAML Metadata

---

## 3. Test Scenarios

### Scenario 1: Standard Email/Password Login

**Purpose:** Verify baseline authentication

| Step | Action | Expected |
|------|--------|----------|
| 1 | Navigate to `http://localhost:4200/auth/login` | Login page shown |
| 2 | Enter email/password | - |
| 3 | Click "Sign In" | Redirected to `/app/dashboard` |

---

### Scenario 2: Google OIDC Login

**Configure in UI:**
1. Navigate to `/app/admin/settings/sso`
2. Select "Google Workspace"
3. Enter Client ID, Client Secret, Issuer URL: `https://accounts.google.com`
4. Save and Test Connection

**Test:**
| Step | Action | Expected |
|------|--------|----------|
| 1 | Open incognito window | - |
| 2 | Navigate to login | Login page |
| 3 | Click "Sign in with Google" | Redirect to Google |
| 4 | Enter Google credentials | Consent screen |
| 5 | Click Allow | Redirect to dashboard |

**Verify:**
```sql
SELECT * FROM users WHERE email = 'user@google-domain.com';
SELECT * FROM user_tenant_membership WHERE user_email = 'user@google-domain.com';
```

---

### Scenario 3: Azure AD OIDC Login

**Configure in UI:**
1. Select "Microsoft Azure AD"
2. Enter Client ID, Client Secret
3. Issuer URL: `https://login.microsoftonline.com/{tenant-id}/v2.0`

**Test:**
| Step | Action | Expected |
|------|--------|----------|
| 1 | Click "Sign in with Microsoft" | Redirect to Azure |
| 2 | Enter credentials + MFA | Authentication |
| 3 | Consent (first time) | Approval |
| 4 | Redirect back | Dashboard |

---

### Scenario 4: Okta SAML Login

**Configure in UI:**
1. Select "Okta"
2. Enter IdP Metadata URL
3. Save and Test

**Test:**
| Step | Action | Expected |
|------|--------|----------|
| 1 | Click "Sign in with SSO" | Redirect to Okta |
| 2 | Enter Okta credentials | Authentication |
| 3 | Redirect back | Dashboard with groups |

**Verify:**
```sql
SELECT * FROM idp_groups WHERE tenant_id = 'your-tenant-id';
SELECT * FROM group_role_mappings;
```

---

### Scenario 5: Ping Identity SAML Login

**Configure:** Upload metadata XML, set groups claim to `memberOf`

**Test:** Similar flow to Okta

---

### Scenario 6: Group-to-Role Mapping

**Setup:**
1. Navigate to `/app/admin/settings/group-mapping`
2. Add mappings:

| External Group ID | Group Name | Role | Priority |
|-------------------|------------|------|----------|
| `cn=Admins,ou=Groups,dc=company` | Admins | Admin | 100 |
| `Engineering` | Engineering | Editor | 50 |
| `cn=Viewers,ou=Groups,dc=company` | Viewers | Viewer | 10 |

**Test:**
1. Login with user in "Engineering" → should get Editor role
2. Login with user in both "Admins" + "Engineering" → should get Admin (higher priority)

---

### Scenario 7: JIT Provisioning

**Setup:** Delete test user if exists

**Test:**
1. Login with new SSO user
2. Verify user created in DB
3. Check Lambda logs:

```
INFO: User newuser@company.com already exists: false
INFO: Resolved role 'viewer' for groups: ['Engineering']
INFO: Successfully JIT provisioned SSO user: email=newuser@company.com
```

---

### Scenario 8: SSO Toggle

**Test:**
1. Toggle SSO OFF in settings
2. Try SSO login → should fail
3. Toggle ON → login works again

---

### Scenario 9: Test Connection Validation

**Test:**
1. Configure with invalid client secret
2. Click "Test Connection"
3. Verify error message: "Invalid client credentials"

---

## 4. Test Cases & Edge Cases

### Core Functionality Tests

| ID | Test Case | Priority |
|----|-----------|----------|
| SSO-001 | First SSO Login (JIT Provision) | HIGH |
| SSO-002 | Returning SSO User | HIGH |
| SSO-003 | Group-to-Role Mapping | HIGH |
| SSO-004 | Multiple Groups (priority) | HIGH |
| SSO-005 | No Group Membership | MEDIUM |
| SSO-006 | Unknown Groups | MEDIUM |
| SSO-007 | SSO Disabled Mid-Session | MEDIUM |
| SSO-008 | Invalid SAML Metadata | HIGH |
| SSO-009 | Expired OIDC Secret | MEDIUM |
| SSO-010 | Email Case Sensitivity | MEDIUM |

### Security Tests

| ID | Test Case | Priority |
|----|-----------|----------|
| SEC-001 | SAML Signature Validation | CRITICAL |
| SEC-002 | OIDC Token Validation | CRITICAL |
| SEC-003 | Replay Attack Prevention | HIGH |
| SEC-004 | IdP Impersonation | HIGH |
| SEC-005 | Cross-Tenant Access | CRITICAL |

### Performance Tests

| ID | Test Case | Expected |
|----|-----------|----------|
| PERF-001 | Group Sync (50+ groups) | < 3s |
| PERF-002 | Concurrent SSO Logins (10) | All succeed |
| PERF-003 | Large Metadata Validation | < 2s |

---

## 5. Troubleshooting

### Common Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| "Invalid SAML Response" | Wrong ACS URL | Check Cognito domain in IdP |
| No groups in JWT | Groups claim not mapped | Add groups attribute in IdP |
| User not created | JIT provisioning error | Check Lambda logs |
| Wrong role assigned | Mapping priority | Check priority values |
| "Invalid redirect_uri" | URL mismatch | Verify exact URL in IdP |

### Debugging Commands

```bash
# Check Cognito Identity Providers
aws cognito-idp list-identity-providers --user-pool-id us-east-1_xxxxxxxx

# View Cognito user
aws cognito-idp admin-get-user --user-pool-id us-east-1_xxxxxxxx --username user@example.com

# Check Lambda logs
aws logs filter-log-events \
  --log-group-name /aws/lambda/cognito-pre-token-generation \
  --filter-pattern "ERROR"
```

### Log Locations

| Component | Location |
|-----------|----------|
| PreTokenGeneration Lambda | CloudWatch: `/aws/lambda/cognito-pre-token-generation` |
| Platform Service | `docker logs platform-service` |
| Auth Service | `docker logs auth-service` |

---

## 6. Quick Reference

### Cognito URLs

| URL Type | Format |
|----------|--------|
| **SAML ACS URL** | `https://<domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
| **SP Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
| **OIDC Redirect URI** | `https://<domain>.auth.<region>.amazoncognito.com/oauth2/idpresponse` |

### API Endpoints

```
GET  /api/v1/sso/config         - Get config
POST /api/v1/sso/config/saml    - Save SAML
POST /api/v1/sso/config/oidc    - Save OIDC
POST /api/v1/sso/toggle         - Enable/disable
POST /api/v1/sso/test           - Test connection
```

### Frontend Routes

```
/app/admin/settings/sso           - SSO configuration
/app/admin/settings/group-mapping - Group role mappings
```

---

## 7. Local Development Testing

### 7.1 Local Environment Setup

For testing SSO locally (Lambda can't reach local services), client-side JIT provisioning handles user creation.

```bash
# Start all services with Docker Compose
docker-compose up -d

# Verify all services are healthy
docker ps

# Frontend with hot-reload
cd frontend && npm run start
```

**Important:** Ensure `environment.ts` has:
```typescript
production: false  // Enables client-side JIT for local dev
```

### 7.2 Okta Local Testing Flow

| Step | Action | What Happens |
|------|--------|--------------|
| 1 | Click "Login with SSO" | Redirects to Cognito Hosted UI |
| 2 | Cognito redirects to Okta | User authenticates with Okta |
| 3 | Okta → Cognito → Frontend callback | `callback.component.ts` handles token |
| 4 | Frontend calls JIT provision API | Creates user membership in platform-service |
| 5 | Frontend calls `/me` API | Gets email, role from auth-service |
| 6 | User lands on dashboard | Profile shows email, tenant, role |

### 7.3 Verifying Local SSO

**Check JWT Claims:**
- Open browser DevTools → Application → Local Storage → `CognitoIdentityServiceProvider.*`
- Decode ID token at https://jwt.io
- Verify:
  - `custom:tenantId` is present
  - `identities[0].userId` contains email
  - `cognito:groups` contains tenant group

**Check API Response:**
```bash
# After SSO login, call /me endpoint
curl -H "Authorization: Bearer <id-token>" \
  http://localhost:8080/auth/api/v1/auth/me

# Expected response:
{
  "userId": "uuid",
  "email": "user@company.com",
  "role": "viewer",
  "name": ""
}
```

### 7.4 Database Verification

```bash
# Check user membership was created
docker exec postgres psql -U postgres -d platform_db -c \
  "SELECT * FROM user_tenant_memberships WHERE email = 'user@company.com';"

# Check tenant has SSO enabled
docker exec postgres psql -U postgres -d platform_db -c \
  "SELECT tenant_id, sso_enabled, sso_provider, cognito_provider_name FROM tenants;"
```

---

## 8. Additional Test Scenarios (Local Dev)

### Scenario 10: Admin Password Login with SSO Enabled

**Purpose:** Ensure admin users can login with password even when tenant has SSO configured.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Enter admin email | - |
| 2 | Click continue | Goes to password step (NOT SSO redirect) |
| 3 | Enter password | Successful login |

> **Note:** SSO redirect only happens via the "Login with SSO" button, never via email/password flow.

---

### Scenario 11: Default Role Without Group Mapping

**Purpose:** Verify SSO users get default role when no group mappings exist.

| Step | Action | Expected |
|------|--------|----------|
| 1 | Configure SSO (no group mappings) | - |
| 2 | Login with SSO | User created |
| 3 | Check profile | Role = "viewer" (default) |
| 4 | Add group mapping for user's group | - |
| 5 | Re-login with SSO | Role = mapped role |

---

### Scenario 12: Email Extraction from identities

**Purpose:** Verify email is correctly extracted from JWT for SSO users.

**Check Points:**
1. JWT has `identities[0].userId` with email
2. Gateway extracts email → `X-Email` header
3. `/me` API returns correct email
4. UI displays email in profile

---

## 9. Key Learnings & Gotchas

### 9.1 JWT Claim Differences (SSO vs Password)

| Claim | Password User | SSO User |
|-------|--------------|----------|
| `email` | ✅ Present | ❌ Not present |
| `identities` | ❌ Not present | ✅ Array with userId |
| `cognito:username` | email | `{idp}_{email}` |
| `custom:tenantId` | ✅ Present | ✅ Present (from Lambda) |

### 9.2 Service Dependencies

```
Gateway → Auth-Service → (role lookup)
      ↘ Platform-Service → (JIT provision, tenant info)
      
Lambda → Platform-Service → (in production, not local)
```

### 9.3 Common Pitfalls

| Issue | Cause | Fix |
|-------|-------|-----|
| 403 after SSO login | No role mapping + no default role | Added default 'viewer' role |
| Missing email in profile | `identities` is array, not string | Parse `identities[0].userId` |
| Auto-redirect to SSO | `selectTenant()` checked ssoEnabled | Removed auto-redirect |
| Stale service connection | Container IP changed after restart | Restart dependent services |
| Lambda can't reach local services | Lambda in AWS, services in Docker | Use client-side JIT for local dev |

---

## 10. Production Checklist

Before deploying to production:

- [ ] `environment.production = true` (disables client-side JIT)
- [ ] Lambda VPC configured to reach platform-service
- [ ] Group mappings configured for all expected IdP groups
- [ ] SAML/OIDC metadata URLs are production endpoints
- [ ] Cognito Hosted UI callback URLs include production domain
- [ ] Log levels set to INFO (not DEBUG)




