# Cognito User Pool Module

Creates and configures AWS Cognito User Pool with:
- Custom attributes (tenantId, role, tenantType)
- Password policy (12+ chars, mixed case, numbers, symbols)
- Email verification
- OAuth2 configuration
- Modern Managed Login UI (v2)
- User groups (admin, tenant-admin, user)

## Usage

```hcl
module "cognito" {
  source = "./modules/cognito-user-pool"

  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id

  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  access_token_validity  = 60  # minutes
  id_token_validity      = 60  # minutes
  refresh_token_validity = 30  # days

  # Optional: SES for custom email
  enable_ses_email = false
}
```

## Outputs

| Output | Description |
|--------|-------------|
| `user_pool_id` | Cognito User Pool ID |
| `user_pool_arn` | User Pool ARN |
| `native_client_id` | Native client ID (with secret) |
| `native_client_secret` | Native client secret |
| `spa_client_id` | SPA client ID (no secret) |
| `domain` | User Pool domain prefix |
| `issuer_uri` | OAuth Issuer URI |
| `jwks_uri` | JWKS URI for token verification |
| `hosted_ui_url` | Hosted UI login URL |
