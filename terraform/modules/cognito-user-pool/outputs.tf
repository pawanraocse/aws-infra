# =============================================================================
# Cognito User Pool Module - Outputs
# =============================================================================

# User Pool
output "user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  description = "Cognito User Pool ARN"
  value       = aws_cognito_user_pool.main.arn
}

output "user_pool_endpoint" {
  description = "Cognito User Pool endpoint"
  value       = aws_cognito_user_pool.main.endpoint
}

# Domain
output "domain" {
  description = "Cognito User Pool domain prefix"
  value       = aws_cognito_user_pool_domain.main.domain
}

output "domain_cloudfront_distribution_arn" {
  description = "CloudFront distribution ARN for the domain"
  value       = aws_cognito_user_pool_domain.main.cloudfront_distribution_arn
}

# Native Client
output "native_client_id" {
  description = "Native client ID (with secret)"
  value       = aws_cognito_user_pool_client.native.id
}

output "native_client_secret" {
  description = "Native client secret"
  value       = aws_cognito_user_pool_client.native.client_secret
  sensitive   = true
}

# SPA Client
output "spa_client_id" {
  description = "SPA client ID (no secret)"
  value       = aws_cognito_user_pool_client.spa.id
}

# Computed URIs
output "issuer_uri" {
  description = "Cognito Issuer URI"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "jwks_uri" {
  description = "Cognito JWKS URI"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json"
}

output "hosted_ui_url" {
  description = "Cognito Hosted UI URL"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/authorize?client_id=${aws_cognito_user_pool_client.native.id}&response_type=code&scope=openid+email+profile+phone&redirect_uri=${urlencode(var.callback_urls[0])}"
}

# Branding
output "branding_id" {
  description = "Managed Login Branding ID"
  value       = aws_cognito_managed_login_branding.main.managed_login_branding_id
}

# Groups
output "admin_group_name" {
  description = "Admin group name"
  value       = aws_cognito_user_group.admin.name
}

output "tenant_admin_group_name" {
  description = "Tenant admin group name"
  value       = aws_cognito_user_group.tenant_admin.name
}

output "user_group_name" {
  description = "User group name"
  value       = aws_cognito_user_group.user.name
}
