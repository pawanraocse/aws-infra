# Cognito Pre-Token-Generation Lambda Module

Terraform module that creates an AWS Lambda function for the Cognito PreTokenGeneration trigger.

## Purpose

Enables multi-tenant login by dynamically injecting the user's selected tenant ID into the JWT during authentication. This is part of the "Email-First with Smart Tenant Selection" login flow.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Multi-Tenant Login Flow                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. User enters email    ──▶  Frontend calls /api/v1/auth/lookup   │
│                                                                     │
│  2. User selects tenant  ──▶  Frontend stores selectedTenantId     │
│                                                                     │
│  3. User enters password ──▶  Cognito Auth with clientMetadata:    │
│                               { selectedTenantId: "..." }          │
│                                                                     │
│  4. Before token issued  ──▶  PreTokenGeneration Lambda:           │
│                               • Reads selectedTenantId from meta   │
│                               • Overrides custom:tenantId claim    │
│                                                                     │
│  5. JWT issued           ──▶  Contains correct tenantId for        │
│                               the selected workspace               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Usage

```hcl
module "cognito_pre_token_generation" {
  source = "./modules/cognito-pre-token-generation"

  environment    = var.environment
  user_pool_id   = module.cognito.user_pool_id
  user_pool_arn  = module.cognito.user_pool_arn
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id
}

# Attach to Cognito User Pool
resource "aws_cognito_user_pool" "main" {
  # ... other config ...

  lambda_config {
    pre_token_generation = module.cognito_pre_token_generation.lambda_arn
    # If using post_confirmation too:
    # post_confirmation = module.cognito_post_confirmation.lambda_arn
  }
}
```

## Variables

| Name | Description | Type | Required |
|------|-------------|------|----------|
| environment | Environment name | string | yes |
| user_pool_id | Cognito User Pool ID | string | yes |
| user_pool_arn | Cognito User Pool ARN | string | yes |
| aws_region | AWS region | string | yes |
| aws_account_id | AWS account ID | string | yes |

## Outputs

| Name | Description |
|------|-------------|
| lambda_arn | ARN of the Lambda function |
| lambda_function_name | Name of the Lambda function |
| lambda_invoke_arn | Invoke ARN for Cognito trigger config |

## Related Resources

- [PostConfirmation Lambda](../cognito-post-confirmation/) - Initial attribute setup on signup
- [PreTokenGeneration Lambda Source](../../lambdas/cognito-pre-token-generation/) - Python source code
