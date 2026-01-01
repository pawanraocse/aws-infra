# =============================================================================
# Cognito User Pool Module - Variables
# =============================================================================

variable "project_name" {
  description = "Project name used in resource naming"
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

variable "aws_account_id" {
  description = "AWS Account ID"
  type        = string
}

# =============================================================================
# OAuth Configuration
# =============================================================================

variable "callback_urls" {
  description = "OAuth callback URLs"
  type        = list(string)
}

variable "logout_urls" {
  description = "OAuth logout URLs"
  type        = list(string)
}

variable "identity_providers" {
  description = "List of identity provider names to support (e.g., SAML/OIDC provider names created dynamically)"
  type        = list(string)
  default     = []
}

# =============================================================================
# Token Validity
# =============================================================================

variable "access_token_validity" {
  description = "Access token validity in minutes"
  type        = number
  default     = 60
}

variable "id_token_validity" {
  description = "ID token validity in minutes"
  type        = number
  default     = 60
}

variable "refresh_token_validity" {
  description = "Refresh token validity in days"
  type        = number
  default     = 30
}

# =============================================================================
# SES Email Configuration
# =============================================================================

variable "enable_ses_email" {
  description = "Enable SES for email sending (requires verified SES identity)"
  type        = bool
  default     = false
}

variable "ses_from_email" {
  description = "SES from email address (must be verified in SES)"
  type        = string
  default     = ""
}

variable "ses_reply_to_email" {
  description = "Reply-to email address"
  type        = string
  default     = ""
}

variable "project_display_name" {
  description = "Project display name for email sender"
  type        = string
  default     = "SaaS Factory"
}

# =============================================================================
# UI Customization
# =============================================================================

variable "enable_ui_customization" {
  description = "Enable custom UI branding (false uses Cognito defaults)"
  type        = bool
  default     = false
}

# =============================================================================
# Lambda Triggers (passed from parent)
# =============================================================================

variable "post_confirmation_lambda_arn" {
  description = "ARN of PostConfirmation Lambda function"
  type        = string
  default     = ""
}

variable "pre_token_generation_lambda_arn" {
  description = "ARN of PreTokenGeneration Lambda function"
  type        = string
  default     = ""
}

# =============================================================================
# Google Social Login (Personal Gmail - B2C)
# =============================================================================

variable "enable_google_social_login" {
  description = "Enable Google as a social identity provider for personal Gmail sign-in"
  type        = bool
  default     = false
}

variable "google_client_id" {
  description = "Google OAuth 2.0 Client ID for personal Gmail sign-in"
  type        = string
  default     = ""
  sensitive   = true
}

variable "google_client_secret" {
  description = "Google OAuth 2.0 Client Secret for personal Gmail sign-in"
  type        = string
  default     = ""
  sensitive   = true
}

