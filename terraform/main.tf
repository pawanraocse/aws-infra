# =============================================================================
# SaaS Factory - Terraform Configuration
# =============================================================================
# This file orchestrates all infrastructure modules.
# 
# Modules:
# - cognito-user-pool: Cognito User Pool, Clients, Domain, Groups
# - cognito-post-confirmation: Lambda for PostConfirmation trigger
# - cognito-pre-token-generation: Lambda for PreTokenGeneration trigger  
# - ssm-parameters: All SSM parameters for application config
# =============================================================================

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
  # backend "s3" {
  #   bucket         = "your-terraform-state-bucket"
  #   key            = "cognito/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-state-lock"
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

data "aws_caller_identity" "current" {}

# =============================================================================
# SSM Parameters for Secrets (Managed by Terraform)
# =============================================================================

resource "aws_ssm_parameter" "google_client_id" {
  count     = var.enable_google_social_login ? 1 : 0
  name      = "/${var.project_name}/${var.environment}/auth-service/google_client_id"
  type      = "String"
  value     = var.google_client_id
  overwrite = true

  tags = {
    Environment = var.environment
    Project     = var.project_name
    ManagedBy   = "Terraform"
  }
}

resource "aws_ssm_parameter" "google_client_secret" {
  count     = var.enable_google_social_login ? 1 : 0
  name      = "/${var.project_name}/${var.environment}/auth-service/google_client_secret"
  type      = "SecureString"
  value     = var.google_client_secret
  overwrite = true

  tags = {
    Environment = var.environment
    Project     = var.project_name
    ManagedBy   = "Terraform"
  }
}

# =============================================================================
# Cognito User Pool Module
# =============================================================================

module "cognito" {
  source = "./modules/cognito-user-pool"

  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id

  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  access_token_validity  = var.access_token_validity
  id_token_validity      = var.id_token_validity
  refresh_token_validity = var.refresh_token_validity

  # SES Configuration
  enable_ses_email     = var.enable_ses_email
  ses_from_email       = var.ses_from_email
  ses_reply_to_email   = var.ses_reply_to_email
  project_display_name = var.project_display_name

  enable_ui_customization = var.enable_ui_customization

  # Google Social Login (Personal Gmail - B2C)
  # Credentials are read from SSM Parameter Store
  enable_google_social_login = var.enable_google_social_login
  google_client_id           = var.enable_google_social_login ? var.google_client_id : ""
  google_client_secret       = var.enable_google_social_login ? var.google_client_secret : ""
}

# =============================================================================
# Lambda Modules (PostConfirmation + PreTokenGeneration)
# =============================================================================

module "lambda_post_confirmation" {
  source = "./modules/cognito-post-confirmation"

  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id
  user_pool_id   = module.cognito.user_pool_id
  user_pool_arn  = module.cognito.user_pool_arn
}

module "lambda_pre_token_generation" {
  source = "./modules/cognito-pre-token-generation"

  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id
  user_pool_id   = module.cognito.user_pool_id
  user_pool_arn  = module.cognito.user_pool_arn

  platform_service_url = var.platform_service_url
  auth_service_url     = var.auth_service_url
  enable_group_sync    = var.enable_group_sync
}

# =============================================================================
# Configure Cognito Lambda Triggers (via CLI to break circular dependency)
# =============================================================================

resource "null_resource" "configure_cognito_trigger" {
  triggers = {
    post_confirmation_arn    = module.lambda_post_confirmation.lambda_function_arn
    pre_token_generation_arn = module.lambda_pre_token_generation.lambda_arn
    user_pool_id             = module.cognito.user_pool_id
  }

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      aws cognito-idp update-user-pool \
        --user-pool-id ${module.cognito.user_pool_id} \
        --auto-verified-attributes email \
        --lambda-config 'PostConfirmation=${module.lambda_post_confirmation.lambda_function_arn},PreTokenGenerationConfig={LambdaVersion=V2_0,LambdaArn=${module.lambda_pre_token_generation.lambda_arn}}' \
        --region ${var.aws_region}
      echo "✅ Cognito User Pool updated with Lambda triggers (PreToken V2_0)"
    EOT
  }

  depends_on = [
    module.cognito,
    module.lambda_post_confirmation,
    module.lambda_pre_token_generation
  ]
}

# =============================================================================
# SSM Parameters Module
# =============================================================================

module "ssm_parameters" {
  source = "./modules/ssm-parameters"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  # From cognito module
  user_pool_id        = module.cognito.user_pool_id
  client_id           = module.cognito.native_client_id
  spa_client_id       = module.cognito.spa_client_id
  client_secret       = module.cognito.native_client_secret
  issuer_uri          = module.cognito.issuer_uri
  jwks_uri            = module.cognito.jwks_uri
  domain              = module.cognito.domain
  hosted_ui_url       = module.cognito.hosted_ui_url
  branding_id         = module.cognito.branding_id
  callback_url        = var.callback_urls[0]
  logout_redirect_url = var.logout_urls[0]

  # SES
  ses_from_email = var.ses_from_email
  ses_enabled    = var.enable_ses_email

  depends_on = [module.cognito]
}
