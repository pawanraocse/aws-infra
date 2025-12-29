# Cognito Identity Provider Module

This Terraform module manages SAML 2.0 and OIDC identity providers for AWS Cognito User Pool.

## Features

- **SAML 2.0 Providers**: Okta, Azure AD, Ping Identity, custom SAML IdPs
- **Google Sign-In**: Native Google OAuth integration
- **Generic OIDC**: Support for any OIDC-compliant provider
- **Attribute Mapping**: Configurable claim-to-attribute mappings

## Usage

### Google Sign-In Only

```hcl
module "identity_providers" {
  source = "./modules/cognito-identity-provider"

  user_pool_id = module.cognito.user_pool_id
  project_name = "my-app"
  environment  = "prod"

  # Google
  google_provider_enabled = true
  google_client_id        = var.google_client_id
  google_client_secret    = var.google_client_secret
}
```

### Enterprise SAML (Okta)

```hcl
module "identity_providers" {
  source = "./modules/cognito-identity-provider"

  user_pool_id = module.cognito.user_pool_id
  project_name = "my-app"
  environment  = "prod"

  # SAML
  saml_provider_enabled = true
  saml_provider_name    = "Okta"
  saml_metadata_url     = "https://dev-xxx.okta.com/app/xxx/sso/saml/metadata"
  
  saml_attribute_mapping = {
    "custom:groups"   = "http://schemas.xmlsoap.org/claims/Group"
    "custom:tenantId" = "http://schemas.example.com/claims/tenantId"
  }
}
```

### Multiple Providers

```hcl
module "identity_providers" {
  source = "./modules/cognito-identity-provider"

  user_pool_id = module.cognito.user_pool_id
  project_name = "my-app"
  environment  = "prod"

  # Google
  google_provider_enabled = true
  google_client_id        = var.google_client_id
  google_client_secret    = var.google_client_secret

  # SAML
  saml_provider_enabled = true
  saml_provider_name    = "AzureAD"
  saml_metadata_url     = var.azure_metadata_url
}
```

## IdP Configuration

After applying, configure your IdP with these values:

| Setting | Value |
|---------|-------|
| **ACS URL** | `https://{domain}.auth.{region}.amazoncognito.com/saml2/idpresponse` |
| **Entity ID** | `urn:amazon:cognito:sp:{user_pool_id}` |
| **Name ID Format** | Email |

## Inputs

See [variables.tf](./variables.tf) for all input variables.

## Outputs

| Name | Description |
|------|-------------|
| `enabled_providers` | List of enabled provider names |
| `saml_acs_url` | SAML ACS URL for IdP configuration |
| `saml_entity_id` | SAML Entity ID for IdP configuration |
