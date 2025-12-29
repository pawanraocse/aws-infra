"""
Unit tests for the Cognito PreTokenGeneration Lambda handler.
Tests group extraction, IdP detection, and claim generation.
"""
import json
import unittest
from unittest.mock import patch, MagicMock

import handler


class TestGroupExtraction(unittest.TestCase):
    """Tests for _extract_groups_from_claims function."""

    def test_extract_comma_separated_groups(self):
        """Should extract groups from comma-separated string."""
        user_attributes = {'custom:groups': 'Engineering,Marketing,Sales'}
        result = handler._extract_groups_from_claims(user_attributes)
        self.assertEqual(result, ['Engineering', 'Marketing', 'Sales'])

    def test_extract_json_array_groups(self):
        """Should extract groups from JSON array format."""
        user_attributes = {'custom:groups': '["Engineering","Marketing"]'}
        result = handler._extract_groups_from_claims(user_attributes)
        self.assertEqual(result, ['Engineering', 'Marketing'])

    def test_extract_groups_with_spaces(self):
        """Should trim whitespace from group names."""
        user_attributes = {'custom:groups': ' Engineering , Marketing '}
        result = handler._extract_groups_from_claims(user_attributes)
        self.assertEqual(result, ['Engineering', 'Marketing'])

    def test_empty_groups(self):
        """Should return empty list when no groups."""
        user_attributes = {}
        result = handler._extract_groups_from_claims(user_attributes)
        self.assertEqual(result, [])

    def test_deduplicate_groups(self):
        """Should deduplicate groups."""
        user_attributes = {'custom:groups': 'Engineering,Engineering,Marketing'}
        result = handler._extract_groups_from_claims(user_attributes)
        self.assertEqual(result, ['Engineering', 'Marketing'])

    def test_fallback_claim_attributes(self):
        """Should check multiple claim attribute names."""
        user_attributes = {'cognito:groups': 'CognitoGroup1'}
        result = handler._extract_groups_from_claims(user_attributes)
        self.assertEqual(result, ['CognitoGroup1'])


class TestIdpTypeDetection(unittest.TestCase):
    """Tests for _detect_idp_type function."""

    def test_detect_okta(self):
        """Should detect Okta IdP."""
        user_attributes = {
            'identities': '[{"providerName":"Okta","userId":"123"}]'
        }
        result = handler._detect_idp_type(user_attributes)
        self.assertEqual(result, 'OKTA')

    def test_detect_azure_ad(self):
        """Should detect Azure AD IdP."""
        user_attributes = {
            'identities': '[{"providerName":"AzureAD","userId":"123"}]'
        }
        result = handler._detect_idp_type(user_attributes)
        self.assertEqual(result, 'AZURE_AD')

    def test_detect_google(self):
        """Should detect Google IdP."""
        user_attributes = {
            'identities': '[{"providerName":"Google","userId":"123"}]'
        }
        result = handler._detect_idp_type(user_attributes)
        self.assertEqual(result, 'GOOGLE')

    def test_detect_saml_from_attributes(self):
        """Should detect SAML from attribute prefix."""
        user_attributes = {'saml:subject': 'user@example.com'}
        result = handler._detect_idp_type(user_attributes)
        self.assertEqual(result, 'SAML')

    def test_default_to_oidc(self):
        """Should default to OIDC when unknown."""
        user_attributes = {}
        result = handler._detect_idp_type(user_attributes)
        self.assertEqual(result, 'OIDC')


class TestLambdaHandler(unittest.TestCase):
    """Tests for the main lambda_handler function."""

    def test_basic_token_generation(self):
        """Should set claims in response."""
        event = {
            'userName': 'testuser',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'tenant-123',
                    'custom:tenantType': 'ORGANIZATION'
                },
                'clientMetadata': {}
            },
            'response': {}
        }
        
        result = handler.lambda_handler(event, None)
        
        self.assertIn('claimsOverrideDetails', result['response'])
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        self.assertEqual(claims['custom:tenantId'], 'tenant-123')
        self.assertNotIn('custom:role', claims)  # Role no longer in JWT

    def test_tenant_override(self):
        """Should override tenant from clientMetadata."""
        event = {
            'userName': 'testuser',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'original-tenant'
                },
                'clientMetadata': {
                    'selectedTenantId': 'new-tenant'
                }
            },
            'response': {}
        }
        
        result = handler.lambda_handler(event, None)
        
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        self.assertEqual(claims['custom:tenantId'], 'new-tenant')

    def test_groups_added_to_claims(self):
        """Should add groups to response claims."""
        event = {
            'userName': 'testuser',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': {
                'userAttributes': {
                    'custom:tenantId': 'tenant-123',
                    'custom:groups': 'Engineering,Marketing'
                },
                'clientMetadata': {}
            },
            'response': {}
        }
        
        with patch.object(handler, '_sync_groups_to_platform'):
            result = handler.lambda_handler(event, None)
        
        claims = result['response']['claimsOverrideDetails']['claimsToAddOrOverride']
        self.assertEqual(claims['custom:groups'], 'Engineering,Marketing')

    def test_skip_non_auth_triggers(self):
        """Should skip non-token-generation triggers."""
        event = {
            'userName': 'testuser',
            'triggerSource': 'PreSignUp_SignUp',
            'request': {},
            'response': {}
        }
        
        result = handler.lambda_handler(event, None)
        
        self.assertNotIn('claimsOverrideDetails', result.get('response', {}))

    def test_graceful_error_handling(self):
        """Should not block auth on errors."""
        event = {
            'userName': 'testuser',
            'triggerSource': 'TokenGeneration_Authentication',
            'request': None  # Invalid - will cause error
        }
        
        # Should not raise, just return event
        result = handler.lambda_handler(event, None)
        self.assertIsNotNone(result)


class TestTenantIdDetermination(unittest.TestCase):
    """Tests for _determine_tenant_id function."""

    def test_prefer_selected_over_stored(self):
        """Should prefer selected tenant over stored."""
        result = handler._determine_tenant_id('selected', 'stored', 'user')
        self.assertEqual(result, 'selected')

    def test_fallback_to_stored(self):
        """Should fall back to stored when no selection."""
        result = handler._determine_tenant_id(None, 'stored', 'user')
        self.assertEqual(result, 'stored')

    def test_return_none_when_both_missing(self):
        """Should return None when both missing."""
        result = handler._determine_tenant_id(None, None, 'user')
        self.assertIsNone(result)

    def test_strip_whitespace(self):
        """Should strip whitespace from tenant IDs."""
        result = handler._determine_tenant_id(' selected ', None, 'user')
        self.assertEqual(result, 'selected')


class TestJitProvisioning(unittest.TestCase):
    """Tests for JIT provisioning functions."""

    @patch.object(handler, '_check_user_exists')
    @patch.object(handler, '_resolve_role_from_groups')
    @patch.object(handler, '_provision_user')
    def test_jit_provision_new_sso_user(self, mock_provision, mock_resolve, mock_exists):
        """Should provision new SSO user with resolved role."""
        mock_exists.return_value = False
        mock_resolve.return_value = 'editor'
        
        handler._jit_provision_if_needed(
            tenant_id='tenant-123',
            email='user@example.com',
            user_sub='sub-456',
            groups=['engineering', 'developers'],
            idp_type='OKTA'
        )
        
        mock_exists.assert_called_once_with('tenant-123', 'user@example.com')
        mock_resolve.assert_called_once_with(['engineering', 'developers'])
        mock_provision.assert_called_once_with(
            'tenant-123', 'user@example.com', 'sub-456', 'editor', 'OKTA'
        )

    @patch.object(handler, '_check_user_exists')
    @patch.object(handler, '_provision_user')
    def test_skip_existing_user(self, mock_provision, mock_exists):
        """Should skip provisioning when user already exists."""
        mock_exists.return_value = True
        
        handler._jit_provision_if_needed(
            tenant_id='tenant-123',
            email='existing@example.com',
            user_sub='sub-789',
            groups=['admins'],
            idp_type='AZURE_AD'
        )
        
        mock_provision.assert_not_called()

    @patch.object(handler, '_check_user_exists')
    @patch.object(handler, '_resolve_role_from_groups')
    @patch.object(handler, '_provision_user')
    def test_default_role_when_no_mapping(self, mock_provision, mock_resolve, mock_exists):
        """Should use default 'viewer' role when no group mapping found."""
        mock_exists.return_value = False
        mock_resolve.return_value = None  # No mapping found
        
        handler._jit_provision_if_needed(
            tenant_id='tenant-123',
            email='newuser@example.com',
            user_sub='sub-abc',
            groups=['unknown-group'],
            idp_type='SAML'
        )
        
        # Should provision with 'viewer' as default role
        mock_provision.assert_called_once_with(
            'tenant-123', 'newuser@example.com', 'sub-abc', 'viewer', 'SAML'
        )

    @patch.object(handler, '_check_user_exists')
    def test_jit_provision_error_handling(self, mock_exists):
        """Should not block login on JIT provision error."""
        mock_exists.side_effect = Exception('API connection failed')
        
        # Should not raise exception
        handler._jit_provision_if_needed(
            tenant_id='tenant-123',
            email='error@example.com',
            user_sub='sub-err',
            groups=['admins'],
            idp_type='GOOGLE'
        )
        # Test passes if no exception raised

    def test_resolve_role_empty_groups(self):
        """Should return None for empty groups list."""
        result = handler._resolve_role_from_groups([])
        self.assertIsNone(result)

    def test_resolve_role_none_groups(self):
        """Should return None for None groups."""
        result = handler._resolve_role_from_groups(None)
        self.assertIsNone(result)


if __name__ == '__main__':
    unittest.main()

