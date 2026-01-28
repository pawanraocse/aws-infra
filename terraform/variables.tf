variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}-[a-z]+-[0-9]$", var.aws_region))
    error_message = "AWS region must be in valid format (e.g., us-east-1)"
  }
}

variable "aws_profile" {
  description = "AWS CLI profile to use for deployments"
  type        = string
  default     = "personal"
}

variable "platform_service_url" {
  description = "Platform service URL for Lambda to call tenant provisioning API"
  type        = string
  default     = "http://platform-service:8083/platform"
}

variable "auth_service_url" {
  description = "Auth service URL for Lambda to resolve group-role mappings"
  type        = string
  default     = "http://auth-service:8081"
}

variable "enable_group_sync" {
  description = "Enable SSO group sync during token generation"
  type        = bool
  default     = true
}

variable "project_name" {
  description = "Project name used in resource naming (set via TF_VAR_project_name from .env)"
  type        = string
  # No default - must be provided by scripts
  # default     = "cloud-infra"

  validation {
    condition     = can(regex("^[a-z0-9-]{3,20}$", var.project_name)) && !can(regex("^aws", var.project_name))
    error_message = "3-20 chars, lowercase letters, numbers, hyphens. Cannot start with 'aws'."
  }
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod"
  }
}

variable "callback_urls" {
  description = "List of allowed callback URLs for OAuth. Must be explicitly configured in terraform.tfvars"
  type        = list(string)

  validation {
    condition     = length(var.callback_urls) > 0
    error_message = "At least one callback URL must be specified."
  }
}

variable "logout_urls" {
  description = "List of allowed logout URLs. Must be explicitly configured in terraform.tfvars"
  type        = list(string)

  validation {
    condition     = length(var.logout_urls) > 0
    error_message = "At least one logout URL must be specified."
  }
}

variable "access_token_validity" {
  description = "Access token validity in minutes"
  type        = number
  default     = 60

  validation {
    condition     = var.access_token_validity >= 5 && var.access_token_validity <= 1440
    error_message = "Access token validity must be between 5 and 1440 minutes"
  }
}

variable "id_token_validity" {
  description = "ID token validity in minutes"
  type        = number
  default     = 60

  validation {
    condition     = var.id_token_validity >= 5 && var.id_token_validity <= 1440
    error_message = "ID token validity must be between 5 and 1440 minutes"
  }
}

variable "refresh_token_validity" {
  description = "Refresh token validity in days"
  type        = number
  default     = 30

  validation {
    condition     = var.refresh_token_validity >= 1 && var.refresh_token_validity <= 3650
    error_message = "Refresh token validity must be between 1 and 3650 days"
  }
}

variable "enable_ui_customization" {
  description = "Enable UI customization for Cognito Hosted UI"
  type        = bool
  default     = false
}

# ==========================================================================
# AWS SES Email Configuration
# ==========================================================================
# To enable SES for Cognito emails:
# 1. Set enable_ses_email = true
# 2. Verify ses_from_email in SES first (or domain)
# 3. Request SES Production Access for sending to any email
# ==========================================================================

variable "enable_ses_email" {
  description = "Enable SES for Cognito email sending instead of Cognito default. Recommended for production."
  type        = bool
  default     = false
}

variable "ses_from_email" {
  description = "From email address for Cognito emails (must be verified in SES)"
  type        = string
  default     = "noreply@example.com"
}

variable "ses_reply_to_email" {
  description = "Reply-to email address for Cognito emails (optional)"
  type        = string
  default     = null
}

variable "project_display_name" {
  description = "Display name for email sender (e.g., 'Your App Name' appears as 'Your App Name <noreply@...>')"
  type        = string
  default     = "Cloud Infra"
}

# ==========================================================================
# Google Social Login (Personal Gmail - B2C)
# ==========================================================================
# To enable personal Gmail sign-in:
# 1. Create OAuth 2.0 credentials in Google Cloud Console
# 2. Run: ./scripts/identity/setup-ssm-secrets.sh (sets SSM params)  
# 3. Set enable_google_social_login = true
# 4. Run: terraform apply
# ==========================================================================

variable "enable_google_social_login" {
  description = "Enable 'Sign in with Google' for personal Gmail accounts. Managed via Terraform resources."
  type        = bool
  default     = false
}

variable "google_client_id" {
  description = "Google Client ID for OAuth (set via TF_VAR_google_client_id from .env)"
  type        = string
  default     = ""
}

variable "google_client_secret" {
  description = "Google Client Secret for OAuth (set via TF_VAR_google_client_secret from .env)"
  type        = string
  default     = ""
  sensitive   = true
}


