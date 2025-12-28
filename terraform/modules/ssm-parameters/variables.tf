# =============================================================================
# SSM Parameters Module - Variables
# =============================================================================

variable "project_name" {
  description = "Project name used in SSM path prefix"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

# =============================================================================
# Cognito Configuration (from cognito-user-pool module)
# =============================================================================

variable "user_pool_id" {
  description = "Cognito User Pool ID"
  type        = string
}

variable "client_id" {
  description = "Cognito Client ID (native)"
  type        = string
}

variable "spa_client_id" {
  description = "Cognito SPA Client ID (for frontend - no secret)"
  type        = string
}

variable "client_secret" {
  description = "Cognito Client Secret"
  type        = string
  sensitive   = true
}

variable "issuer_uri" {
  description = "Cognito Issuer URI"
  type        = string
}

variable "jwks_uri" {
  description = "Cognito JWKS URI"
  type        = string
}

variable "domain" {
  description = "Cognito domain prefix"
  type        = string
}

variable "hosted_ui_url" {
  description = "Cognito Hosted UI URL"
  type        = string
}

variable "branding_id" {
  description = "Cognito Managed Login Branding ID"
  type        = string
}

variable "callback_url" {
  description = "Primary OAuth callback URL"
  type        = string
}

variable "logout_redirect_url" {
  description = "Primary logout redirect URL"
  type        = string
}

# =============================================================================
# SES Configuration
# =============================================================================

variable "ses_from_email" {
  description = "SES from email address"
  type        = string
  default     = ""
}

variable "ses_enabled" {
  description = "Whether SES is enabled"
  type        = bool
  default     = false
}
