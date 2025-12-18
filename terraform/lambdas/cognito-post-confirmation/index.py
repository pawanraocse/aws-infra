"""
AWS Lambda function for Cognito PostConfirmation trigger.

This function is invoked automatically by Cognito after a user confirms their email.
It sets custom attributes (tenantId and role) that were passed during signup.

Security:
- Only processes confirmed users (Cognito guarantees this)
- Validates all inputs before processing
- Logs errors to CloudWatch for monitoring
- Does not block user confirmation on failure (graceful degradation)
"""

import json
import logging
import os
import boto3
from botocore.exceptions import ClientError

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Initialize Cognito client
cognito_client = boto3.client('cognito-idp')

def lambda_handler(event, context):
    """
    Handle Cognito PostConfirmation trigger.
    
    Args:
        event: Cognito trigger event containing user info and metadata
        context: Lambda context (unused)
    
    Returns:
        event: Must return the event for Cognito to proceed
    """
    try:
        logger.info(f"PostConfirmation triggered for user: {event.get('userName')}")
        
        # Extract event data
        user_pool_id = event.get('userPoolId')
        username = event.get('userName')
        trigger_source = event.get('triggerSource')
        
        # Validate trigger source
        if trigger_source != 'PostConfirmation_ConfirmSignUp':
            logger.warning(f"Unexpected trigger source: {trigger_source}")
            return event
        
        # Extract client metadata (passed during signup)
        # NOTE: tenantType and role are NOT stored in Cognito - managed in tenant DB
        client_metadata = event.get('request', {}).get('clientMetadata', {})
        tenant_id = client_metadata.get('tenantId')
        
        # Validate required data
        if not tenant_id:
            logger.error(f"Missing tenantId for user {username}")
            # Don't block user - they can still login, admin can fix manually
            return event
        
        # Update user attributes with custom claims (only tenantId)
        update_user_attributes(user_pool_id, username, tenant_id)
        
        logger.info(f"Successfully set tenantId={tenant_id} for user {username}")
        
    except Exception as e:
        # Log error but don't raise - we don't want to block user confirmation
        logger.error(f"Error in PostConfirmation handler: {str(e)}", exc_info=True)
    
    # Always return event to allow Cognito to proceed
    return event


def update_user_attributes(user_pool_id, username, tenant_id):
    """
    Update Cognito user with custom attributes.
    
    NOTE: Only tenantId is stored in Cognito. Role is managed in tenant DB.
    
    Args:
        user_pool_id: Cognito User Pool ID
        username: User's username (email)
        tenant_id: Tenant ID to assign
    
    Raises:
        ClientError: If Cognito API call fails
    """
    try:
        cognito_client.admin_update_user_attributes(
            UserPoolId=user_pool_id,
            Username=username,
            UserAttributes=[
                {
                    'Name': 'custom:tenantId',
                    'Value': tenant_id
                }
            ]
        )
        logger.info(f"Updated tenantId for user {username}: {tenant_id}")
        
    except ClientError as e:
        error_code = e.response['Error']['Code']
        logger.error(f"Cognito API error ({error_code}): {e.response['Error']['Message']}")
        raise
