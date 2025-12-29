# SSO Testing Guide: Manual Testing Procedures

**Version:** 1.0  
**Date:** 2025-12-29  
**Purpose:** Step-by-step guide for testing all SSO login options

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Setting Up Test IdP Accounts](#2-setting-up-test-idp-accounts)
3. [Google OIDC Testing](#3-google-oidc-testing)
4. [Okta SAML Testing](#4-okta-saml-testing)
5. [Azure AD Testing](#5-azure-ad-testing)
6. [Ping Identity Testing](#6-ping-identity-testing)
7. [Test Cases & Edge Cases](#7-test-cases--edge-cases)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Prerequisites

### Required Accounts
| Provider | Type | Cost | Signup URL |
|----------|------|------|------------|
| **Okta** | Developer | FREE | https://developer.okta.com/signup/ |
| **Azure AD** | Free/Trial | FREE | https://azure.microsoft.com/free/ |
| **Google Workspace** | Developer | FREE | https://workspace.google.com/essentials/ |
| **Ping Identity** | Developer | FREE | https://www.pingidentity.com/developer |

### Local Environment
```bash
# Ensure services are running
docker-compose up -d

# Verify platform-service
curl http://localhost:8083/platform/actuator/health

# Verify auth-service  
curl http://localhost:8081/actuator/health
```

### Cognito Information Needed
```bash
# Get from Terraform outputs
cd terraform
terraform output cognito_user_pool_id
terraform output cognito_region
terraform output cognito_domain

# Example values:
# User Pool ID: us-east-1_aBcDeFgHi
# Region: us-east-1
# Domain: cloud-infra-dev-abc12345.auth.us-east-1.amazoncognito.com
```

---

## 2. Setting Up Test IdP Accounts

### 2.1 Okta Developer Account Setup

**Step 1: Create Account**
1. Go to https://developer.okta.com/signup/
2. Fill in: Work email, First Name, Last Name, Country
3. Activate via email link
4. Set password (complexity: 8+ chars, uppercase, lowercase, number)

**Step 2: Create Test Users**
1. Directory → People → Add Person
2. Create 3 test users:
   - `alice@yourcompany.com` (Group: Engineering)
   - `bob@yourcompany.com` (Group: Marketing)
   - `charlie@yourcompany.com` (Groups: Engineering, Marketing)

**Step 3: Create Groups**
1. Directory → Groups → Add Group
2. Create: `Engineering`, `Marketing`, `Admins`
3. Assign users to groups

**Step 4: Create SAML Application**
1. Applications → Create App Integration
2. Select: **SAML 2.0**
3. App name: "SaaS Platform (Test)"
4. Configure SAML:
   - **Single Sign-On URL:** `https://<cognito-domain>/saml2/idpresponse`
   - **Audience URI (SP Entity ID):** `urn:amazon:cognito:sp:<user-pool-id>`
   - **Name ID format:** Email
   - **Application username:** Email
5. Attribute Statements:
   | Name | Format | Value |
   |------|--------|-------|
   | email | Unspecified | user.email |
   | name | Unspecified | user.displayName |
6. Group Attribute Statements:
   | Name | Filter | Value |
   |------|--------|-------|
   | groups | Matches regex | .* |
7. Click Next → Finish

**Step 5: Get IdP Metadata**
1. Go to: Applications → Your App → Sign On tab
2. Click "View SAML setup instructions"
3. Copy the **Identity Provider metadata** URL
   - Format: `https://dev-XXXXX.okta.com/app/YYYYYY/sso/saml/metadata`

---

### 2.2 Azure AD Setup

**Step 1: Create Azure Account**
1. Go to https://portal.azure.com
2. Sign up for free account (requires credit card for verification, not charged)

**Step 2: Register an Enterprise Application**
1. Azure Active Directory → Enterprise applications → New application
2. Create your own application → "SaaS Platform Test"
3. Integrate any other application (non-gallery)

**Step 3: Configure SAML SSO**
1. Single sign-on → SAML
2. Basic SAML Configuration:
   - **Identifier (Entity ID):** `urn:amazon:cognito:sp:<user-pool-id>`
   - **Reply URL:** `https://<cognito-domain>/saml2/idpresponse`
3. User Attributes & Claims:
   - email: user.mail
   - name: user.displayname
   - groups: user.groups

**Step 4: Download Metadata**
1. SAML Signing Certificate section
2. Download → Federation Metadata XML
3. Or copy App Federation Metadata Url

**Step 5: Create Test Users**
1. Azure AD → Users → New user
2. Create test users with group assignments

---

### 2.3 Google Workspace (OIDC)

**Step 1: Google Cloud Console**
1. Go to https://console.cloud.google.com
2. Create new project: "SaaS Platform SSO Test"

**Step 2: Configure OAuth Consent Screen**
1. APIs & Services → OAuth consent screen
2. User Type: External (for testing)
3. App name: "SaaS Platform"
4. Scopes: email, profile, openid

**Step 3: Create OAuth Credentials**
1. APIs & Services → Credentials → Create Credentials → OAuth client ID
2. Application type: Web application
3. Name: "Cognito SSO"
4. Authorized redirect URIs: 
   - `https://<cognito-domain>/oauth2/idpresponse`
5. Note the **Client ID** and **Client Secret**

---

### 2.4 Ping Identity Setup

**Step 1: Create PingOne Account**
1. Go to https://www.pingidentity.com/en/try-ping.html  
2. Sign up for free trial

**Step 2: Create Application**
1. Applications → Add Application → SAML Application
2. ACS URL: `https://<cognito-domain>/saml2/idpresponse`
3. Entity ID: `urn:amazon:cognito:sp:<user-pool-id>`

**Step 3: Configure Attributes**
- email: Email Address
- name: Display Name
- memberOf: Group Membership (use `memberOf` claim name)

**Step 4: Download Metadata**
1. Configuration tab → Download SAML Metadata

---

## 3. Google OIDC Testing

### Configure in Platform UI
1. Login as tenant admin
2. Settings → SSO Configuration → OIDC tab
3. Provider: Google
4. Client ID: (from Google Cloud Console)
5. Client Secret: (from Google Cloud Console)
6. Save & Enable

### Test Flow
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Logout of application | Redirect to login page |
| 2 | Click "Sign in with Google" | Redirect to Google login |
| 3 | Enter Google credentials | Google prompts for consent (first time) |
| 4 | Click Allow | Redirect back to application |
| 5 | Check user in Users page | User appears with source=OIDC |
| 6 | Check JWT claims | Contains email, name from Google |

### Verify in Database
```sql
-- Platform DB: Check membership
SELECT * FROM user_tenant_memberships 
WHERE user_email = 'testuser@gmail.com';

-- Tenant DB: Check user record
SELECT * FROM users WHERE email = 'testuser@gmail.com';
-- source should be 'OIDC'
```

---

## 4. Okta SAML Testing

### Configure in Platform UI
1. Login as tenant admin
2. Settings → SSO Configuration → SAML tab
3. Provider: Okta
4. Metadata URL: (from Okta app setup)
5. Save & Enable

### Test Flow
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Logout of application | Redirect to login page |
| 2 | Enter email from Okta | System detects SSO tenant |
| 3 | Click "Sign in with SSO" | Redirect to Okta login page |
| 4 | Enter Okta credentials | Okta authenticates |
| 5 | Redirect back to app | User logged in with groups |
| 6 | Check user's role | Role matches group mapping |

### Verify Group Mapping
```sql
-- Check synced groups
SELECT * FROM idp_groups WHERE tenant_id = 'your-tenant-id';

-- Check group-role mappings
SELECT * FROM group_role_mappings;

-- Verify user role
SELECT u.email, r.name as role 
FROM user_roles ur
JOIN users u ON ur.user_id = u.user_id
JOIN roles r ON ur.role_id = r.id
WHERE u.email = 'alice@yourcompany.com';
```

---

## 5. Azure AD Testing

### Configure in Platform UI
1. Settings → SSO Configuration → SAML tab
2. Provider: Azure AD  
3. Upload metadata XML or enter URL
4. Save & Enable

### Test Flow
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Logout | Redirect to login |
| 2 | Click "Sign in with Azure" | Redirect to Microsoft login |
| 3 | Enter Microsoft credentials | Azure authenticates |
| 4 | MFA prompt (if enabled) | Complete MFA |
| 5 | Consent screen (first time) | Accept permissions |
| 6 | Redirect back | User logged in |

### Specific Azure AD Checks
- Verify group restrictions work (Azure AD groups)
- Test with MFA enabled users
- Test Conditional Access policies

---

## 6. Ping Identity Testing

### Configure in Platform UI  
1. Settings → SSO Configuration → SAML tab
2. Provider: Ping Identity
3. Upload downloaded metadata XML
4. Attribute mapping: Set groups claim to `memberOf`
5. Save & Enable

### Test Flow
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Logout | Redirect to login |
| 2 | Click "Sign in with SSO" | Redirect to PingOne |
| 3 | Enter Ping credentials | PingOne authenticates |
| 4 | Redirect back | User logged in |
| 5 | Verify groups | `memberOf` mapped correctly |

---

## 7. Test Cases & Edge Cases

### Core Functionality Tests

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| SSO-001 | First SSO Login (JIT Provision) | Login with new SSO user | User created in tenant DB with default role | HIGH |
| SSO-002 | Returning SSO User | Login with existing SSO user | User logged in, no duplicate created | HIGH |
| SSO-003 | Group-to-Role Mapping | Login with user in mapped group | User gets correct role | HIGH |
| SSO-004 | Multiple Groups | Login with user in multiple groups | Highest priority role used | HIGH |
| SSO-005 | No Group Membership | Login with user in no groups | User gets default role | MEDIUM |
| SSO-006 | Unknown Groups | Login with groups not in mappings | Groups ignored, uses default | MEDIUM |
| SSO-007 | SSO Disabled Mid-Session | Disable SSO while user logged in | Next request fails auth | MEDIUM |
| SSO-008 | Invalid SAML Metadata | Upload invalid XML | Error message displayed | HIGH |
| SSO-009 | Expired OIDC Secret | Login after secret expired | Error, admin notified | MEDIUM |
| SSO-010 | Email Case Sensitivity | Login with UPPER@case.com | Matches existing lowercase | MEDIUM |

### Security Tests

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| SEC-001 | SAML Signature Validation | Tamper with SAML response | Login rejected | CRITICAL |
| SEC-002 | OIDC Token Validation | Send forged id_token | Login rejected | CRITICAL |
| SEC-003 | Replay Attack | Replay old SAML assertion | Login rejected (expired) | HIGH |
| SEC-004 | IdP Impersonation | Configure malicious IdP URL | Validation fails | HIGH |
| SEC-005 | Cross-Tenant Access | SSO user tries different tenant | Access denied | CRITICAL |

### Performance Tests

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| PERF-001 | Group Sync Performance | Login with 50+ groups | Login < 3 seconds | MEDIUM |
| PERF-002 | Concurrent SSO Logins | 10 simultaneous SSO logins | All succeed | MEDIUM |
| PERF-003 | Metadata Validation | Upload 100KB metadata XML | Validation < 2 seconds | LOW |

### Error Handling Tests

| ID | Test Case | Steps | Expected Result | Priority |
|----|-----------|-------|-----------------|----------|
| ERR-001 | IdP Unreachable | Configure non-existent IdP URL | Graceful error message | HIGH |
| ERR-002 | Platform Service Down | SSO login when service down | Graceful fallback | MEDIUM |
| ERR-003 | Lambda Timeout | Large group list processing | Partial success logged | LOW |
| ERR-004 | DB Connection Failure | JIT provision during DB outage | Login fails gracefully | MEDIUM |

---

## 8. Troubleshooting

### Common Issues

| Problem | Possible Causes | Solution |
|---------|-----------------|----------|
| "Invalid SAML Response" | Wrong ACS URL configured | Check Cognito domain in IdP config |
| No groups in JWT | Groups claim not mapped | Add groups attribute in IdP |
| User not created | JIT provisioning disabled | Enable in SSO config |
| Wrong role assigned | Mapping priority issue | Check group_role_mappings priority |
| "Email mismatch" | IdP email != expected | Check attribute mapping |

### Debugging Commands

```bash
# Check Cognito Identity Providers
aws cognito-idp list-identity-providers \
  --user-pool-id us-east-1_xxxxxxxx

# View Cognito user attributes
aws cognito-idp admin-get-user \
  --user-pool-id us-east-1_xxxxxxxx \
  --username user@example.com

# Check Lambda logs
aws logs filter-log-events \
  --log-group-name /aws/lambda/cognito-pre-token-generation \
  --filter-pattern "ERROR"

# Check platform-service logs
docker logs platform-service 2>&1 | grep -i sso
```

### Log Locations

| Component | Log Location |
|-----------|--------------|
| PreTokenGeneration Lambda | CloudWatch: /aws/lambda/cognito-pre-token-generation |
| PostConfirmation Lambda | CloudWatch: /aws/lambda/cognito-post-confirmation |
| Platform Service | `docker logs platform-service` |
| Auth Service | `docker logs auth-service` |
| Gateway | `docker logs gateway-service` |

---

## Quick Reference: Cognito URLs

| URL Type | Format |
|----------|--------|
| **SAML ACS URL** | `https://<domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
| **SAML SP Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
| **OIDC Redirect URI** | `https://<domain>.auth.<region>.amazoncognito.com/oauth2/idpresponse` |
| **SAML Metadata** | `https://<domain>.auth.<region>.amazoncognito.com/saml2/idpmetadata?client_id=<client-id>` |
