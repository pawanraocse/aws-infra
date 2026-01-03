# SSO Testing Guide: Complete Manual Testing Procedures

**Version:** 2.0  
**Date:** 2026-01-01  
**Purpose:** Step-by-step guide for testing all SSO login options

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Supported SSO Providers](#2-supported-sso-providers)
3. [Google Workspace SAML Setup](#3-google-workspace-saml-setup)
4. [Microsoft Azure AD OIDC Setup](#4-microsoft-azure-ad-oidc-setup)
5. [Microsoft Azure AD SAML Setup](#5-microsoft-azure-ad-saml-setup)
6. [Okta SAML Setup](#6-okta-saml-setup)
7. [Ping Identity SAML Setup](#7-ping-identity-saml-setup)
8. [OneLogin SAML Setup](#8-onelogin-saml-setup)
9. [Generic SAML Provider Setup](#9-generic-saml-provider-setup)
10. [Testing Procedures](#10-testing-procedures)
11. [Troubleshooting](#11-troubleshooting)
12. [Quick Reference](#12-quick-reference)

---

## 1. Prerequisites

### 1.1 Running Infrastructure

```bash
# Start all services with Docker Compose
cd /Users/pawan.yadav/prototype/AWS-Infra
docker compose up -d

# Or start individual services
mvn spring-boot:run -pl eureka-server &
mvn spring-boot:run -pl gateway-service &
mvn spring-boot:run -pl platform-service &
mvn spring-boot:run -pl auth-service &

# Frontend
cd frontend && npm run start
```

### 1.2 Cognito Information

```bash
# Get from Terraform outputs
cd terraform
terraform output cognito_user_pool_id    # e.g., us-east-1_JTWyGznRm
terraform output cognito_region          # e.g., us-east-1
terraform output cognito_domain          # e.g., cloud-infra-dev-xxxxx.auth.us-east-1.amazoncognito.com
```

### 1.3 Required Information from Cognito

| Value | How to Find |
|-------|-------------|
| **User Pool ID** | AWS Console → Cognito → User Pools → Overview |
| **Cognito Domain** | AWS Console → Cognito → User Pools → App Integration → Domain |
| **SAML ACS URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
| **SP Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
| **OIDC Redirect URI** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/oauth2/idpresponse` |

### 1.4 Test Organization Setup

1. Login as platform admin
2. Navigate to Admin → Tenants
3. Create test organization with Enterprise tier
4. Note the tenant ID (e.g., `test-org`)

---

## 2. Supported SSO Providers

### 2.1 Provider Options in UI

| Provider | Protocol | Use Case | Free Tier |
|----------|----------|----------|-----------|
| **Google Workspace** | SAML | Enterprise Google SSO with groups | Requires Google Workspace |
| **Microsoft Azure AD (OIDC)** | OIDC | Simpler setup, <200 groups | ✅ Free tier |
| **Microsoft Azure AD (SAML)** | SAML | Enterprise, unlimited groups | ✅ Free tier |
| **Okta** | SAML | Enterprise SSO | ✅ Developer account |
| **Ping Identity** | SAML | Enterprise SSO | ✅ Free trial |
| **OneLogin** | SAML | Enterprise SSO | ✅ Developer account |
| **Other SAML Provider** | SAML | Generic SAML support | Varies |

### 2.2 Protocol Comparison

| Feature | OIDC | SAML |
|---------|------|------|
| **Setup Complexity** | Simpler (Client ID + Secret) | More complex (Metadata XML) |
| **Groups Support** | Provider-dependent | ✅ Standard |
| **Token Format** | JSON (JWT) | XML Assertion |
| **Certificate Management** | Not needed | Certificates expire |
| **Best For** | Smaller orgs, simpler needs | Enterprise, groups/roles |

### 2.3 Cognito Provider Naming Convention

| Provider | Cognito Provider Name |
|----------|----------------------|
| Google Workspace (SAML) | `GSAML-{tenantId}` |
| Azure AD (OIDC) | `AZURE-OIDC-{tenantId}` |
| Azure AD (SAML) | `AZURE-SAML-{tenantId}` |
| Okta | `OKTA-{tenantId}` |
| Ping Identity | `PING-{tenantId}` |
| OneLogin | `ONELOGIN-{tenantId}` |
| Generic SAML | `SAML-{tenantId}` |

> **⚠️ IMPORTANT:** Provider names are **case-sensitive**.

---

## 3. Google Workspace SAML Setup

> **Note:** Google Workspace SAML requires admin access to Google Admin Console.

### 3.1 Create SAML App in Google Admin Console

1. Go to https://admin.google.com
2. Navigate to **Apps** → **Web and mobile apps** → **Add app** → **Add custom SAML app**
3. **App Details:**
   - App name: `Your SaaS Platform`
   - Description: Optional
   - Upload app icon (optional)
4. Click **Continue**

### 3.2 Configure SAML Settings

| Setting | Value |
|---------|-------|
| **ACS URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
| **Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
| **Start URL** | Leave blank |
| **Signed Response** | Checked |
| **Name ID** | Basic Information > Primary email |
| **Name ID Format** | EMAIL |

### 3.3 Attribute Mapping

| Google Directory | App Attribute |
|------------------|---------------|
| Primary email | `email` |
| First name | `firstName` |
| Last name | `lastName` |

For groups (optional):
- Add custom attribute: `groups` → Map to user's group membership

### 3.4 Get Metadata

1. After saving, click on your app
2. Download **IdP metadata** XML file
3. Note the **SSO URL** and **Entity ID**

### 3.5 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **Google Workspace**
4. Enter **IdP Metadata URL** or paste metadata content
5. Click **Save Configuration**
6. Toggle **Enable SSO**

### 3.6 Configure Directly in Cognito (Alternative)

1. AWS Console → Cognito → User Pools → Your Pool
2. **Sign-in experience** → **Add identity provider** → **SAML**
3. Configure:
   - **Provider name:** `GSAML-{tenantId}`
   - **Metadata:** Upload XML or paste URL
4. **Attribute mapping:**

   | User pool attribute | SAML attribute |
   |---------------------|----------------|
   | `email` | `email` or `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |

5. Enable for app client in **App integration**

---

## 4. Microsoft Azure AD OIDC Setup

> **Best for:** Organizations with fewer than 200 groups, simpler setup.

### 4.1 Create Azure AD Application

1. Go to https://portal.azure.com
2. Navigate to **Azure Active Directory** → **App registrations** → **New registration**
3. Configure:
   - **Name:** `Your SaaS Platform SSO`
   - **Supported account types:** Single tenant or Multitenant
   - **Redirect URI:** 
     - Platform: `Web`
     - URL: `https://<cognito-domain>.auth.<region>.amazoncognito.com/oauth2/idpresponse`

### 4.2 Configure Authentication

1. Go to **Authentication** → **Platform configurations**
2. Ensure redirect URI is added
3. Under **Implicit grant**, check:
   - ✅ ID tokens

### 4.3 Get Client Credentials

1. **Overview** page:
   - Copy **Application (client) ID**
   - Copy **Directory (tenant) ID**
2. **Certificates & secrets** → **New client secret**
   - Add description, set expiry
   - **Copy the secret value immediately** (shown only once)

### 4.4 Configure API Permissions (for groups)

1. **API permissions** → **Add a permission**
2. **Microsoft Graph** → **Delegated permissions**
3. Add:
   - `openid`
   - `profile`
   - `email`
   - `GroupMember.Read.All` (for groups)
4. Click **Grant admin consent**

### 4.5 Configure Token Claims (for groups)

1. **Token configuration** → **Add groups claim**
2. Select:
   - ✅ Security groups
   - For ID token: **Group ID**
3. Save

### 4.6 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **Microsoft Azure AD (OIDC)**
4. Enter:
   - **Client ID:** Application (client) ID
   - **Client Secret:** Your client secret
   - **Issuer URL:** `https://login.microsoftonline.com/{tenant-id}/v2.0`
5. Click **Save Configuration**
6. Toggle **Enable SSO**

### 4.7 Configure Directly in Cognito (Alternative)

1. AWS Console → Cognito → User Pools → Your Pool
2. **Sign-in experience** → **Add identity provider** → **OpenID Connect**
3. Configure:
   - **Provider name:** `AZURE-OIDC-{tenantId}`
   - **Client ID:** Your App ID
   - **Client secret:** Your secret
   - **Issuer URL:** `https://login.microsoftonline.com/{tenant-id}/v2.0`
   - **Authorize scope:** `openid email profile`
4. **Attribute mapping:**

   | User pool attribute | OIDC attribute |
   |---------------------|----------------|
   | `email` | `email` |
   | `name` | `name` |

---

## 5. Microsoft Azure AD SAML Setup

> **Best for:** Large enterprises with >200 groups or needing group names (not GUIDs).

### 5.1 Create Enterprise Application

1. Azure Portal → **Enterprise applications** → **New application**
2. Click **Create your own application**
3. Name: `Your SaaS Platform SSO`
4. Select **Integrate any other application you don't find in the gallery**
5. Create

### 5.2 Configure SAML

1. Go to **Single sign-on** → Select **SAML**
2. **Basic SAML Configuration:**

   | Setting | Value |
   |---------|-------|
   | **Identifier (Entity ID)** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **Reply URL (ACS URL)** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **Sign on URL** | Leave blank |

3. **Attributes & Claims** → Edit:
   | Claim name | Source attribute |
   |------------|------------------|
   | `emailaddress` | `user.mail` |
   | `name` | `user.displayname` |
   | `groups` | `user.groups` (optional) |

### 5.3 Get Metadata

1. **SAML Certificates** section
2. Copy **App Federation Metadata Url** or download XML

### 5.4 Assign Users

1. **Users and groups** → **Add user/group**
2. Select users or groups to grant access

### 5.5 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **Microsoft Azure AD (SAML)**
4. Enter **IdP Metadata URL**
5. Click **Save Configuration**
6. Toggle **Enable SSO**

---

## 6. Okta SAML Setup

### 6.1 Create Developer Account

1. Go to https://developer.okta.com/signup/
2. Sign up with work email
3. Activate account via email
4. Note your Okta domain: `https://dev-XXXXX.okta.com`

### 6.2 Create SAML Application

1. **Applications** → **Create App Integration** → **SAML 2.0** → Next
2. **General Settings:**
   - App name: `Your SaaS Platform`
3. **SAML Settings:**

   | Setting | Value |
   |---------|-------|
   | **Single sign-on URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **Audience URI (SP Entity ID)** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **Name ID format** | EmailAddress |
   | **Application username** | Email |

4. **Attribute Statements:**

   | Name | Value |
   |------|-------|
   | `email` | `user.email` |
   | `firstName` | `user.firstName` |
   | `lastName` | `user.lastName` |

5. **Group Attribute Statements:** (optional)

   | Name | Filter |
   |------|--------|
   | `groups` | Matches regex `.*` |

6. Click **Next** → **I'm an Okta customer** → **Finish**

### 6.3 Get Metadata URL

1. **Sign On** tab → **SAML 2.0** section
2. Copy **Metadata URL**
   - Format: `https://dev-XXXXX.okta.com/app/YYYYYY/sso/saml/metadata`

### 6.4 Assign Users

1. **Assignments** tab → **Assign** → **Assign to People/Groups**

### 6.5 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **Okta**
4. Enter **IdP Metadata URL**
5. Click **Save Configuration**
6. Toggle **Enable SSO**

### 6.6 Configure Group-to-Role Mapping (Recommended)

This feature allows automatic role assignment based on Okta group membership.

#### Step 1: Verify Okta Group Attribute Statement (in Okta)

Ensure your Okta SAML app has a **Group Attribute Statement**:

| Setting | Value |
|---------|-------|
| **Name** | `group` |
| **Name format** | Basic |
| **Filter** | Matches regex `.*` (all groups) or specific regex like `dev|admin` |

> **Note:** Users must be members of groups that are **assigned to the Okta SAML app**, not just member of any group.

#### Step 2: Verify Cognito Attribute Mapping

Our platform automatically maps Okta's `group` attribute to `custom:samlGroups` in Cognito when you save SSO configuration. Verify this is in place:

```bash
aws cognito-idp describe-identity-provider \
  --user-pool-id us-east-1_XXXXX \
  --provider-name OKTA-{tenantId} \
  --query 'IdentityProvider.AttributeMapping'
```

Expected output:
```json
{
    "custom:samlGroups": "group",
    "email": "email",
    "name": "name"
}
```

#### Step 3: Create Group Mappings in the App

1. Login as tenant admin
2. Navigate to **Admin** → **Group Mappings**
3. Add mappings for each IdP group:

   | IdP Group Name | Role | Priority |
   |----------------|------|----------|
   | `Admins` | admin | 1 |
   | `Developers` | editor | 10 |
   | `dev` | editor | 10 |
   | `QA` | viewer | 20 |

> **Priority:** Lower number = higher priority. If user is in multiple groups, highest priority role is used.

#### Step 4: Test Group-to-Role Mapping

1. **Check JWT claims** after SSO login (use browser dev tools or jwt.io):
   ```json
   "custom:samlGroups": "[Everyone, dev]"
   ```

2. **Verify role assignment** in the UI under user profile

3. **Expected behavior:**
   - If user is in `dev` group and you have `dev` → `editor` mapping → user gets `editor` role
   - If no matching group mapping → falls back to `user_roles` table
   - If no `user_roles` entry → defaults to `viewer` role

#### Debugging Group Mapping

```bash
# Check gateway logs for parsed groups
docker logs gateway-service 2>&1 | grep -i 'samlGroups'

# Expected: "Found SAML groups in custom:samlGroups: [Everyone, dev] -> parsed: [Everyone, dev]"
# Expected: "Passing IdP groups to downstream: Everyone,dev"

# Check auth-service logs for role resolution
docker logs auth-service 2>&1 | grep -i 'group mapping'

# Expected: "Role from group mapping: userId=... groups=Everyone,dev role=editor"
```

#### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `custom:samlGroups` empty in JWT | Okta not sending groups | Add Group Attribute Statement in Okta |
| Groups in JWT but no role mapping | Group name mismatch | Check exact group name (case-sensitive) |
| Role still `viewer` | No matching group mapping | Create mapping in Admin → Group Mappings |
| Groups have brackets `[dev, qa]` | Normal Cognito format | Gateway strips brackets automatically |

---

## 7. Ping Identity SAML Setup

### 7.1 Create PingOne Account

1. Go to https://www.pingidentity.com/en/try-ping.html
2. Sign up for PingOne free trial
3. Complete registration and login

### 7.2 Create SAML Application

1. **Connections** → **Applications** → **+ Application**
2. **Application Type:** SAML Application
3. **Name:** `Your SaaS Platform`
4. **Configure SAML:**

   | Setting | Value |
   |---------|-------|
   | **ACS URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **SLO Binding** | HTTP-POST |

5. **Attribute Mappings:**

   | PingOne Attribute | SAML Attribute |
   |-------------------|----------------|
   | `email` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |
   | `name` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name` |

6. **Save** and **Enable**

### 7.3 Get Metadata URL

1. **Configuration** tab
2. Copy **Metadata URL**

### 7.4 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **Ping Identity**
4. Enter **IdP Metadata URL**
5. Click **Save Configuration**
6. Toggle **Enable SSO**

---

## 8. OneLogin SAML Setup

### 8.1 Create Developer Account

1. Go to https://www.onelogin.com/developer-signup
2. Sign up for free developer account
3. Complete registration

### 8.2 Create SAML Application

1. **Applications** → **Add App**
2. Search for **SAML Custom Connector (Advanced)**
3. **Display Name:** `Your SaaS Platform`
4. **Configuration:**

   | Setting | Value |
   |---------|-------|
   | **Audience (EntityID)** | `urn:amazon:cognito:sp:<user-pool-id>` |
   | **ACS (Consumer) URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
   | **ACS URL Validator** | `.*` |
   | **SAML nameID format** | Email |

5. **Parameters:**

   | Field name | Value |
   |------------|-------|
   | `email` | Email |
   | `firstName` | First Name |
   | `lastName` | Last Name |

6. **Save**

### 8.3 Get Metadata

1. **SSO** tab
2. Click **More Actions** → **SAML Metadata**
3. Copy URL or download XML

### 8.4 Assign Users

1. **Users** tab → **Add users**

### 8.5 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **OneLogin**
4. Enter **IdP Metadata URL**
5. Click **Save Configuration**
6. Toggle **Enable SSO**

---

## 9. Generic SAML Provider Setup

For any SAML 2.0 compliant identity provider not listed above.

### 9.1 Required Information from Your IdP

You need to provide:

| Information | Description |
|-------------|-------------|
| **Metadata URL** | URL to IdP's SAML metadata XML |
| **OR Metadata XML** | The actual metadata XML content |
| **Entity ID** | IdP's entity identifier (optional if in metadata) |
| **SSO URL** | IdP's sign-on URL (optional if in metadata) |

### 9.2 Configure Your IdP

Set up your IdP with these values:

| Setting | Value |
|---------|-------|
| **SP Entity ID** | `urn:amazon:cognito:sp:<user-pool-id>` |
| **ACS URL** | `https://<cognito-domain>.auth.<region>.amazoncognito.com/saml2/idpresponse` |
| **Name ID Format** | `urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress` |

### 9.3 Attribute Mapping

Ensure your IdP sends at minimum:

| Attribute | SAML Claim Name |
|-----------|-----------------|
| **Email** | `email` or `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |
| **Name** (optional) | `name` or `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name` |
| **Groups** (optional) | `groups` or `memberOf` |

### 9.4 Configure in Our App

1. Login as tenant admin
2. Navigate to **Settings** → **SSO Configuration**
3. Select **Other SAML Provider**
4. Enter **IdP Metadata URL** or paste XML content
5. Optionally fill:
   - **Entity ID** (if not in metadata)
   - **Sign-In URL** (if not in metadata)
6. Click **Save Configuration**
7. Toggle **Enable SSO**

---

## 10. Testing Procedures

### 10.1 Test SSO Login Flow

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Go to login page | Login form displayed |
| 2 | Click "Sign in with SSO" | Org selection prompt |
| 3 | Enter org name, click Continue | Redirect to IdP |
| 4 | Authenticate with IdP | IdP login succeeds |
| 5 | Return to app | Dashboard shown, user logged in |

### 10.2 Verify User Creation (JIT Provisioning)

```sql
-- Check user was created
SELECT email, tenant_id, created_at 
FROM users 
WHERE email = 'sso-user@company.com';

-- Check membership
SELECT * 
FROM user_tenant_membership 
WHERE user_email = 'sso-user@company.com';
```

### 10.3 Test SSO Toggle

1. Disable SSO in settings
2. Try SSO login → Should show error
3. Enable SSO again
4. SSO login should work

### 10.4 Test with New User

1. Create new user in IdP (not in our system)
2. Login via SSO
3. Verify user is created via JIT provisioning
4. Check correct role is assigned

---

## 11. Troubleshooting

### 11.1 Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Invalid SAML Response" | Wrong ACS URL | Verify ACS URL matches Cognito |
| "Invalid redirect_uri" | OIDC redirect mismatch | Check redirect URI in IdP matches Cognito |
| "Login pages unavailable" | Provider not enabled | Enable provider for app client in Cognito |
| "No user found" | JIT provisioning failed | Check Lambda/auth-service logs |
| "Access denied" | User not assigned in IdP | Assign user to app in IdP |

### 11.2 Debug Commands

```bash
# Check Cognito providers
aws cognito-idp list-identity-providers \
  --user-pool-id us-east-1_XXXXX

# Check specific provider
aws cognito-idp describe-identity-provider \
  --user-pool-id us-east-1_XXXXX \
  --provider-name OKTA-tenantid

# View auth-service logs
docker logs auth-service 2>&1 | grep -i sso

# View platform-service logs
docker logs platform-service 2>&1 | grep -i sso
```

### 11.3 SAML Debugging

1. Install SAML DevTools browser extension
2. Capture SAML response during login
3. Verify:
   - Correct ACS URL
   - Valid signature
   - Email attribute present

---

## 12. Quick Reference

### 12.1 Cognito URLs (Example)

```
Domain: cloud-infra-dev-xxxxx.auth.us-east-1.amazoncognito.com
User Pool ID: us-east-1_JTWyGznRm
Region: us-east-1

SAML ACS URL: https://cloud-infra-dev-xxxxx.auth.us-east-1.amazoncognito.com/saml2/idpresponse
SP Entity ID: urn:amazon:cognito:sp:us-east-1_JTWyGznRm
OIDC Redirect: https://cloud-infra-dev-xxxxx.auth.us-east-1.amazoncognito.com/oauth2/idpresponse
```

### 12.2 API Endpoints

```
GET  /api/v1/sso/config         - Get current config
POST /api/v1/sso/config/saml    - Save SAML config
POST /api/v1/sso/config/oidc    - Save OIDC config
PATCH /api/v1/sso/toggle        - Enable/disable SSO
POST /api/v1/sso/test           - Test connection
DELETE /api/v1/sso/config       - Remove SSO config
GET  /api/v1/sso/sp-metadata    - Get SP metadata
```

### 12.3 Frontend Routes

```
/app/admin/settings         - Admin settings
SSO Configuration panel     - Configure SSO providers
```

### 12.4 IdP Signup URLs

| Provider | Signup URL |
|----------|------------|
| Okta Developer | https://developer.okta.com/signup/ |
| Azure AD Free | https://azure.microsoft.com/free/ |
| Google Cloud | https://console.cloud.google.com |
| Ping Identity | https://www.pingidentity.com/en/try-ping.html |
| OneLogin | https://www.onelogin.com/developer-signup |

---

## Appendix: Personal Google Sign-In (Social Login)

> **Note:** This is separate from organization SSO. Personal Google Sign-In uses Cognito's built-in OIDC provider for individual users.

Personal Google Sign-In is pre-configured via Terraform and works automatically for the "Sign in with Google" button on the login page. No additional configuration is needed.

This is different from Google Workspace SAML which is for enterprise organizations to configure their own Google Workspace domain as an SSO provider with group support.
