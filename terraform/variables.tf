variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"

  validation {
    condition     = can(regex("^[a-z]{2}-[a-z]+-[0-9]$", var.aws_region))
    error_message = "AWS region must be in valid format (e.g., us-east-1)"
  }
}

variable "project_name" {
  description = "Project name used in resource naming"
  type        = string
  default     = "cloud-infra"

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
  description = "List of allowed callback URLs for OAuth"
  type        = list(string)
  default = [
    "http://localhost:8081/login/oauth2/code/cognito",
    "http://localhost:3000/callback"
  ]
}

variable "logout_urls" {
  description = "List of allowed logout URLs"
  type        = list(string)
  default = [
    "http://localhost:8081/logged-out",
    "http://localhost:3000"
  ]
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
