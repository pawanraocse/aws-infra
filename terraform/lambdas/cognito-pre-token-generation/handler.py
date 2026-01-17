"""
AWS Lambda function for Cognito PreTokenGeneration trigger.

This function is invoked by Cognito before generating tokens during authentication.
It handles:
1. Tenant selection override (existing)
2. SSO group extraction and sync (NEW)
3. Group-based role resolution (NEW)

Use Case:
---------
When a user logs in via SSO (SAML/OIDC), their group memberships are extracted from
the identity provider's claims and synced to the platform. These groups are then
used to resolve roles for the user.

Event Structure (SSO):
----------------------
{
    "userName": "user@example.com",
    "triggerSource": "TokenGeneration_Authentication",
    "request": {
        "userAttributes": {
            "custom:tenantId": "stored-tenant-id",
            "custom:role": "admin",
            "custom:tenantType": "ORGANIZATION",
            "identities": "[{\"providerName\":\"Okta\",\"userId\":\"...\",...}]",
            "custom:groups": "Engineering,Marketing"  // From IdP
        },
        "clientMetadata": {
            "selectedTenantId": "user-selected-tenant-id"
        }
    },
    "response": {}
}
"""

import json
import logging
import os
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

# Configure logging - DEBUG level for production troubleshooting
logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

# Environment variables
PLATFORM_SERVICE_URL = os.environ.get('PLATFORM_SERVICE_URL', 'http://localhost:8082')
AUTH_SERVICE_URL = os.environ.get('AUTH_SERVICE_URL', 'http://localhost:8081')
ENABLE_GROUP_SYNC = os.environ.get('ENABLE_GROUP_SYNC', 'true').lower() == 'true'

# Log environment at module load (helps debug configuration issues)
logger.info(f"[CONFIG] PLATFORM_SERVICE_URL={PLATFORM_SERVICE_URL}")
logger.info(f"[CONFIG] AUTH_SERVICE_URL={AUTH_SERVICE_URL}")
logger.info(f"[CONFIG] ENABLE_GROUP_SYNC={ENABLE_GROUP_SYNC}")

# Valid trigger sources for token generation
TOKEN_GENERATION_TRIGGERS = frozenset([
    'TokenGeneration_Authentication',
    'TokenGeneration_RefreshTokens',
    'TokenGeneration_HostedAuth',
    'TokenGeneration_NewPasswordChallenge'
])

# Known IdP group claim attribute names
GROUP_CLAIM_ATTRIBUTES = [
    'custom:groups',           # Custom attribute for groups
    'cognito:groups',          # Cognito native groups
    'saml:groups',             # SAML groups claim
    'groups',                  # OIDC groups claim
    'custom:saml_groups',      # Alternative SAML format
]


def lambda_handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    """
    Handle Cognito PreTokenGeneration trigger.
    
    This function is called on EVERY LOGIN (not just signup), making it ideal
    for JIT (Just-In-Time) provisioning of SSO users on their first login.
    
    Steps:
    1. Extract tenant and role info
    2. Extract groups from SSO claims (if present)
    3. Sync groups to platform service
    4. JIT provision new SSO users (if not exists)
    5. Add groups to JWT claims
    
    Args:
        event: Cognito trigger event containing user info and metadata
        context: Lambda context (unused)
    
    Returns:
        event: Modified event with claimsOverrideDetails if applicable
    """
    try:
        username = event.get('userName', 'unknown')
        trigger_source = event.get('triggerSource', '')
        
        # Extract data from event
        request_data = event.get('request', {})
        client_metadata = request_data.get('clientMetadata', {}) or {}
        user_attributes = request_data.get('userAttributes', {}) or {}
        user_sub = user_attributes.get('sub', username)
        
        # Debug logging for production troubleshooting
        logger.info(f"PreTokenGeneration triggered for user: {username}, trigger: {trigger_source}")
        logger.debug(f"[EVENT] userAttributes keys: {list(user_attributes.keys())}")
        logger.debug(f"[EVENT] clientMetadata: {client_metadata}")
        
        # Only process token generation events
        if trigger_source not in TOKEN_GENERATION_TRIGGERS:
            logger.debug(f"Skipping non-token-generation trigger: {trigger_source}")
            return event
        
        # Get selected tenant from login (if any)
        selected_tenant_id = client_metadata.get('selectedTenantId')
        
        # Get stored attributes from Cognito user
        stored_tenant_id = user_attributes.get('custom:tenantId')
        stored_tenant_type = user_attributes.get('custom:tenantType', 'PERSONAL')
        
        # Extract groups and detect IdP type from SSO claims
        groups = _extract_groups_from_claims(user_attributes)
        idp_type = _detect_idp_type(user_attributes)
        is_sso_user = idp_type in ('SAML', 'OIDC', 'OKTA', 'AZURE_AD', 'GOOGLE', 'PING')
        
        # Check if this is a social login (built-in Google, not GWORKSPACE-/GSAML-)
        is_social_login = _is_social_login(user_attributes)
        
        # For SSO users, try to extract tenant from identity provider name
        # Provider names follow pattern: OKTA-{tenantId}, SAML-{tenantId}, etc.
        sso_tenant_id = None
        if is_sso_user and not is_social_login:
            sso_tenant_id = _extract_tenant_from_idp(user_attributes)
            if sso_tenant_id:
                logger.info(f"Extracted tenant '{sso_tenant_id}' from SSO identity provider")
                stored_tenant_type = 'ORGANIZATION'  # SSO users are always org users
        
        # For social login (personal Gmail), create personal tenant from email
        if is_social_login and not stored_tenant_id:
            email = user_attributes.get('email', username)
            sso_tenant_id = _generate_personal_tenant_id(email)
            stored_tenant_type = 'PERSONAL'
            logger.info(f"Generated personal tenant '{sso_tenant_id}' for social login user: {email}")
        
        # Determine final tenant ID - SSO tenant takes precedence for federated users
        final_tenant_id = _determine_tenant_id(
            selected_tenant_id, 
            sso_tenant_id or stored_tenant_id, 
            username
        )
        
        if not final_tenant_id:
            logger.warning(f"No tenantId found for user: {username}")
            return event

        # CORRECTION: Force Tenant Type if not personal
        # If the tenant ID is set but type is wrong (e.g. from attributes), correct it
        if final_tenant_id and not final_tenant_id.startswith('personal-'):
            stored_tenant_type = 'ORGANIZATION'
            logger.info(f"Enforcing ORGANIZATION type for tenant: {final_tenant_id}")
        
        # Sync groups if enabled and groups found
        if ENABLE_GROUP_SYNC and groups and final_tenant_id:
            _sync_groups_to_platform(final_tenant_id, groups, idp_type)
        
        # JIT Provisioning: Check if SSO user exists and provision if new
        if is_sso_user:
            email = user_attributes.get('email', username)
            _jit_provision_if_needed(final_tenant_id, email, user_sub, groups, idp_type)
        
        # Build claims override (role is managed in DB, not JWT)
        claims_to_override = {
            'custom:tenantId': final_tenant_id,
            'custom:tenantType': stored_tenant_type,
        }
        
        # Add groups to claims if present
        if groups:
            claims_to_override['custom:groups'] = ','.join(groups)
        
        # Set the override in the response
        # NOTE: Using V2 format (claimsAndScopeOverrideDetails) as Lambda is configured for V2_0
        event['response'] = event.get('response', {})
        event['response']['claimsAndScopeOverrideDetails'] = {
            'idTokenGeneration': {
                'claimsToAddOrOverride': claims_to_override
            },
            'accessTokenGeneration': {
                'claimsToAddOrOverride': claims_to_override
            }
        }
        
        logger.info(
            f"Token claims set for {username}: tenantId={final_tenant_id}, "
            f"groups={len(groups) if groups else 0}, sso={is_sso_user}"
        )
        
    except Exception as e:
        # Log error but don't block authentication
        logger.error(f"Error in PreTokenGeneration: {str(e)}", exc_info=True)
    
    return event


def _extract_groups_from_claims(user_attributes: Dict[str, str]) -> List[str]:
    """
    Extract groups from various IdP claim formats.
    
    Handles:
    - SAML: Groups in custom:groups or saml:groups
    - OIDC: Groups in groups claim
    - Cognito: Groups in cognito:groups
    
    Returns:
        List of group identifiers
    """
    groups = []
    
    for attr_name in GROUP_CLAIM_ATTRIBUTES:
        if attr_name in user_attributes:
            raw_value = user_attributes[attr_name]
            if raw_value:
                # Handle comma-separated format
                if isinstance(raw_value, str):
                    if raw_value.startswith('['):
                        # JSON array format
                        try:
                            groups.extend(json.loads(raw_value))
                        except json.JSONDecodeError:
                            groups.extend([g.strip() for g in raw_value.split(',') if g.strip()])
                    else:
                        # Comma-separated format
                        groups.extend([g.strip() for g in raw_value.split(',') if g.strip()])
                elif isinstance(raw_value, list):
                    groups.extend(raw_value)
                
                logger.debug(f"Found groups in {attr_name}: {groups}")
                break  # Use first match
    
    # Deduplicate and filter empty
    unique_groups = list(dict.fromkeys(g for g in groups if g))
    
    if unique_groups:
        logger.info(f"Extracted {len(unique_groups)} groups from claims")
    
    return unique_groups


def _detect_idp_type(user_attributes: Dict[str, str]) -> str:
    """
    Detect the Identity Provider type from user attributes.
    
    Returns:
        IdP type string (SAML, OIDC, OKTA, AZURE_AD, GOOGLE, etc.)
    """
    identities_raw = user_attributes.get('identities', '')
    
    if identities_raw:
        try:
            identities = json.loads(identities_raw)
            if identities and isinstance(identities, list):
                provider_name = identities[0].get('providerName', '').upper()
                
                if 'OKTA' in provider_name:
                    return 'OKTA'
                elif 'AZURE' in provider_name or 'MICROSOFT' in provider_name:
                    return 'AZURE_AD'
                elif 'GOOGLE' in provider_name:
                    return 'GOOGLE'
                elif 'PING' in provider_name:
                    return 'PING'
                elif 'SAML' in provider_name:
                    return 'SAML'
        except json.JSONDecodeError:
            pass
    
    # Check for SAML-specific attributes
    if any(attr.startswith('saml:') for attr in user_attributes.keys()):
        return 'SAML'
    
    return 'OIDC'


def _is_social_login(user_attributes: Dict[str, str]) -> bool:
    """
    Check if this is a social login (built-in Google, Apple, Facebook, etc.).
    
    Social providers use simple names like 'Google', not prefixed like 'GWORKSPACE-tenant'.
    This distinguishes personal Gmail sign-in (B2C) from organization SSO (B2B).
    
    Returns:
        True if social login (e.g., Google built-in), False for org SSO
    """
    identities_raw = user_attributes.get('identities', '')
    
    if not identities_raw:
        return False
    
    try:
        identities = json.loads(identities_raw)
        if identities and isinstance(identities, list):
            provider_name = identities[0].get('providerName', '')
            
            # Built-in social providers have simple names without tenant suffix
            # e.g., 'Google' vs 'GWORKSPACE-aarohan' or 'GSAML-aarohan'
            social_providers = {'Google', 'Facebook', 'Amazon', 'Apple', 'SignInWithApple'}
            
            return provider_name in social_providers
    except json.JSONDecodeError:
        pass
    
    return False


def _generate_personal_tenant_id(email: str) -> str:
    """
    Generate a personal tenant ID from user's email.
    
    For B2C personal sign-in via social login (Google, etc.), we create
    a personal tenant based on the user's email to isolate their data.
    
    Args:
        email: User's email address
    
    Returns:
        Personal tenant ID in format 'personal-{username}' (max 32 chars)
    """
    import re
    import hashlib
    
    if not email or '@' not in email:
        # Fallback to hash if email is invalid
        return f"personal-{hashlib.md5(email.encode()).hexdigest()[:8]}"
    
    # Extract username part of email (before @)
    username = email.split('@')[0]
    
    # Sanitize: only alphanumeric and hyphens
    sanitized = re.sub(r'[^a-zA-Z0-9]', '', username).lower()
    
    # Limit length and add prefix
    if len(sanitized) > 20:
        sanitized = sanitized[:20]
    
    return f"personal-{sanitized}"


def _extract_tenant_from_idp(user_attributes: Dict[str, str]) -> Optional[str]:
    """
    Extract tenant ID from the identity provider name.
    
    Identity providers are named with a pattern: {IdP_TYPE}-{tenantId}
    Examples:
    - OKTA-aarohan -> aarohan
    - SAML-acme-corp -> acme-corp
    - AZURE_AD-mycompany -> mycompany
    
    Returns:
        Tenant ID if found, None otherwise
    """
    identities_raw = user_attributes.get('identities', '')
    
    if not identities_raw:
        return None
    
    try:
        identities = json.loads(identities_raw)
        if identities and isinstance(identities, list):
            provider_name = identities[0].get('providerName', '')
            
            if not provider_name:
                return None
            
            # Provider names follow pattern: PREFIX-tenantId
            # e.g., OKTA-aarohan, SAML-acme, AZURE_AD-mycompany
            prefixes = ['OKTA-', 'SAML-', 'OIDC-', 'AZURE_AD-', 'GOOGLE-', 'PING-']
            
            for prefix in prefixes:
                if provider_name.upper().startswith(prefix):
                    # Find position of the prefix (case-insensitive)
                    idx = provider_name.upper().find(prefix)
                    if idx != -1:
                        tenant_id = provider_name[idx + len(prefix):]
                        if tenant_id:
                            logger.debug(f"Extracted tenant '{tenant_id}' from provider '{provider_name}'")
                            return tenant_id.lower()
            
            # If no known prefix, try generic dash split
            if '-' in provider_name:
                parts = provider_name.split('-', 1)
                if len(parts) == 2 and parts[1]:
                    logger.debug(f"Extracted tenant '{parts[1]}' from provider '{provider_name}' (generic)")
                    return parts[1].lower()
                    
    except json.JSONDecodeError:
        pass
    
    return None


def _sync_groups_to_platform(tenant_id: str, groups: List[str], idp_type: str) -> None:
    """
    Call platform service to sync groups.
    
    This is a fire-and-forget operation - we don't block login on sync failure.
    """
    try:
        url = f"{PLATFORM_SERVICE_URL}/internal/groups/sync"
        
        payload = {
            'tenantId': tenant_id,
            'groups': groups,
            'idpType': idp_type
        }
        
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                'Content-Type': 'application/json',
                'X-Internal-Request': 'true'
            },
            method='POST'
        )
        
        with urllib.request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                logger.info(f"Successfully synced {len(groups)} groups for tenant {tenant_id}")
            else:
                logger.warning(f"Group sync returned status {response.status}")
                
    except urllib.error.URLError as e:
        logger.warning(f"Failed to sync groups (non-blocking): {str(e)}")
    except Exception as e:
        logger.error(f"Unexpected error syncing groups: {str(e)}")


# =============================================================================
# JIT PROVISIONING FUNCTIONS
# =============================================================================

def _jit_provision_if_needed(
    tenant_id: str, 
    email: str, 
    user_sub: str, 
    groups: List[str],
    idp_type: str
) -> None:
    """
    JIT (Just-In-Time) provision a new SSO user if they don't already exist.
    
    This function is called on every SSO login to check if the user exists
    in the tenant's user registry. If not, it provisions them with an
    appropriate role based on their group memberships.
    
    This is a fire-and-forget operation - login is not blocked on failure.
    
    Args:
        tenant_id: The tenant ID
        email: User email from SSO claims
        user_sub: Cognito user sub (user ID)
        groups: List of IdP group IDs the user belongs to
        idp_type: The identity provider type (SAML, OIDC, OKTA, etc.)
    """
    try:
        # Step 1: Check if user already exists
        if _check_user_exists(tenant_id, email):
            logger.debug(f"User {email} already exists in tenant {tenant_id}, skipping JIT provision")
            return
        
        # Step 2: Resolve role from groups (if mappings exist)
        role_id = _resolve_role_from_groups(groups)
        if not role_id:
            role_id = 'viewer'  # Default role for SSO users with no group mapping
            logger.info(f"No group mapping found for {email}, using default role: {role_id}")
        
        # Step 3: Provision the user
        _provision_user(tenant_id, email, user_sub, role_id, idp_type)
        
        logger.info(
            f"Successfully JIT provisioned SSO user: email={email}, "
            f"tenant={tenant_id}, role={role_id}, idpType={idp_type}"
        )
        
    except Exception as e:
        # Log error but don't block SSO login
        logger.error(f"JIT provision failed for {email} (non-blocking): {str(e)}", exc_info=True)


def _check_user_exists(tenant_id: str, email: str) -> bool:
    """
    Check if a user exists in the tenant's user registry.
    
    Calls the platform-service internal API to check user existence.
    
    Args:
        tenant_id: The tenant ID
        email: User email to check
        
    Returns:
        True if user exists, False otherwise
    """
    try:
        url = f"{PLATFORM_SERVICE_URL}/internal/users/exists?tenantId={tenant_id}&email={email}"
        
        req = urllib.request.Request(
            url,
            headers={
                'Content-Type': 'application/json',
                'X-Internal-Request': 'true'
            },
            method='GET'
        )
        
        with urllib.request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                result = json.loads(response.read().decode('utf-8'))
                return result.get('exists', False)
            return False
            
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return False
        logger.warning(f"HTTP error checking user existence: {e.code}")
        return False
    except Exception as e:
        logger.warning(f"Error checking user existence: {str(e)}")
        return False  # Assume doesn't exist, provision attempt will fail gracefully


def _resolve_role_from_groups(groups: List[str]) -> Optional[str]:
    """
    Resolve the appropriate role for a user based on their IdP group memberships.
    
    Calls the auth-service internal API to find matching group-role mappings.
    The highest priority matching role is returned.
    
    Args:
        groups: List of external group IDs from the IdP
        
    Returns:
        Role ID if a mapping is found, None otherwise
    """
    if not groups:
        return None
        
    try:
        url = f"{AUTH_SERVICE_URL}/internal/groups/resolve-role"
        
        payload = {'groups': groups}
        data = json.dumps(payload).encode('utf-8')
        
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                'Content-Type': 'application/json',
                'X-Internal-Request': 'true'
            },
            method='POST'
        )
        
        with urllib.request.urlopen(req, timeout=5) as response:
            if response.status == 200:
                result = json.loads(response.read().decode('utf-8'))
                if result.get('matched'):
                    role_id = result.get('roleId')
                    logger.debug(f"Resolved role '{role_id}' for groups: {groups}")
                    return role_id
        
        return None
        
    except Exception as e:
        logger.warning(f"Error resolving role from groups: {str(e)}")
        return None  # Caller will use default role


def _provision_user(
    tenant_id: str, 
    email: str, 
    user_sub: str, 
    role_id: str,
    source: str
) -> None:
    """
    Provision a new SSO user via auth-service sso-complete endpoint.
    
    This calls the unified signup pipeline in auth-service which:
    - Creates tenant (if needed for personal accounts)
    - Creates membership in platform DB
    - Assigns user_roles in tenant DB
    
    Args:
        tenant_id: The tenant ID
        email: User email
        user_sub: Cognito user sub (user ID)
        role_id: Role to assign
        source: User source (SAML, OIDC, OKTA, GOOGLE, etc.)
    """
    # Call auth-service sso-complete endpoint (unified signup pipeline)
    url = f"{AUTH_SERVICE_URL}/api/v1/auth/sso-complete"
    
    payload = {
        'tenantId': tenant_id,
        'email': email,
        'cognitoUserId': user_sub,
        'source': source,
        'defaultRole': role_id,
        'groups': []  # Groups are synced separately
    }
    
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            'Content-Type': 'application/json',
            'X-Internal-Request': 'true'
        },
        method='POST'
    )
    
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            if response.status in (200, 201):
                result = json.loads(response.read().decode('utf-8'))
                if result.get('success'):
                    logger.info(f"SSO user provisioned successfully: email={email} tenant={tenant_id}")
                else:
                    logger.warning(f"SSO provision returned success=false: {result.get('message')}")
            else:
                logger.warning(f"SSO provision unexpected status: {response.status}")
    except urllib.error.HTTPError as e:
        # Log error body for debugging
        error_body = e.read().decode('utf-8') if e.fp else 'No body'
        logger.error(f"SSO provision HTTP error {e.code}: {error_body}")
        raise
    except Exception as e:
        logger.error(f"SSO provision error: {str(e)}")
        raise



def _determine_tenant_id(
    selected: Optional[str],
    stored: Optional[str],
    username: str
) -> Optional[str]:
    """
    Determine which tenant ID to use.
    
    Priority:
    1. Selected tenant (from clientMetadata) if provided
    2. Stored tenant (from user attributes)
    """
    if selected and selected.strip():
        if selected != stored:
            logger.info(f"Tenant override for {username}: {stored} -> {selected}")
        return selected.strip()
    
    return stored.strip() if stored else None
