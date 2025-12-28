# SSM Parameters Module

Creates all SSM parameters for application configuration.
Parameters are stored in `/${project_name}/${environment}/` path.

## Parameters Created

| Path | Type | Description |
|------|------|-------------|
| `/.../cognito/user_pool_id` | String | Cognito User Pool ID |
| `/.../cognito/client_id` | String | Native client ID |
| `/.../cognito/client_secret` | SecureString | Native client secret |
| `/.../cognito/issuer_uri` | String | OAuth Issuer URI |
| `/.../cognito/jwks_uri` | String | JWKS URI |
| `/.../cognito/domain` | String | Hosted UI domain |
| `/.../cognito/hosted_ui_url` | String | Full login URL |
| `/.../cognito/branding_id` | String | Branding ID |
| `/.../cognito/callback_url` | String | OAuth callback |
| `/.../cognito/logout_redirect_url` | String | Logout redirect |
| `/.../aws/region` | String | AWS region |
| `/.../ses/from_email` | String | SES sender |
| `/.../ses/enabled` | String | SES enabled flag |

## Usage

```hcl
module "ssm_parameters" {
  source = "./modules/ssm-parameters"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  # From cognito-user-pool module
  user_pool_id        = module.cognito.user_pool_id
  client_id           = module.cognito.native_client_id
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
}
```
