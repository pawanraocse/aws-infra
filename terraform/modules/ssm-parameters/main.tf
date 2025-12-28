# =============================================================================
# SSM Parameters Module - Main Resources
# =============================================================================
# Creates all SSM parameters for application configuration.
# Parameters are stored in /${project_name}/${environment}/cognito/* path.
# =============================================================================

locals {
  base_path = "/${var.project_name}/${var.environment}"
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# =============================================================================
# Cognito Parameters
# =============================================================================

resource "aws_ssm_parameter" "user_pool_id" {
  name        = "${local.base_path}/cognito/user_pool_id"
  description = "Cognito User Pool ID"
  type        = "String"
  value       = var.user_pool_id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-user-pool-id"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "client_id" {
  name        = "${local.base_path}/cognito/client_id"
  description = "Cognito Client ID"
  type        = "String"
  value       = var.client_id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-client-id"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "spa_client_id" {
  name        = "${local.base_path}/cognito/spa_client_id"
  description = "Cognito SPA Client ID (for frontend - no secret)"
  type        = "String"
  value       = var.spa_client_id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-spa-client-id"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "client_secret" {
  name        = "${local.base_path}/cognito/client_secret"
  description = "Cognito Client Secret"
  type        = "SecureString"
  value       = var.client_secret

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-client-secret"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "issuer_uri" {
  name        = "${local.base_path}/cognito/issuer_uri"
  description = "Cognito Issuer URI"
  type        = "String"
  value       = var.issuer_uri

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-issuer-uri"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "jwks_uri" {
  name        = "${local.base_path}/cognito/jwks_uri"
  description = "Cognito JWKS URI"
  type        = "String"
  value       = var.jwks_uri

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-jwks-uri"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "domain" {
  name        = "${local.base_path}/cognito/domain"
  description = "Cognito Hosted UI Domain"
  type        = "String"
  value       = var.domain

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-domain"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "hosted_ui_url" {
  name        = "${local.base_path}/cognito/hosted_ui_url"
  description = "Cognito Hosted UI URL"
  type        = "String"
  value       = var.hosted_ui_url

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-hosted-ui-url"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "branding_id" {
  name        = "${local.base_path}/cognito/branding_id"
  description = "Cognito Managed Login Branding ID"
  type        = "String"
  value       = var.branding_id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-branding-id"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "callback_url" {
  name        = "${local.base_path}/cognito/callback_url"
  description = "Cognito OAuth2 Callback URL"
  type        = "String"
  value       = var.callback_url

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-callback-url"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "logout_redirect_url" {
  name        = "${local.base_path}/cognito/logout_redirect_url"
  description = "Cognito Logout Redirect URL"
  type        = "String"
  value       = var.logout_redirect_url

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-logout-redirect-url"
  })

  lifecycle {
    prevent_destroy = false
  }
}

# =============================================================================
# AWS Region Parameter
# =============================================================================

resource "aws_ssm_parameter" "aws_region" {
  name        = "${local.base_path}/aws/region"
  description = "AWS Region for this deployment"
  type        = "String"
  value       = var.aws_region

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-aws-region"
  })

  lifecycle {
    prevent_destroy = false
  }
}

# =============================================================================
# SES Parameters
# =============================================================================

resource "aws_ssm_parameter" "ses_from_email" {
  name        = "${local.base_path}/ses/from_email"
  description = "SES From Email Address"
  type        = "String"
  value       = var.ses_from_email

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-ses-from-email"
  })

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "ses_enabled" {
  name        = "${local.base_path}/ses/enabled"
  description = "Whether SES is enabled for email sending"
  type        = "String"
  value       = var.ses_enabled ? "true" : "false"

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-ses-enabled"
  })

  lifecycle {
    prevent_destroy = false
  }
}
