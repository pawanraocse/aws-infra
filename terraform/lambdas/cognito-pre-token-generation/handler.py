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
import urllib.request
import urllib.error
from typing import Any, Dict, List, Optional

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Environment variables
PLATFORM_SERVICE_URL = os.environ.get('PLATFORM_SERVICE_URL', 'http://localhost:8082')
AUTH_SERVICE_URL = os.environ.get('AUTH_SERVICE_URL', 'http://localhost:8081')
ENABLE_GROUP_SYNC = os.environ.get('ENABLE_GROUP_SYNC', 'true').lower() == 'true'

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
    
    Steps:
    1. Extract tenant and role info
    2. Extract groups from SSO claims (if present)
    3. Sync groups to platform service
    4. Add groups to JWT claims
    
    Args:
        event: Cognito trigger event containing user info and metadata
        context: Lambda context (unused)
    
    Returns:
        event: Modified event with claimsOverrideDetails if applicable
    """
    try:
        username = event.get('userName', 'unknown')
        trigger_source = event.get('triggerSource', '')
        
        logger.info(f"PreTokenGeneration triggered for user: {username}, trigger: {trigger_source}")
        
        # Only process token generation events
        if trigger_source not in TOKEN_GENERATION_TRIGGERS:
            logger.debug(f"Skipping non-token-generation trigger: {trigger_source}")
            return event
        
        # Extract data from event
        request_data = event.get('request', {})
        client_metadata = request_data.get('clientMetadata', {}) or {}
        user_attributes = request_data.get('userAttributes', {}) or {}
        
        # Get selected tenant from login (if any)
        selected_tenant_id = client_metadata.get('selectedTenantId')
        
        # Get stored attributes from Cognito user
        stored_tenant_id = user_attributes.get('custom:tenantId')
        stored_tenant_type = user_attributes.get('custom:tenantType', 'PERSONAL')
        
        # Determine final tenant ID
        final_tenant_id = _determine_tenant_id(selected_tenant_id, stored_tenant_id, username)
        
        if not final_tenant_id:
            logger.warning(f"No tenantId found for user: {username}")
            return event
        
        # Extract groups from SSO claims
        groups = _extract_groups_from_claims(user_attributes)
        idp_type = _detect_idp_type(user_attributes)
        
        # Sync groups if enabled and groups found
        if ENABLE_GROUP_SYNC and groups and final_tenant_id:
            _sync_groups_to_platform(final_tenant_id, groups, idp_type)
        
        # Build claims override (role is managed in DB, not JWT)
        claims_to_override = {
            'custom:tenantId': final_tenant_id,
            'custom:tenantType': stored_tenant_type,
        }
        
        # Add groups to claims if present
        if groups:
            claims_to_override['custom:groups'] = ','.join(groups)
        
        # Set the override in the response
        event['response'] = event.get('response', {})
        event['response']['claimsOverrideDetails'] = {
            'claimsToAddOrOverride': claims_to_override
        }
        
        logger.info(
            f"Token claims set for {username}: tenantId={final_tenant_id}, "
            f"groups={len(groups) if groups else 0}"
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
