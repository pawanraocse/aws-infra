# Terraform configuration for AWS Cognito multi-tenant (native only) setup
provider "aws" {
region = var.aws_region
}

# --- Cognito User Pool ---
resource "aws_cognito_user_pool" "multi_tenant" {
name = "multi-tenant-user-pool"
auto_verified_attributes = ["email"]
username_attributes      = ["email"]

schema {
name                = "tenantId"
attribute_data_type = "String"
mutable             = true
required            = false
}

# Password policy (optional but recommended)
password_policy {
minimum_length    = 8
require_lowercase = true
require_numbers   = true
require_symbols   = true
require_uppercase = true
}
}

# --- Cognito Domain (Required for Hosted UI) ---
resource "aws_cognito_user_pool_domain" "multi_tenant_domain" {
domain       = "${var.project_name}-${var.aws_region}"
user_pool_id = aws_cognito_user_pool.multi_tenant.id
}

# --- User Groups ---
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

# --- User Pool Client ---
resource "aws_cognito_user_pool_client" "native" {
name                = "native-client"
user_pool_id        = aws_cognito_user_pool.multi_tenant.id
generate_secret     = true

# OAuth Configuration
allowed_oauth_flows                  = ["code"]
allowed_oauth_scopes                 = ["openid", "email", "profile", "phone"]
allowed_oauth_flows_user_pool_client = true

# FIX: Match Spring Boot's default redirect URI pattern
callback_urls = [
"http://localhost:8080/login/oauth2/code/cognito"
]

logout_urls = [
"http://localhost:8080/logout"
]

supported_identity_providers = ["COGNITO"]

# Token validity (optional but recommended)
access_token_validity  = 60  # 60 minutes
id_token_validity      = 60  # 60 minutes
refresh_token_validity = 30  # 30 days

token_validity_units {
access_token  = "minutes"
id_token      = "minutes"
refresh_token = "days"
}
}

# --- Variables ---
variable "aws_region" {
description = "AWS region to deploy resources in"
type        = string
default     = "us-east-1"
}

variable "project_name" {
description = "A unique identifier for the project to use in the Cognito domain prefix."
type        = string
default     = "my-multi-tenant-app-dev"
}

# --- Outputs ---
output "user_pool_id" {
value = aws_cognito_user_pool.multi_tenant.id
}

output "native_client_id" {
value = aws_cognito_user_pool_client.native.id
}

# FIX: Add client secret output (mark as sensitive)
output "native_client_secret" {
value     = aws_cognito_user_pool_client.native.client_secret
sensitive = true
}

output "aws_region" {
value = var.aws_region
}

output "cognito_domain_prefix" {
description = "The prefix used for the Cognito Hosted UI domain."
value       = aws_cognito_user_pool_domain.multi_tenant_domain.domain
}

output "issuer_uri" {
description = "The issuer URI for Spring Boot OAuth2 configuration"
value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.multi_tenant.id}"
}

output "hosted_ui_url" {
description = "The full URL for the Cognito Hosted UI login page."
value       = "https://${aws_cognito_user_pool_domain.multi_tenant_domain.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/authorize?client_id=${aws_cognito_user_pool_client.native.id}&response_type=code&scope=openid%20email%20profile%20phone&redirect_uri=${urlencode(tolist(aws_cognito_user_pool_client.native.callback_urls)[0])}"
}
