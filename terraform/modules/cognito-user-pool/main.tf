# =============================================================================
# Cognito User Pool Module - Main Resources
# =============================================================================
# This module creates:
# - Cognito User Pool with custom attributes
# - User Pool Domain (Modern Managed Login v2)
# - User Pool Clients (native + SPA)
# - User Groups (admin, tenant-admin, user)
# - Managed Login Branding
# =============================================================================

# Random suffix for unique domain name
resource "random_string" "domain_suffix" {
  length  = 8
  special = false
  upper   = false
}

# =============================================================================
# Cognito User Pool
# =============================================================================

resource "aws_cognito_user_pool" "main" {
  name = "${var.project_name}-${var.environment}-user-pool"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  username_configuration {
    case_sensitive = false
  }

  # Custom attributes
  schema {
    name                = "tenantId"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 256
    }
  }

  schema {
    name                = "role"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 50
    }
  }

  schema {
    name                = "tenantType"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 20
    }
  }

  # Password policy
  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  # Account recovery
  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  # Email Configuration
  email_configuration {
    email_sending_account  = var.enable_ses_email ? "DEVELOPER" : "COGNITO_DEFAULT"
    source_arn             = var.enable_ses_email ? "arn:aws:ses:${var.aws_region}:${var.aws_account_id}:identity/${var.ses_from_email}" : null
    from_email_address     = var.enable_ses_email ? "${var.project_display_name} <${var.ses_from_email}>" : null
    reply_to_email_address = var.enable_ses_email ? var.ses_reply_to_email : null
  }

  # MFA configuration
  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  # User verification
  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = "Your ${var.project_name} verification code"
    email_message        = "Your verification code is {####}"
  }

  # Device tracking
  device_configuration {
    challenge_required_on_new_device      = true
    device_only_remembered_on_user_prompt = true
  }

  # Production safety
  deletion_protection = var.environment == "prod" ? "ACTIVE" : "INACTIVE"

  lifecycle {
    prevent_destroy = false
    ignore_changes  = [lambda_config]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-user-pool"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# =============================================================================
# User Pool Domain (Modern Managed Login v2)
# =============================================================================

resource "aws_cognito_user_pool_domain" "main" {
  domain                = "${var.project_name}-${var.environment}-${random_string.domain_suffix.result}"
  user_pool_id          = aws_cognito_user_pool.main.id
  managed_login_version = 2
}

# =============================================================================
# User Pool Client - Native (confidential client with secret)
# =============================================================================

resource "aws_cognito_user_pool_client" "native" {
  name         = "${var.project_name}-${var.environment}-native-client"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = true

  explicit_auth_flows = [
    "ALLOW_ADMIN_USER_PASSWORD_AUTH",
    "ALLOW_CUSTOM_AUTH",
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]

  # OAuth configuration
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile", "phone", "aws.cognito.signin.user.admin"]

  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  supported_identity_providers = ["COGNITO"]

  # Token validity
  access_token_validity  = var.access_token_validity
  id_token_validity      = var.id_token_validity
  refresh_token_validity = var.refresh_token_validity

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  # Security settings
  prevent_user_existence_errors                 = "ENABLED"
  enable_token_revocation                       = true
  enable_propagate_additional_user_context_data = false

  read_attributes = [
    "email",
    "email_verified",
    "name",
    "custom:tenantId",
    "custom:role",
    "custom:tenantType"
  ]

  write_attributes = [
    "email",
    "name",
    "custom:tenantId",
    "custom:role",
    "custom:tenantType"
  ]
}

# =============================================================================
# User Pool Client - SPA (public client, no secret)
# =============================================================================

resource "aws_cognito_user_pool_client" "spa" {
  name         = "${var.project_name}-${var.environment}-spa-client"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_PASSWORD_AUTH"
  ]

  # Enable OAuth for SSO/federated login
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]

  # Support both Cognito and federated identity providers
  supported_identity_providers = concat(["COGNITO"], var.identity_providers)

  # OAuth callback URLs
  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  access_token_validity  = var.access_token_validity
  id_token_validity      = var.id_token_validity
  refresh_token_validity = var.refresh_token_validity

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  prevent_user_existence_errors                 = "ENABLED"
  enable_token_revocation                       = true
  enable_propagate_additional_user_context_data = false

  read_attributes = [
    "email",
    "email_verified",
    "name",
    "custom:tenantId",
    "custom:role",
    "custom:tenantType"
  ]

  write_attributes = [
    "email",
    "name"
  ]

  # Ignore changes to supported_identity_providers because they are
  # managed dynamically by the platform-service when tenants configure SSO
  lifecycle {
    ignore_changes = [
      supported_identity_providers
    ]
  }
}

# =============================================================================
# User Groups
# =============================================================================

resource "aws_cognito_user_group" "admin" {
  name         = "admin"
  user_pool_id = aws_cognito_user_pool.main.id
  description  = "Administrator group with full access"
  precedence   = 1
}

resource "aws_cognito_user_group" "tenant_admin" {
  name         = "tenant-admin"
  user_pool_id = aws_cognito_user_pool.main.id
  description  = "Tenant administrator group"
  precedence   = 2
}

resource "aws_cognito_user_group" "user" {
  name         = "user"
  user_pool_id = aws_cognito_user_pool.main.id
  description  = "Standard user group"
  precedence   = 3
}

# =============================================================================
# Managed Login Branding (Required for Modern UI v2)
# =============================================================================

resource "aws_cognito_managed_login_branding" "main" {
  user_pool_id = aws_cognito_user_pool.main.id
  client_id    = aws_cognito_user_pool_client.native.id

  use_cognito_provided_values = var.enable_ui_customization ? false : true

  depends_on = [
    aws_cognito_user_pool.main,
    aws_cognito_user_pool_client.native,
    aws_cognito_user_pool_domain.main
  ]
}
