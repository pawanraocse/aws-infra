"""
Unit tests for the PreTokenGeneration Lambda handler.
"""

import pytest
from handler import lambda_handler, _determine_tenant_id


class TestLambdaHandler:
    """Tests for the main lambda_handler function."""
    
    def test_authentication_trigger_with_selected_tenant(self):
        """Should override tenantId when user selects a different tenant."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'stored-tenant',
                    'custom:role': 'admin',
                    'custom:tenantType': 'ORGANIZATION'
                },
                'clientMetadata': {
                    'selectedTenantId': 'selected-tenant'
                }
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        assert 'claimsOverrideDetails' in result['response']
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        assert claims['custom:tenantId'] == 'selected-tenant'
        assert claims['custom:role'] == 'admin'
        assert claims['custom:tenantType'] == 'ORGANIZATION'
    
    def test_authentication_trigger_without_selection_uses_stored(self):
        """Should use stored tenantId when no selection provided."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'stored-tenant',
                    'custom:role': 'owner',
                    'custom:tenantType': 'PERSONAL'
                },
                'clientMetadata': {}
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        assert claims['custom:tenantId'] == 'stored-tenant'
    
    def test_refresh_token_trigger_works(self):
        """Should handle refresh token trigger source."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'TokenGeneration_RefreshTokens',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'stored-tenant',
                    'custom:role': 'member',
                    'custom:tenantType': 'ORGANIZATION'
                },
                'clientMetadata': None
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        assert claims['custom:tenantId'] == 'stored-tenant'
    
    def test_non_token_trigger_skipped(self):
        """Should skip non-token-generation triggers."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'PostConfirmation_ConfirmSignUp',
            'request': {
                'userAttributes': {'custom:tenantId': 'stored-tenant'},
                'clientMetadata': {'selectedTenantId': 'selected'}
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        # Should not have any claims override
        assert 'claimsOverrideDetails' not in result.get('response', {})
    
    def test_missing_tenant_id_logs_warning(self):
        """Should handle missing tenantId gracefully."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {},
                'clientMetadata': {}
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        # Should not crash, should not have claims override
        assert 'claimsOverrideDetails' not in result.get('response', {})
    
    def test_default_role_and_type_used_when_missing(self):
        """Should use defaults for role and tenantType if not in attributes."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'some-tenant'
                },
                'clientMetadata': {}
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        assert claims['custom:role'] == 'tenant-user'
        assert claims['custom:tenantType'] == 'PERSONAL'
    
    def test_whitespace_only_selected_tenant_uses_stored(self):
        """Should ignore whitespace-only selectedTenantId."""
        event = {
            'userName': 'test@example.com',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'stored-tenant',
                    'custom:role': 'admin',
                    'custom:tenantType': 'ORGANIZATION'
                },
                'clientMetadata': {
                    'selectedTenantId': '   '
                }
            },
            'response': {}
        }
        
        result = lambda_handler(event, None)
        
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        assert claims['custom:tenantId'] == 'stored-tenant'


class TestDetermineTenantId:
    """Tests for the _determine_tenant_id helper function."""
    
    def test_selected_takes_priority(self):
        """Selected tenant should override stored."""
        result = _determine_tenant_id('selected', 'stored', 'user')
        assert result == 'selected'
    
    def test_stored_used_when_no_selection(self):
        """Stored tenant used when no selection."""
        result = _determine_tenant_id(None, 'stored', 'user')
        assert result == 'stored'
    
    def test_empty_selected_uses_stored(self):
        """Empty string selection uses stored."""
        result = _determine_tenant_id('', 'stored', 'user')
        assert result == 'stored'
    
    def test_none_when_both_missing(self):
        """Returns None when both are missing."""
        result = _determine_tenant_id(None, None, 'user')
        assert result is None
    
    def test_strips_whitespace(self):
        """Should strip whitespace from tenant IDs."""
        result = _determine_tenant_id('  selected  ', None, 'user')
        assert result == 'selected'
