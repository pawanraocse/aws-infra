"""
Unit tests for Cognito PostConfirmation Lambda handler.

Tests cover:
- Successful attribute update
- Missing tenantId handling
- Cognito API errors
- Invalid trigger sources
"""

import json
import pytest
from unittest.mock import Mock, patch, MagicMock
from botocore.exceptions import ClientError

# Import the handler
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))
from index import lambda_handler, update_user_attributes


class TestLambdaHandler:
    """Test suite for lambda_handler function"""
    
    def test_successful_attribute_update(self):
        """Test successful flow with valid tenantId and role"""
        # Given
        event = {
            'userPoolId': 'us-east-1_TEST123',
            'userName': 'test@example.com',
            'triggerSource': 'PostConfirmation_ConfirmSignUp',
            'request': {
                'clientMetadata': {
                    'tenantId': 'tenant-123',
                    'role': 'tenant-admin'
                }
            }
        }
        
        with patch('index.update_user_attributes') as mock_update:
            # When
            result = lambda_handler(event, None)
            
            # Then
            assert result == event
            mock_update.assert_called_once_with(
                'us-east-1_TEST123',
                'test@example.com',
                'tenant-123',
                'tenant-admin'
            )
    
    def test_missing_tenant_id(self):
        """Test graceful handling when tenantId is missing"""
        # Given
        event = {
            'userPoolId': 'us-east-1_TEST123',
            'userName': 'test@example.com',
            'triggerSource': 'PostConfirmation_ConfirmSignUp',
            'request': {
                'clientMetadata': {
                    'role': 'tenant-admin'
                    # tenantId missing
                }
            }
        }
        
        with patch('index.update_user_attributes') as mock_update:
            # When
            result = lambda_handler(event, None)
            
            # Then
            assert result == event
            mock_update.assert_not_called()  # Should not attempt update
    
    def test_default_role_when_missing(self):
        """Test that default role is used when not provided"""
        # Given
        event = {
            'userPoolId': 'us-east-1_TEST123',
            'userName': 'test@example.com',
            'triggerSource': 'PostConfirmation_ConfirmSignUp',
            'request': {
                'clientMetadata': {
                    'tenantId': 'tenant-123'
                    # role missing - should default to tenant-admin
                }
            }
        }
        
        with patch('index.update_user_attributes') as mock_update:
            # When
            result = lambda_handler(event, None)
            
            # Then
            mock_update.assert_called_once_with(
                'us-east-1_TEST123',
                'test@example.com',
                'tenant-123',
                'tenant-admin'  # Default role
            )
    
    def test_wrong_trigger_source(self):
        """Test that wrong trigger source is ignored"""
        # Given
        event = {
            'userPoolId': 'us-east-1_TEST123',
            'userName': 'test@example.com',
            'triggerSource': 'PreSignUp_SignUp',  # Wrong trigger
            'request': {
                'clientMetadata': {
                    'tenantId': 'tenant-123'
                }
            }
        }
        
        with patch('index.update_user_attributes') as mock_update:
            # When
            result = lambda_handler(event, None)
            
            # Then
            assert result == event
            mock_update.assert_not_called()
    
    def test_exception_does_not_block_confirmation(self):
        """Test that exceptions don't prevent user confirmation"""
        # Given
        event = {
            'userPoolId': 'us-east-1_TEST123',
            'userName': 'test@example.com',
            'triggerSource': 'PostConfirmation_ConfirmSignUp',
            'request': {
                'clientMetadata': {
                    'tenantId': 'tenant-123'
                }
            }
        }
        
        with patch('index.update_user_attributes', side_effect=Exception("API Error")):
            # When
            result = lambda_handler(event, None)
            
            # Then
            assert result == event  # Event still returned despite error


class TestUpdateUserAttributes:
    """Test suite for update_user_attributes function"""
    
    @patch('index.cognito_client')
    def test_successful_update(self, mock_cognito):
        """Test successful Cognito API call"""
        # Given
        mock_cognito.admin_update_user_attributes.return_value = {}
        
        # When
        update_user_attributes(
            'us-east-1_TEST123',
            'test@example.com',
            'tenant-123',
            'tenant-admin'
        )
        
        # Then
        mock_cognito.admin_update_user_attributes.assert_called_once_with(
            UserPoolId='us-east-1_TEST123',
            Username='test@example.com',
            UserAttributes=[
                {'Name': 'custom:tenantId', 'Value': 'tenant-123'},
                {'Name': 'custom:role', 'Value': 'tenant-admin'}
            ]
        )
    
    @patch('index.cognito_client')
    def test_cognito_api_error(self, mock_cognito):
        """Test handling of Cognito API errors"""
        # Given
        error_response = {
            'Error': {
                'Code': 'UserNotFoundException',
                'Message': 'User not found'
            }
        }
        mock_cognito.admin_update_user_attributes.side_effect = ClientError(
            error_response, 'AdminUpdateUserAttributes'
        )
        
        # When/Then
        with pytest.raises(ClientError):
            update_user_attributes(
                'us-east-1_TEST123',
                'test@example.com',
                'tenant-123',
                'tenant-admin'
            )


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
