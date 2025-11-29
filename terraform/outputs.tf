# ============================================================================
# Cognito Outputs
# ============================================================================

output "user_pool_id" {
  description = "The ID of the Cognito User Pool"
  value       = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  description = "The ARN of the Cognito User Pool"
  value       = aws_cognito_user_pool.main.arn
}

output "user_pool_endpoint" {
  description = "The endpoint of the Cognito User Pool"
  value       = aws_cognito_user_pool.main.endpoint
}

output "client_id" {
  description = "The ID of the Cognito User Pool Client (Native - with secret)"
  value       = aws_cognito_user_pool_client.native.id
}

output "spa_client_id" {
  description = "The ID of the Cognito User Pool Client (SPA - public, no secret)"
  value       = aws_cognito_user_pool_client.spa.id
}

output "client_secret" {
  description = "The secret of the Cognito User Pool Client (sensitive)"
  value       = aws_cognito_user_pool_client.native.client_secret
  sensitive   = true
}

output "cognito_domain" {
  description = "The Cognito Hosted UI domain"
  value       = aws_cognito_user_pool_domain.main.domain
}

output "cognito_domain_cloudfront" {
  description = "The CloudFront distribution for the Cognito domain"
  value       = aws_cognito_user_pool_domain.main.cloudfront_distribution_arn
}

output "managed_login_branding_id" {
  description = "The ID of the Managed Login Branding Style"
  value       = aws_cognito_managed_login_branding.main.managed_login_branding_id
}

output "issuer_uri" {
  description = "The OIDC issuer URI for the Cognito User Pool"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "jwks_uri" {
  description = "The JWKS URI for token validation"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json"
}

# ============================================================================
# Hosted UI URLs
# ============================================================================

output "hosted_ui_url" {
  description = "The full URL for the Cognito Hosted UI login page (Modern Managed Login v2)"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/authorize?client_id=${aws_cognito_user_pool_client.native.id}&response_type=code&scope=openid+email+profile+phone&redirect_uri=${urlencode(var.callback_urls[0])}"
}

output "logout_url" {
  description = "The Cognito logout URL"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/logout?client_id=${aws_cognito_user_pool_client.native.id}&logout_uri=${urlencode(var.logout_urls[0])}"
}

output "token_endpoint" {
  description = "The OAuth2 token endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/token"
}

output "userinfo_endpoint" {
  description = "The OAuth2 userinfo endpoint"
  value       = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/userInfo"
}

# ============================================================================
# SSM Parameter Store Paths
# ============================================================================

output "ssm_user_pool_id_path" {
  description = "SSM Parameter path for User Pool ID"
  value       = aws_ssm_parameter.user_pool_id.name
}

output "ssm_client_id_path" {
  description = "SSM Parameter path for Client ID"
  value       = aws_ssm_parameter.client_id.name
}

output "ssm_client_secret_path" {
  description = "SSM Parameter path for Client Secret"
  value       = aws_ssm_parameter.client_secret.name
}

output "ssm_issuer_uri_path" {
  description = "SSM Parameter path for Issuer URI"
  value       = aws_ssm_parameter.issuer_uri.name
}

output "ssm_domain_path" {
  description = "SSM Parameter path for Cognito Domain"
  value       = aws_ssm_parameter.domain.name
}

output "ssm_callback_url_path" {
  description = "SSM Parameter path for Callback URL"
  value       = aws_ssm_parameter.callback_url.name
}

output "ssm_logout_redirect_url_path" {
  description = "SSM Parameter path for Logout Redirect URL"
  value       = aws_ssm_parameter.logout_redirect_url.name
}

output "ssm_jwks_uri_path" {
  description = "SSM Parameter path for JWKS URI"
  value       = aws_ssm_parameter.jwks_uri.name
}

output "ssm_hosted_ui_url_path" {
  description = "SSM Parameter path for Hosted UI URL"
  value       = aws_ssm_parameter.hosted_ui_url.name
}

output "ssm_branding_id_path" {
  description = "SSM Parameter path for Branding ID"
  value       = aws_ssm_parameter.branding_id.name
}

output "ssm_aws_region_path" {
  description = "SSM Parameter path for AWS Region"
  value       = aws_ssm_parameter.aws_region.name
}

# ============================================================================
# User Groups
# ============================================================================

output "user_groups" {
  description = "Map of created user groups"
  value = {
    admin        = aws_cognito_user_group.admin.name
    tenant_admin = aws_cognito_user_group.tenant_admin.name
    user         = aws_cognito_user_group.user.name
  }
}

# ============================================================================
# Configuration Summary (for easy reference)
# ============================================================================

output "cognito_config_summary" {
  description = "Summary of Cognito configuration for application setup"
  value = {
    region                    = var.aws_region
    user_pool_id              = aws_cognito_user_pool.main.id
    client_id                 = aws_cognito_user_pool_client.native.id
    issuer_uri                = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
    hosted_ui_domain          = "${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
    managed_login_version     = "v2 (Modern UI)"
    managed_login_branding_id = aws_cognito_managed_login_branding.main.managed_login_branding_id
    callback_urls             = var.callback_urls
    logout_urls               = var.logout_urls
    logout_redirect_url       = var.logout_urls[0]
  }
}

# ============================================================================
# Spring Boot Configuration Helper
# ============================================================================

output "spring_boot_config" {
  description = "Spring Boot application.yml configuration snippet"
  value       = <<-EOT
    # Add this to your Spring Boot application.yml or application.properties
    spring:
      security:
        oauth2:
          client:
            registration:
              cognito:
                client-id: ${aws_cognito_user_pool_client.native.id}
                client-secret: ${aws_cognito_user_pool_client.native.client_secret}
                scope: openid,email,profile,phone
                redirect-uri: ${var.callback_urls[0]}
                authorization-grant-type: authorization_code
            provider:
              cognito:
                issuer-uri: https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}
                user-name-attribute: username
          resourceserver:
            jwt:
              issuer-uri: https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}
              jwk-set-uri: https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json
  EOT
  sensitive   = true
}
