"""
AWS Lambda function for Cognito PreTokenGeneration trigger.

This function is invoked by Cognito before generating tokens during authentication.
It allows overriding custom claims (like tenantId) based on user selection at login time.

Use Case:
---------
When a user belongs to multiple tenants, they select which tenant to access during login.
The selected tenantId is passed via clientMetadata and this Lambda injects it into the JWT.

Security:
---------
- Only processes authentication events (not signup)
- Falls back to stored tenantId if no selection provided
- Logs operations for audit trail
- Does not block authentication on errors (graceful degradation)

Event Structure:
----------------
{
    "userName": "user@example.com",
    "triggerSource": "TokenGeneration_Authentication",
    "request": {
        "userAttributes": {
            "custom:tenantId": "stored-tenant-id",
            "custom:role": "tenant-admin",
            "custom:tenantType": "ORGANIZATION"
        },
        "clientMetadata": {
            "selectedTenantId": "user-selected-tenant-id"
        }
    },
    "response": {}
}
"""

import logging
from typing import Any, Dict, Optional

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Valid trigger sources for token generation
TOKEN_GENERATION_TRIGGERS = frozenset([
    'TokenGeneration_Authentication',
    'TokenGeneration_RefreshTokens',
    'TokenGeneration_HostedAuth',
    'TokenGeneration_NewPasswordChallenge'
])


def lambda_handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    """
    Handle Cognito PreTokenGeneration trigger.
    
    Allows overriding custom:tenantId in the JWT if user selected a different
    tenant at login time via clientMetadata.
    
    Args:
        event: Cognito trigger event containing user info and metadata
        context: Lambda context (unused)
    
    Returns:
        event: Modified event with claimsOverrideDetails if applicable
    """
    try:
        username = event.get('userName', 'unknown')
        trigger_source = event.get('triggerSource', '')
        
        logger.info(
            "PreTokenGeneration triggered",
            extra={
                'user': username,
                'trigger': trigger_source
            }
        )
        
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
        stored_role = user_attributes.get('custom:role', 'tenant-user')
        stored_tenant_type = user_attributes.get('custom:tenantType', 'PERSONAL')
        
        # Determine final tenant ID
        final_tenant_id = _determine_tenant_id(
            selected_tenant_id, 
            stored_tenant_id, 
            username
        )
        
        if not final_tenant_id:
            logger.warning(f"No tenantId found for user: {username}")
            return event
        
        # Build claims override
        claims_to_override = {
            'custom:tenantId': final_tenant_id,
            'custom:role': stored_role,
            'custom:tenantType': stored_tenant_type
        }
        
        # Set the override in the response
        event['response'] = event.get('response', {})
        event['response']['claimsOverrideDetails'] = {
            'claimsToAddOrOverride': claims_to_override
        }
        
        logger.info(
            "Token claims set",
            extra={
                'user': username,
                'tenantId': final_tenant_id,
                'wasOverridden': selected_tenant_id is not None and selected_tenant_id != stored_tenant_id
            }
        )
        
    except Exception as e:
        # Log error but don't block authentication
        logger.error(
            f"Error in PreTokenGeneration: {str(e)}",
            exc_info=True
        )
    
    return event


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
    
    Args:
        selected: Tenant ID selected at login time (may be None)
        stored: Tenant ID stored in Cognito user attributes
        username: Username for logging
    
    Returns:
        The tenant ID to use, or None if neither available
    """
    if selected and selected.strip():
        if selected != stored:
            logger.info(
                f"Tenant override for {username}: {stored} -> {selected}"
            )
        return selected.strip()
    
    return stored.strip() if stored else None
