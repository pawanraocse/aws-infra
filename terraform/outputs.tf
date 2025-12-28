# =============================================================================
# Terraform Outputs
# =============================================================================
# All outputs now reference modules instead of direct resources.
# =============================================================================

# =============================================================================
# Cognito Outputs (from cognito-user-pool module)
# =============================================================================

output "user_pool_id" {
  description = "The ID of the Cognito User Pool"
  value       = module.cognito.user_pool_id
}

output "user_pool_arn" {
  description = "The ARN of the Cognito User Pool"
  value       = module.cognito.user_pool_arn
}

output "user_pool_endpoint" {
  description = "The endpoint of the Cognito User Pool"
  value       = module.cognito.user_pool_endpoint
}

output "client_id" {
  description = "The ID of the Cognito User Pool Client (Native - with secret)"
  value       = module.cognito.native_client_id
}

output "spa_client_id" {
  description = "The ID of the Cognito User Pool Client (SPA - public, no secret)"
  value       = module.cognito.spa_client_id
}

output "client_secret" {
  description = "The secret of the Cognito User Pool Client (sensitive)"
  value       = module.cognito.native_client_secret
  sensitive   = true
}

output "cognito_domain" {
  description = "The Cognito Hosted UI domain"
  value       = module.cognito.domain
}

output "cognito_domain_cloudfront" {
  description = "The CloudFront distribution for the Cognito domain"
  value       = module.cognito.domain_cloudfront_distribution_arn
}

output "managed_login_branding_id" {
  description = "The ID of the Managed Login Branding Style"
  value       = module.cognito.branding_id
}

output "issuer_uri" {
  description = "The OIDC issuer URI for the Cognito User Pool"
  value       = module.cognito.issuer_uri
}

output "jwks_uri" {
  description = "The JWKS URI for token validation"
  value       = module.cognito.jwks_uri
}

# =============================================================================
# Hosted UI URLs
# =============================================================================

output "hosted_ui_url" {
  description = "The full URL for the Cognito Hosted UI login page (Modern Managed Login v2)"
  value       = module.cognito.hosted_ui_url
}

output "logout_url" {
  description = "The Cognito logout URL"
  value       = "https://${module.cognito.domain}.auth.${var.aws_region}.amazoncognito.com/logout?client_id=${module.cognito.native_client_id}&logout_uri=${urlencode(var.logout_urls[0])}"
}

output "token_endpoint" {
  description = "The OAuth2 token endpoint"
  value       = "https://${module.cognito.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/token"
}

output "userinfo_endpoint" {
  description = "The OAuth2 userinfo endpoint"
  value       = "https://${module.cognito.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/userInfo"
}

# =============================================================================
# SSM Parameter Store Paths (from ssm-parameters module)
# =============================================================================

output "ssm_base_path" {
  description = "Base SSM path for this project/environment"
  value       = module.ssm_parameters.base_path
}

output "ssm_cognito_path" {
  description = "SSM path for Cognito parameters"
  value       = module.ssm_parameters.cognito_path
}

# =============================================================================
# User Groups (from cognito-user-pool module)
# =============================================================================

output "user_groups" {
  description = "Map of created user groups"
  value = {
    admin        = module.cognito.admin_group_name
    tenant_admin = module.cognito.tenant_admin_group_name
    user         = module.cognito.user_group_name
  }
}

# =============================================================================
# Configuration Summary
# =============================================================================

output "cognito_config_summary" {
  description = "Summary of Cognito configuration for application setup"
  value = {
    region                    = var.aws_region
    user_pool_id              = module.cognito.user_pool_id
    client_id                 = module.cognito.native_client_id
    issuer_uri                = module.cognito.issuer_uri
    hosted_ui_domain          = "${module.cognito.domain}.auth.${var.aws_region}.amazoncognito.com"
    managed_login_version     = "v2 (Modern UI)"
    managed_login_branding_id = module.cognito.branding_id
    callback_urls             = var.callback_urls
    logout_urls               = var.logout_urls
  }
}

# =============================================================================
# Spring Boot Configuration Helper
# =============================================================================

output "spring_boot_config" {
  description = "Spring Boot application.yml configuration snippet"
  value       = <<-EOT
    spring:
      security:
        oauth2:
          client:
            registration:
              cognito:
                client-id: ${module.cognito.native_client_id}
                client-secret: <from-ssm>
                scope: openid,email,profile,phone
                redirect-uri: ${var.callback_urls[0]}
                authorization-grant-type: authorization_code
            provider:
              cognito:
                issuer-uri: ${module.cognito.issuer_uri}
                user-name-attribute: username
          resourceserver:
            jwt:
              issuer-uri: ${module.cognito.issuer_uri}
              jwk-set-uri: ${module.cognito.jwks_uri}
  EOT
}

# =============================================================================
# SES Configuration
# =============================================================================

output "ses_email_sending_mode" {
  description = "Email sending mode: DEVELOPER (SES) or COGNITO_DEFAULT"
  value       = var.enable_ses_email ? "DEVELOPER (SES)" : "COGNITO_DEFAULT"
}

output "ses_from_email" {
  description = "From email address for SES (if enabled)"
  value       = var.enable_ses_email ? var.ses_from_email : "no-reply@verificationemail.com (Cognito default)"
}
