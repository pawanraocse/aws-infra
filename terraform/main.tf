terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.17"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # IMPORTANT: Uncomment and configure backend for production
  # State file is currently stored locally - NOT production ready!
  # For production, use S3 backend with DynamoDB state locking
  #
  # backend "s3" {
  #   bucket         = "your-terraform-state-bucket"  # Create this bucket first
  #   key            = "cognito/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-state-lock"  # Create this table first
  #
  #   # Optional: Use KMS for state encryption (PAID - may incur KMS charges)
  #   # kms_key_id = "arn:aws:kms:us-east-1:ACCOUNT_ID:key/KEY_ID"
  # }

  backend "local" {
    path = "terraform.tfstate"
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = var.environment
      Project     = var.project_name
      ManagedBy   = "Terraform"
      CostCenter  = "Development"
    }
  }
}

# Cognito User Pool
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

  # Email configuration (COGNITO_DEFAULT uses Cognito's default sending - keeps you in free tier)
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  # MFA configuration: keep SOFTWARE token (free). SMS MFA is commented out to avoid SMS charges.
  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  # SMS configuration is commented to avoid SMS charges (paid).
  # sms_configuration {
  #   sns_caller_arn = "arn:aws:iam::ACCOUNT_ID:role/your-sns-role" # PAID (SMS charges)
  #   external_id    = "external-id"
  # }

  # User verification
  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = "Your ${var.project_name} verification code"
    email_message        = "Your verification code is {####}"
  }

  # Device tracking (free tier)
  device_configuration {
    challenge_required_on_new_device      = true
    device_only_remembered_on_user_prompt = true
  }

  # Advanced security (adaptive auth) is commented out to avoid potential paid features.
  # user_pool_add_ons {
  #   advanced_security_mode = "ENFORCED" # may incur additional costs
  # }

  # Lambda triggers (commented - add if needed and if you accept possible Lambda costs)
  # lambda_config {
  #   pre_sign_up = aws_lambda_function.pre_signup.arn
  # }

  lifecycle {
    prevent_destroy = false
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-user-pool"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Cognito User Pool Domain with Modern Managed Login UI
resource "aws_cognito_user_pool_domain" "main" {
  domain       = "${var.project_name}-${var.environment}-${random_string.domain_suffix.result}"
  user_pool_id = aws_cognito_user_pool.main.id

  # Enable the new modern Managed Login UI (version 2)
  # This gives you the beautiful, modern login interface
  managed_login_version = 2
}

resource "random_string" "domain_suffix" {
  length  = 8
  special = false
  upper   = false
}

# Managed Login Branding Style (REQUIRED for Modern UI v2)
# This creates the branding style that makes the login page visible
resource "aws_cognito_managed_login_branding" "main" {
  user_pool_id = aws_cognito_user_pool.main.id
  client_id    = aws_cognito_user_pool_client.native.id

  # Use Cognito's default branding (you can customize later in the console or via settings)
  use_cognito_provided_values = var.enable_ui_customization ? false : true

  # Optional: Custom settings (uncomment and modify to customize)
  # settings = jsonencode({
  #   backgroundColor = "#FFFFFF"
  #   logoUrl         = "https://example.com/logo.png"
  # })

  # Optional: Custom assets (logos, backgrounds, etc.)
  # Uncomment to add custom images
  # assets = [
  #   {
  #     category   = "PAGE_HEADER_LOGO"
  #     color_mode = "LIGHT"
  #     extension  = "PNG"
  #     bytes      = filebase64("${path.module}/assets/logo-light.png")
  #   },
  #   {
  #     category   = "PAGE_HEADER_LOGO"
  #     color_mode = "DARK"
  #     extension  = "PNG"
  #     bytes      = filebase64("${path.module}/assets/logo-dark.png")
  #   }
  # ]

  depends_on = [
    aws_cognito_user_pool.main,
    aws_cognito_user_pool_client.native,
    aws_cognito_user_pool_domain.main
  ]
}

# User Pool Client - Native Application (confidential client with secret)
resource "aws_cognito_user_pool_client" "native" {
  name         = "${var.project_name}-${var.environment}-native-client"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = true

  # OAuth configuration
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile", "phone", "aws.cognito.signin.user.admin"]

  # Callback URLs
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

  # Read/Write attributes
  read_attributes = [
    "email",
    "email_verified",
    "custom:tenantId",
    "custom:role"
  ]

  write_attributes = [
    "email",
    "custom:tenantId",
    "custom:role"
  ]
}

# OPTIONAL: Public client for SPAs (no client secret). Useful for browser apps.
# Commented out to avoid exposing flows you may not want in free-tier testing.
# resource "aws_cognito_user_pool_client" "spa" {
#   name                       = "${var.project_name}-${var.environment}-spa-client"
# #  user_pool_id               = aws_cognito_user_pool.main.id
# #  generate_secret            = false
# #  allowed_oauth_flows        = ["code"]
# #  allowed_oauth_flows_user_pool_client = false
# #  allowed_oauth_scopes       = ["openid", "email", "profile"]
# #  callback_urls              = var.callback_urls
# #  logout_urls                = var.logout_urls
# #  supported_identity_providers = ["COGNITO"]
# #  explicit_auth_flows        = ["ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_SRP_AUTH"]
# }

# User Groups
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

# SSM Parameters for application configuration
resource "aws_ssm_parameter" "user_pool_id" {
  name        = "/${var.project_name}/${var.environment}/cognito/user_pool_id"
  description = "Cognito User Pool ID"
  type        = "String"
  value       = aws_cognito_user_pool.main.id

  tags = {
    Name        = "${var.project_name}-${var.environment}-user-pool-id"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_ssm_parameter" "client_id" {
  name        = "/${var.project_name}/${var.environment}/cognito/client_id"
  description = "Cognito Client ID"
  type        = "String"
  value       = aws_cognito_user_pool_client.native.id

  tags = {
    Name        = "${var.project_name}-${var.environment}-client-id"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Storing client_secret as SecureString. By default this uses the AWS managed KMS key.
# If you specify a customer-managed KMS key (key_id) it may incur KMS charges -> commented out.
resource "aws_ssm_parameter" "client_secret" {
  name        = "/${var.project_name}/${var.environment}/cognito/client_secret"
  description = "Cognito Client Secret"
  type        = "SecureString"
  value       = aws_cognito_user_pool_client.native.client_secret

  tags = {
    Name        = "${var.project_name}-${var.environment}-client-secret"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }

  # Optional: specify key_id to use customer-managed CMK (PAID - may incur KMS costs)
  # key_id = "arn:aws:kms:us-east-1:ACCOUNT_ID:key/KEY_ID"
}

resource "aws_ssm_parameter" "issuer_uri" {
  name        = "/${var.project_name}/${var.environment}/cognito/issuer_uri"
  description = "Cognito Issuer URI"
  type        = "String"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"

  tags = {
    Name        = "${var.project_name}-${var.environment}-issuer-uri"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_ssm_parameter" "domain" {
  name        = "/${var.project_name}/${var.environment}/cognito/domain"
  description = "Cognito Hosted UI Domain"
  type        = "String"
  value       = aws_cognito_user_pool_domain.main.domain

  tags = {
    Name        = "${var.project_name}-${var.environment}-domain"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_ssm_parameter" "callback_url" {
  name        = "/${var.project_name}/${var.environment}/cognito/callback_url"
  description = "Cognito OAuth2 Callback URL"
  type        = "String"
  value       = var.callback_urls[0]

  tags = {
    Name        = "${var.project_name}-${var.environment}-callback-url"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_ssm_parameter" "logout_redirect_url" {
  name        = "/${var.project_name}/${var.environment}/cognito/logout_redirect_url"
  description = "Cognito Logout Redirect URL"
  type        = "String"
  value       = var.logout_urls[0]

  tags = {
    Name        = "${var.project_name}-${var.environment}-logout-redirect-url"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
