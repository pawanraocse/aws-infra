# Terraform configuration for AWS Cognito multi-tenant setup

provider "aws" {
  region = var.aws_region
}

resource "aws_cognito_user_pool" "multi_tenant" {
  name = "multi-tenant-user-pool"
  auto_verified_attributes = ["email"]
  username_attributes      = ["email"]
  schema {
    name = "tenantId"
    attribute_data_type = "String"
    mutable = true
    required = false
  }
}

resource "aws_cognito_user_group" "tenant_abc" {
  user_pool_id = aws_cognito_user_pool.multi_tenant.id
  name         = "tenant-abc"
  description  = "Group for tenant ABC"
}

resource "aws_cognito_user_group" "tenant_xyz" {
  user_pool_id = aws_cognito_user_pool.multi_tenant.id
  name         = "tenant-xyz"
  description  = "Group for tenant XYZ"
}

resource "aws_cognito_user_pool_client" "native" {
  name         = "native-client"
  user_pool_id = aws_cognito_user_pool.multi_tenant.id
  generate_secret = false
  allowed_oauth_flows = ["code"]
  allowed_oauth_scopes = ["openid", "email", "profile"]
  allowed_oauth_flows_user_pool_client = true
  callback_urls = [
    "http://localhost:8080/api/v1/auth/callback",
    "https://your-frontend-domain.com/auth/callback"
  ]
  logout_urls = [
    "http://localhost:8080/logout",
    "https://your-frontend-domain.com/logout"
  ]
  supported_identity_providers = ["COGNITO"]
}

resource "aws_cognito_user_pool_client" "federated" {
  name         = "federated-client"
  user_pool_id = aws_cognito_user_pool.multi_tenant.id
  generate_secret = false
  allowed_oauth_flows = ["code"]
  allowed_oauth_scopes = ["openid", "email", "profile"]
  allowed_oauth_flows_user_pool_client = true
  callback_urls = [
    "http://localhost:8080/api/v1/auth/callback",
    "https://your-frontend-domain.com/auth/callback"
  ]
  logout_urls = [
    "http://localhost:8080/logout",
    "https://your-frontend-domain.com/logout"
  ]
  supported_identity_providers = ["COGNITO", "Google"]

  depends_on = [
    aws_cognito_identity_provider.google
  ]
}

data "aws_ssm_parameter" "google_client_id" {
  name = "/auth-service/google_client_id"
}

data "aws_ssm_parameter" "google_client_secret" {
  name            = "/auth-service/google_client_secret"
  with_decryption = true
}

resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.multi_tenant.id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id       = data.aws_ssm_parameter.google_client_id.value
    client_secret   = data.aws_ssm_parameter.google_client_secret.value
    authorize_scopes = "openid email profile"
  }

  attribute_mapping = {
    email    = "email"
    username = "sub"
  }
}

variable "aws_region" {
  description = "AWS region to deploy resources in"
  type        = string
  default     = "ap-south-1"
}

output "user_pool_id" {
  value = aws_cognito_user_pool.multi_tenant.id
}
output "native_client_id" {
  value = aws_cognito_user_pool_client.native.id
}
output "federated_client_id" {
  value = aws_cognito_user_pool_client.federated.id
}
