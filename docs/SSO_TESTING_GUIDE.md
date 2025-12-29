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

## 2. Setting Up Test IdP Accounts

### 2.1 Okta Developer Account

**Step 1: Create Account**
1. Go to https://developer.okta.com/signup/
2. Fill in: Work email, Name, Country
3. Activate via email, set password

**Step 2: Create Test Users**
1. Directory → People → Add Person
2. Create users:
   - `alice@yourcompany.com` (Group: Engineering)
   - `bob@yourcompany.com` (Group: Marketing)
   - `charlie@yourcompany.com` (Groups: Engineering, Marketing)

**Step 3: Create Groups**
1. Directory → Groups → Add Group
2. Create: `Engineering`, `Marketing`, `Admins`
3. Assign users to groups

**Step 4: Create SAML Application**
1. Applications → Create App Integration → SAML 2.0
2. Configure:
   - **Single Sign-On URL:** `https://<cognito-domain>/saml2/idpresponse`
   - **Audience URI (SP Entity ID):** `urn:amazon:cognito:sp:<user-pool-id>`
   - **Name ID format:** Email
3. Attribute Statements:
   | Name | Value |
   |------|-------|
   | email | user.email |
   | name | user.displayName |
4. Group Attribute Statements:
   | Name | Filter | Value |
   |------|--------|-------|
   | groups | Matches regex | .* |

**Step 5: Get Metadata URL**
- Applications → Your App → Sign On → "View SAML setup instructions"
- Copy: `https://dev-XXXXX.okta.com/app/YYYYYY/sso/saml/metadata`

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
