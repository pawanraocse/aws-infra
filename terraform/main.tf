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
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
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
  auto_verified_attributes = ["email"] # REQUIRED: Tells Cognito to send verification codes to email

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

  # ==========================================================================
  # Email Configuration Options
  # ==========================================================================
  # Option 1: COGNITO_DEFAULT (Current - Free, good for dev/testing)
  #   - Uses Cognito's shared email service
  #   - Sends from: no-reply@verificationemail.com
  #   - Limit: ~50 emails/day
  #   - Cost: FREE
  #
  # Option 2: SES with verified identity (Production recommended)
  #   - Uses your own SES-verified domain/email
  #   - Sends from: your custom address (e.g., noreply@yourcompany.com)
  #   - Limit: Based on SES limits (62,000/month free from EC2/Lambda)
  #   - Cost: FREE within limits, then $0.10/1000 emails
  #   - Requires: SES identity verification + exit sandbox for production
  # ==========================================================================

  # Current: Using Cognito Default (free tier, good for development)
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  # PRODUCTION: Uncomment below and comment out COGNITO_DEFAULT above
  # First, verify your email/domain in SES and create the identity resource
  # email_configuration {
  #   email_sending_account  = "DEVELOPER"
  #   source_arn             = "arn:aws:ses:${var.aws_region}:${data.aws_caller_identity.current.account_id}:identity/noreply@yourcompany.com"
  #   from_email_address     = "Your App <noreply@yourcompany.com>"
  #   reply_to_email_address = "support@yourcompany.com"
  # }

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

  # Lambda triggers
  # lambda_config {
  #   post_confirmation = module.lambda_post_confirmation.lambda_function_arn
  # }

  # ==========================================================================
  # Production Safety
  # ==========================================================================
  # IMPORTANT: Set to true in production to prevent accidental User Pool deletion
  # User Pools cannot be recovered once deleted - all users will be lost!
  deletion_protection = var.environment == "prod" ? "ACTIVE" : "INACTIVE"

  lifecycle {
    # Set to true in production to prevent terraform destroy
    prevent_destroy = false # Change to true for production
    ignore_changes  = [lambda_config]
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-user-pool"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Lambda Post Confirmation Module
module "lambda_post_confirmation" {
  source = "./modules/cognito-post-confirmation"

  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id
  user_pool_id   = aws_cognito_user_pool.main.id
  user_pool_arn  = aws_cognito_user_pool.main.arn
}

# Lambda Pre Token Generation Module (for multi-tenant login)
# Injects selected tenant ID into JWT when user logs in with multiple tenants
module "lambda_pre_token_generation" {
  source = "./modules/cognito-pre-token-generation"

  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id
  user_pool_id   = aws_cognito_user_pool.main.id
  user_pool_arn  = aws_cognito_user_pool.main.arn
}

# Break circular dependency by configuring trigger via CLI
# IMPORTANT: update-user-pool replaces unspecified attributes, so we must include --auto-verified-attributes
resource "null_resource" "configure_cognito_trigger" {
  triggers = {
    post_confirmation_arn    = module.lambda_post_confirmation.lambda_function_arn
    pre_token_generation_arn = module.lambda_pre_token_generation.lambda_arn
    user_pool_id             = aws_cognito_user_pool.main.id
  }

  provisioner "local-exec" {
    # Note: set -e ensures script fails if CLI command fails
    command = <<-EOT
      set -e
      aws cognito-idp update-user-pool \
        --user-pool-id ${aws_cognito_user_pool.main.id} \
        --auto-verified-attributes email \
        --lambda-config PostConfirmation=${module.lambda_post_confirmation.lambda_function_arn},PreTokenGeneration=${module.lambda_pre_token_generation.lambda_arn} \
        --region ${var.aws_region}
      echo "✅ Cognito User Pool updated with Lambda triggers successfully"
    EOT
  }

  depends_on = [
    aws_cognito_user_pool.main,
    module.lambda_post_confirmation,
    module.lambda_pre_token_generation
  ]
}

data "aws_caller_identity" "current" {}

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


# User Pool Client - SPA/Web Application (public client, no secret)
# Used by Angular frontend for direct username/password authentication
resource "aws_cognito_user_pool_client" "spa" {
  name         = "${var.project_name}-${var.environment}-spa-client"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  # Auth flows for browser-based apps
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",      # Secure Remote Password (recommended)
    "ALLOW_REFRESH_TOKEN_AUTH", # Refresh token flow
    "ALLOW_USER_PASSWORD_AUTH"  # Direct username/password (for Amplify signIn)
  ]

  # No OAuth flows - using direct authentication
  allowed_oauth_flows_user_pool_client = false

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

  # Read attributes - what the app can read from user profile
  read_attributes = [
    "email",
    "email_verified",
    "name",
    "custom:tenantId",
    "custom:role",
    "custom:tenantType"
  ]

  # Write attributes - what the app can update
  write_attributes = [
    "email",
    "name"
  ]
}

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

  lifecycle {
    prevent_destroy = false
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
  lifecycle {
    prevent_destroy = false
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
  lifecycle {
    prevent_destroy = false
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
  lifecycle {
    prevent_destroy = false
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
  lifecycle {
    prevent_destroy = false
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
  lifecycle {
    prevent_destroy = false
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
  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "jwks_uri" {
  name        = "/${var.project_name}/${var.environment}/cognito/jwks_uri"
  description = "Cognito JWKS URI"
  type        = "String"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json"
  tags = {
    Name        = "${var.project_name}-${var.environment}-jwks-uri"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "hosted_ui_url" {
  name        = "/${var.project_name}/${var.environment}/cognito/hosted_ui_url"
  description = "Cognito Hosted UI URL (Modern Managed Login v2)"
  type        = "String"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/authorize?client_id=${aws_cognito_user_pool_client.native.id}&response_type=code&scope=openid+email+profile+phone&redirect_uri=${urlencode(var.callback_urls[0])}"
  tags = {
    Name        = "${var.project_name}-${var.environment}-hosted-ui-url"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "branding_id" {
  name        = "/${var.project_name}/${var.environment}/cognito/branding_id"
  description = "Cognito Managed Login Branding ID"
  type        = "String"
  value       = aws_cognito_managed_login_branding.main.managed_login_branding_id
  tags = {
    Name        = "${var.project_name}-${var.environment}-branding-id"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_ssm_parameter" "aws_region" {
  name        = "/${var.project_name}/${var.environment}/aws/region"
  description = "AWS Region for this deployment"
  type        = "String"
  value       = var.aws_region
  tags = {
    Name        = "${var.project_name}-${var.environment}-aws-region"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
  lifecycle {
    prevent_destroy = false
  }
}
