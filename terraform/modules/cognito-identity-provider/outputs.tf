# =============================================================================
# Cognito Identity Provider Module - Outputs
# =============================================================================

# -----------------------------------------------------------------------------
# SAML Provider Outputs
# -----------------------------------------------------------------------------

output "saml_provider_name" {
  description = "Name of the SAML identity provider"
  value       = var.saml_provider_enabled ? aws_cognito_identity_provider.saml[0].provider_name : null
}

output "saml_provider_type" {
  description = "Type of the SAML identity provider"
  value       = var.saml_provider_enabled ? aws_cognito_identity_provider.saml[0].provider_type : null
}

# -----------------------------------------------------------------------------
# Google Provider Outputs
# -----------------------------------------------------------------------------

output "google_provider_name" {
  description = "Name of the Google identity provider"
  value       = var.google_provider_enabled ? aws_cognito_identity_provider.google[0].provider_name : null
}

# -----------------------------------------------------------------------------
# OIDC Provider Outputs
# -----------------------------------------------------------------------------

output "oidc_provider_name" {
  description = "Name of the OIDC identity provider"
  value       = var.oidc_provider_enabled ? aws_cognito_identity_provider.oidc[0].provider_name : null
}

output "oidc_issuer_url" {
  description = "OIDC issuer URL"
  value       = var.oidc_provider_enabled ? var.oidc_issuer_url : null
}

# -----------------------------------------------------------------------------
# Convenience Outputs
# -----------------------------------------------------------------------------

output "enabled_providers" {
  description = "List of enabled identity provider names"
  value = compact([
    var.saml_provider_enabled ? var.saml_provider_name : "",
    var.google_provider_enabled ? "Google" : "",
    var.oidc_provider_enabled ? var.oidc_provider_name : ""
  ])
}

output "has_sso_providers" {
  description = "Whether any SSO providers are enabled"
  value       = var.saml_provider_enabled || var.google_provider_enabled || var.oidc_provider_enabled
}

# -----------------------------------------------------------------------------
# SP Metadata URLs (for IdP configuration)
# -----------------------------------------------------------------------------

output "saml_acs_url" {
  description = "SAML Assertion Consumer Service (ACS) URL - configure this in your IdP"
  value       = "https://${var.project_name}-${var.environment}.auth.${data.aws_region.current.id}.amazoncognito.com/saml2/idpresponse"
}

output "saml_entity_id" {
  description = "SAML Entity ID / Audience URI - configure this in your IdP"
  value       = "urn:amazon:cognito:sp:${var.user_pool_id}"
}

# Current region data source
data "aws_region" "current" {}
