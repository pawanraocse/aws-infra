# =============================================================================
# Cognito Identity Provider Module - Main Resources
# =============================================================================
# This module creates:
# - SAML Identity Providers (Okta, Azure AD, Ping, custom SAML)
# - OIDC Identity Providers (Google, custom OIDC)
# - Configures attribute mappings
# =============================================================================

# -----------------------------------------------------------------------------
# SAML Identity Provider
# -----------------------------------------------------------------------------
# Creates a SAML 2.0 identity provider for enterprise SSO
# Used by: Okta, Azure AD, Ping Identity, custom SAML IdPs

resource "aws_cognito_identity_provider" "saml" {
  count = var.saml_provider_enabled ? 1 : 0

  user_pool_id  = var.user_pool_id
  provider_name = var.saml_provider_name
  provider_type = "SAML"

  provider_details = merge(
    # Metadata source (either URL or inline XML)
    var.saml_metadata_url != "" ? {
      MetadataURL = var.saml_metadata_url
      } : {
      MetadataFile = var.saml_metadata_xml
    },
    {
      IDPSignout              = tostring(var.saml_idp_signout)
      RequestSigningAlgorithm = var.saml_request_signing_algorithm
    }
  )

  attribute_mapping = merge(
    # Default mappings
    {
      email    = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"
      name     = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name"
      username = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier"
    },
    # Custom role/tenant mappings
    var.saml_attribute_mapping
  )

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Google OIDC Identity Provider
# -----------------------------------------------------------------------------
# Native Google integration for "Sign in with Google"

resource "aws_cognito_identity_provider" "google" {
  count = var.google_provider_enabled ? 1 : 0

  user_pool_id  = var.user_pool_id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id                     = var.google_client_id
    client_secret                 = var.google_client_secret
    authorize_scopes              = var.google_scopes
    attributes_url                = "https://people.googleapis.com/v1/people/me?personFields="
    attributes_url_add_attributes = "true"
    authorize_url                 = "https://accounts.google.com/o/oauth2/v2/auth"
    oidc_issuer                   = "https://accounts.google.com"
    token_request_method          = "POST"
    token_url                     = "https://www.googleapis.com/oauth2/v4/token"
  }

  attribute_mapping = merge(
    {
      email    = "email"
      name     = "name"
      username = "sub"
      picture  = "picture"
    },
    var.google_attribute_mapping
  )

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Generic OIDC Identity Provider
# -----------------------------------------------------------------------------
# Supports custom OIDC providers like Okta OIDC, Auth0, etc.

resource "aws_cognito_identity_provider" "oidc" {
  count = var.oidc_provider_enabled ? 1 : 0

  user_pool_id  = var.user_pool_id
  provider_name = var.oidc_provider_name
  provider_type = "OIDC"

  provider_details = {
    client_id                 = var.oidc_client_id
    client_secret             = var.oidc_client_secret
    authorize_scopes          = var.oidc_scopes
    oidc_issuer               = var.oidc_issuer_url
    attributes_request_method = var.oidc_attributes_request_method
    # Auto-discover endpoints from .well-known
  }

  attribute_mapping = merge(
    {
      email    = "email"
      name     = "name"
      username = "sub"
    },
    var.oidc_attribute_mapping
  )

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Update User Pool Client to support identity providers
# -----------------------------------------------------------------------------
# This data source fetches existing client info

data "aws_cognito_user_pool_clients" "existing" {
  user_pool_id = var.user_pool_id
}

# NOTE: The user pool client must be updated separately to add
# supported_identity_providers. This can be done via:
# 1. Parent module managing the client
# 2. AWS CLI: aws cognito-idp update-user-pool-client
# 3. Platform-service API managing dynamically
