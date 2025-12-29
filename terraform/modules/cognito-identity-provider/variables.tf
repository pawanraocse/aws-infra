# =============================================================================
# Cognito Identity Provider Module - Variables
# =============================================================================

# -----------------------------------------------------------------------------
# Core Configuration
# -----------------------------------------------------------------------------

variable "user_pool_id" {
  description = "Cognito User Pool ID to attach identity providers to"
  type        = string
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = ""
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = ""
}

# -----------------------------------------------------------------------------
# SAML Provider Configuration
# -----------------------------------------------------------------------------

variable "saml_provider_enabled" {
  description = "Enable SAML identity provider"
  type        = bool
  default     = false
}

variable "saml_provider_name" {
  description = "Name for the SAML provider (must be unique within user pool)"
  type        = string
  default     = "SAML"
  validation {
    condition     = can(regex("^[a-zA-Z0-9_]+$", var.saml_provider_name))
    error_message = "Provider name must be alphanumeric with underscores only."
  }
}

variable "saml_metadata_url" {
  description = "URL to SAML IdP metadata XML (mutually exclusive with saml_metadata_xml)"
  type        = string
  default     = ""
}

variable "saml_metadata_xml" {
  description = "Inline SAML IdP metadata XML content (mutually exclusive with saml_metadata_url)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "saml_idp_signout" {
  description = "Enable IdP-initiated sign-out"
  type        = bool
  default     = false
}

variable "saml_request_signing_algorithm" {
  description = "SAML request signing algorithm (rsa-sha256)"
  type        = string
  default     = "rsa-sha256"
}

variable "saml_attribute_mapping" {
  description = "Additional SAML attribute mappings (Cognito attr => SAML claim)"
  type        = map(string)
  default = {
    "custom:groups" = "http://schemas.microsoft.com/ws/2008/06/identity/claims/groups"
  }
}

# -----------------------------------------------------------------------------
# Google OIDC Provider Configuration
# -----------------------------------------------------------------------------

variable "google_provider_enabled" {
  description = "Enable Google identity provider"
  type        = bool
  default     = false
}

variable "google_client_id" {
  description = "Google OAuth client ID"
  type        = string
  default     = ""
}

variable "google_client_secret" {
  description = "Google OAuth client secret"
  type        = string
  default     = ""
  sensitive   = true
}

variable "google_scopes" {
  description = "Google OAuth scopes"
  type        = string
  default     = "openid email profile"
}

variable "google_attribute_mapping" {
  description = "Additional Google attribute mappings"
  type        = map(string)
  default     = {}
}

# -----------------------------------------------------------------------------
# Generic OIDC Provider Configuration
# -----------------------------------------------------------------------------

variable "oidc_provider_enabled" {
  description = "Enable generic OIDC identity provider"
  type        = bool
  default     = false
}

variable "oidc_provider_name" {
  description = "Name for the OIDC provider (must be unique within user pool)"
  type        = string
  default     = "OIDC"
  validation {
    condition     = can(regex("^[a-zA-Z0-9_]+$", var.oidc_provider_name))
    error_message = "Provider name must be alphanumeric with underscores only."
  }
}

variable "oidc_issuer_url" {
  description = "OIDC issuer URL (e.g., https://dev-xxx.okta.com)"
  type        = string
  default     = ""
}

variable "oidc_client_id" {
  description = "OIDC client ID"
  type        = string
  default     = ""
}

variable "oidc_client_secret" {
  description = "OIDC client secret"
  type        = string
  default     = ""
  sensitive   = true
}

variable "oidc_scopes" {
  description = "OIDC scopes"
  type        = string
  default     = "openid email profile"
}

variable "oidc_attributes_request_method" {
  description = "Method to request OIDC attributes (GET or POST)"
  type        = string
  default     = "GET"
}

variable "oidc_attribute_mapping" {
  description = "Additional OIDC attribute mappings"
  type        = map(string)
  default = {
    "custom:groups" = "groups"
  }
}

# -----------------------------------------------------------------------------
# Tags
# -----------------------------------------------------------------------------

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}
