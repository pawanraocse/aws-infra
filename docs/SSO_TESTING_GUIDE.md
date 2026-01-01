# SSO Testing Guide: Complete Manual Testing Procedures

**Version:** 1.1  
**Date:** 2025-12-29  
**Purpose:** Step-by-step guide for testing all SSO login options

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Complete Okta SSO Setup Guide](#2-complete-okta-sso-setup-guide)
3. [Complete Azure AD SSO Setup Guide](#3-complete-azure-ad-sso-setup-guide)
4. [Complete Google Workspace SSO Setup Guide](#4-complete-google-workspace-sso-setup-guide-oidc)
5. [Complete Ping Identity SSO Setup Guide](#5-complete-ping-identity-sso-setup-guide)
6. [Supported SSO Providers Summary](#6-supported-sso-providers-summary)
7. [Test Scenarios](#7-test-scenarios)
8. [Troubleshooting](#8-troubleshooting)
9. [Quick Reference](#9-quick-reference)
10. [Local Development Testing](#10-local-development-testing)
11. [Additional Test Scenarios](#11-additional-test-scenarios-local-dev)
12. [Key Learnings & Gotchas](#12-key-learnings--gotchas)
13. [Production Checklist](#13-production-checklist)

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

## 3. Complete Azure AD SSO Setup Guide

### 3.1 Create Azure AD Account (Free Tier)

1. Go to https://azure.microsoft.com/free/
2. Sign up with a Microsoft account (free)
3. Navigate to **Azure Portal** → https://portal.azure.com

---

### 3.2 Create Test Users in Azure AD

1. **Azure Portal → Azure Active Directory → Users → New user**
2. Create test users with emails (e.g., `testuser@yourdomain.onmicrosoft.com`)
3. Optionally assign to groups for role mapping

---

### 3.3 Create Enterprise Application for SAML SSO

1. **Azure AD → Enterprise applications → New application**
2. Click **Create your own application**
3. Name: "SaaS Platform SSO", select "Non-gallery application"
4. **Single sign-on → SAML**
5. Configure Basic SAML Configuration:

   | Setting | Value |
   |---------|-------|
   | **Identifier (Entity ID)** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **Reply URL (ACS URL)** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **Sign on URL** | (leave blank) |

6. **User Attributes & Claims** → Edit:
   | Claim name | Source attribute |
   |------------|------------------|
   | `emailaddress` | `user.mail` |
   | `name` | `user.displayname` |

---

### 3.4 Get Azure AD Metadata

1. **Single sign-on** page → **SAML Certificates** section
2. Download **Federation Metadata XML** or copy **App Federation Metadata Url**
3. Example URL: `https://login.microsoftonline.com/{tenant-id}/federationmetadata/2007-06/federationmetadata.xml?appid={app-id}`

---

### 3.5 Set Up Azure AD in Our App

1. Login as tenant admin
2. Navigate to **Settings → SSO Configuration**
3. Select **Azure AD** as provider
4. Paste the **Metadata URL** from step 3.4
5. Click **Save Configuration** → **Enable SSO**

---

### 3.6 Set Up Azure AD Directly in Cognito

**Step 1: Add Identity Provider**
1. AWS Console → Cognito → User Pools → Your Pool
2. **Social and external providers** → **Add identity provider** → **SAML**
3. Configure:
   - **Provider name:** `AZURE-{tenantId}` (e.g., `AZURE-aarohan`)
   - **Metadata document URL:** Paste URL from step 3.4
4. **Map attributes:**
   | User pool attribute | SAML attribute |
   |---------------------|----------------|
   | `email` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |

**Step 2: Enable for App Client**
1. **App integration** → Your app client → **Hosted UI** → Edit
2. Check the SAML provider
3. Save changes

---

## 4. Complete Google Workspace SSO Setup Guide (OIDC)

> **⚠️ IMPORTANT:** Cognito reserves the `GOOGLE` provider name for its built-in social login.
> For enterprise Google Workspace SSO, the app uses `GWORKSPACE-{tenantId}` as the provider name.

### 4.1 Create Google Cloud Account

1. Go to https://console.cloud.google.com
2. Sign in with Google account (free)
3. Create a new project or use existing

---

### 4.2 Configure OAuth Consent Screen

1. **APIs & Services → OAuth consent screen**
2. **User Type:**
   - **External:** For development/testing (requires adding test users)
   - **Internal:** For production (only works with Google Workspace domains)
3. Fill in:
   - App name: "SaaS Platform"
   - Support email: Your email
   - Developer contact: Your email
4. **Scopes:** Click "Add or Remove Scopes" → Add:
   - `email`
   - `profile`
   - `openid`
5. **Test users:** Add Gmail accounts that can test SSO login

> **Note:** Test users must be real Google accounts. Fake emails won't work.

---

### 4.3 Create OAuth 2.0 Credentials

1. **APIs & Services → Credentials → Create Credentials → OAuth client ID**
2. **Application type:** Web application
3. **Name:** "AWS Cognito SSO" (or any descriptive name)
4. **Authorized redirect URIs:** Add:
   ```
   https://<cognito-domain>.auth.<region>.amazoncognito.com/oauth2/idpresponse
   ```
   
   Example for our dev environment:
   ```
   https://cloud-infra-dev-9dosle0q.auth.us-east-1.amazoncognito.com/oauth2/idpresponse
   ```

5. Click **Create**
6. **Save the Client ID and Client Secret** - you'll need these!

---

### 4.4 Google OIDC Values Reference

| Value | Location |
|-------|----------|
| **Client ID** | Credentials page after creating OAuth client |
| **Client Secret** | Credentials page after creating OAuth client |
| **Issuer URL** | `https://accounts.google.com` |
| **Authorization endpoint** | `https://accounts.google.com/o/oauth2/v2/auth` |
| **Token endpoint** | `https://oauth2.googleapis.com/token` |
| **JWKS URI** | `https://www.googleapis.com/oauth2/v3/certs` |

---

### 4.5 Set Up Google SSO via the App (Recommended)

1. Login as tenant admin
2. Navigate to **Settings → SSO Configuration**
3. Select **Google Workspace** as provider
4. Enter:
   - **Client ID:** From step 4.3
   - **Client Secret:** From step 4.3
   - **Issuer URL:** `https://accounts.google.com`
5. Click **Save Configuration**
6. Click **Enable SSO**

The app automatically:
- Creates Cognito provider as `GWORKSPACE-{tenantId}` (e.g., `GWORKSPACE-aarohan`)
- Enables the provider for the app client
- Stores the `cognitoProviderName` in the tenant's `idp_config_json`

---

### 4.6 Set Up Google SSO Directly in Cognito (Alternative)

Use this method if testing Cognito directly without the app.

**Step 1: Add Identity Provider**
1. AWS Console → Cognito → User Pools → Your Pool
2. **Sign-in experience** → **Federated identity provider sign-in** → **Add identity provider** → **OpenID Connect**
3. Configure:
   - **Provider name:** `GWORKSPACE-{tenantId}` (e.g., `GWORKSPACE-aarohan`)
   
   > ⚠️ **DO NOT use `GOOGLE-{tenantId}`** - Cognito reserves names starting with `GOOGLE` for built-in social login
   
   - **Client ID:** From step 4.3
   - **Client secret:** From step 4.3
   - **Issuer URL:** `https://accounts.google.com`
   - **Authorize scope:** `openid email profile`
4. **Map attributes:**
   | User pool attribute | OIDC attribute |
   |---------------------|----------------|
   | `email` | `email` |
   | `name` | `name` |

**Step 2: Enable for App Client**
1. **App integration** → Your app client → **Hosted UI** → Edit
2. Check `GWORKSPACE-{tenantId}` under identity providers
3. Save changes

**Step 3: Update Database (if testing directly in Cognito)**
```sql
UPDATE tenant 
SET idp_type = 'GOOGLE',
    sso_enabled = true,
    idp_config_json = '{"cognitoProviderName": "GWORKSPACE-aarohan"}'
WHERE id = 'aarohan';
```

---

### 4.7 Test Google SSO Login

1. Go to login page
2. Click **"Sign in with SSO"**
3. Enter organization name (e.g., `aarohan`)
4. Click **"Continue to SSO"**
5. You should be redirected to Google sign-in
6. Sign in with a test user account
7. After authentication, you should be redirected back to the app

---

### 4.8 Google SSO Troubleshooting

| Error | Cause | Solution |
|-------|-------|----------|
| "Provider GOOGLE-xxx cannot be of type Google" | Using reserved `GOOGLE` prefix | Use `GWORKSPACE-` prefix instead |
| "redirect_uri_mismatch" | Callback URL not added to OAuth client | Add Cognito callback URL to Google OAuth client |
| "Access denied" | User not in test users list | Add user to OAuth consent screen test users |
| "Login pages unavailable" | Provider not enabled for app client | Enable provider in Cognito app client settings |

---

### 4.9 Google Workspace SAML (With Group Mapping)

For **enterprise users who need group-to-role mapping**, use Google SAML instead of OIDC.

> **Requires:** Google Workspace admin access

**Step 1: Create SAML App in Google Admin Console**
1. Go to https://admin.google.com
2. **Apps** → **Web and mobile apps** → **Add app** → **Add custom SAML app**
3. Configure:
   - **App name:** "AWS Cognito SSO"
   - **ACS URL:** `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse`
   - **Entity ID:** `urn:amazon:cognito:sp:<user-pool-id>`
   - **Name ID format:** Email
4. Add attribute mappings:
   - `email` → `email`
   - `name` → `name`
   - Add `groups` attribute for group membership

**Step 2: Configure in Our App**
1. Select **Google Workspace (SAML)** as provider type
2. Upload the SAML metadata or paste metadata URL
3. Provider will be created as `GSAML-{tenantId}`

**Step 3: Verify Group Mapping**
- Groups from Google will appear in the SAML assertion
- Configure group mappings in **Settings → Group Mapping**

---

## 5. Complete Ping Identity SSO Setup Guide

### 5.1 Create Ping Identity Account

1. Go to https://www.pingidentity.com/en/try-ping.html
2. Sign up for **PingOne** free trial
3. Complete registration and login to PingOne admin console

---

### 5.2 Create Test Users in PingOne

1. **Directory → Users → Add User**
2. Create test users with emails
3. Optionally create groups and assign users

---

### 5.3 Create SAML Application in PingOne

1. **Connections → Applications → Add Application**
2. Select **SAML Application**
3. **Name:** "SaaS Platform SSO"
4. **Configure SAML:**

   | Setting | Value |
   |---------|-------|
   | **ACS URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **Sign on URL** | (leave blank) |
   | **Binding** | HTTP-POST |

5. **Attribute Mappings:**
   | PingOne Attribute | SAML Attribute |
   |-------------------|----------------|
   | `email` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |
   | `name` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name` |

6. Click **Save** and **Enable** the application

---

### 5.4 Get Ping Identity Metadata

1. **Applications → Your App → Configuration**
2. Copy **Metadata URL** or download **Metadata XML**
3. Example URL: `https://auth.pingone.com/{environment-id}/saml20/metadata/{application-id}`

---

### 5.5 Set Up Ping Identity in Our App

1. Login as tenant admin
2. Navigate to **Settings → SSO Configuration**
3. Select **Ping Identity** as provider
4. Paste the **Metadata URL** from step 5.4
5. Click **Save Configuration** → **Enable SSO**

---

### 5.6 Set Up Ping Identity Directly in Cognito

**Step 1: Add Identity Provider**
1. AWS Console → Cognito → User Pools → Your Pool
2. **Social and external providers** → **Add identity provider** → **SAML**
3. Configure:
   - **Provider name:** `PING-{tenantId}` (e.g., `PING-aarohan`)
   - **Metadata document URL:** Paste URL from step 5.4
4. **Map attributes:**
   | User pool attribute | SAML attribute |
   |---------------------|----------------|
   | `email` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |

**Step 2: Enable for App Client**
1. **App integration** → Your app client → **Hosted UI** → Edit
2. Check the SAML provider
3. Save changes

---

## 6. Supported SSO Providers Summary

| Provider | Protocol | Free Tier | Setup Complexity |
|----------|----------|-----------|------------------|
| **Okta** | SAML | ✅ Developer account | Medium |
| **Azure AD** | SAML | ✅ Free tier | Medium |
| **Google** | OIDC | ✅ Free | Easy |
| **Ping Identity** | SAML | ✅ Free trial | Medium |
| **OneLogin** | SAML | ✅ Developer account | Medium |
| **Auth0** | OIDC | ✅ Free tier | Easy |

### Provider Name Convention

| Provider | Cognito Provider Name Format | Protocol |
|----------|------------------------------|----------|
| Okta | `OKTA-{tenantId}` | SAML |
| Azure AD | `AZURE-{tenantId}` | SAML |
| Google Workspace | `GWORKSPACE-{tenantId}` | OIDC |
| Ping Identity | `PING-{tenantId}` | SAML |
| OneLogin | `ONELOGIN-{tenantId}` | SAML |
| Auth0 | `AUTH0-{tenantId}` | OIDC |

> **⚠️ IMPORTANT:** 
> - Provider names are **case-sensitive**. Always use UPPERCASE for the IdP type prefix.
> - `GOOGLE-` is reserved by Cognito for built-in social login. Use `GWORKSPACE-` for enterprise Google Workspace SSO.

---

## 7. Test Scenarios

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

## 8. Troubleshooting

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

## 9. Quick Reference

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

## 10. Local Development Testing

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

## 11. Additional Test Scenarios (Local Dev)

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

## 12. Key Learnings & Gotchas

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

## 13. Production Checklist

Before deploying to production:

- [ ] `environment.production = true` (disables client-side JIT)
- [ ] Lambda VPC configured to reach platform-service
- [ ] Group mappings configured for all expected IdP groups
- [ ] SAML/OIDC metadata URLs are production endpoints
- [ ] Cognito Hosted UI callback URLs include production domain
- [ ] Log levels set to INFO (not DEBUG)




