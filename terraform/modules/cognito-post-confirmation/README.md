# Lambda PostConfirmation Trigger Module

Terraform module for deploying AWS Lambda function that handles Cognito PostConfirmation trigger.

## Purpose

This Lambda function is automatically invoked by AWS Cognito after a user confirms their email address. It sets custom user attributes (`custom:tenantId` and `custom:role`) that are required for the multi-tenant application.

## Usage

```hcl
module "cognito_post_confirmation" {
  source = "./modules/lambda-cognito-trigger"
  
  environment    = var.environment
  user_pool_id   = aws_cognito_user_pool.main.id
  user_pool_arn  = aws_cognito_user_pool.main.arn
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id
}

# Update Cognito User Pool to use the Lambda trigger
resource "aws_cognito_user_pool" "main" {
  # ... other configuration ...
  
  lambda_config {
    post_confirmation = module.cognito_post_confirmation.lambda_function_arn
  }
}
```

## Resources Created

- **Lambda Function**: `{environment}-cognito-post-confirmation`
- **IAM Role**: Lambda execution role with Cognito permissions
- **CloudWatch Log Group**: `/aws/lambda/{function-name}` (14-day retention)
- **Lambda Permission**: Allows Cognito to invoke the function

## Requirements

- Python 3.11 runtime
- Lambda source code in `lambda/post-confirmation/` directory
- Cognito User Pool with custom attributes defined

## Inputs

| Name | Description | Type | Required |
|------|-------------|------|----------|
| environment | Environment name | string | yes |
| user_pool_id | Cognito User Pool ID | string | yes |
| user_pool_arn | Cognito User Pool ARN | string | yes |
| aws_region | AWS region | string | no (default: us-east-1) |
| aws_account_id | AWS Account ID | string | yes |

## Outputs

| Name | Description |
|------|-------------|
| lambda_function_arn | ARN of the Lambda function |
| lambda_function_name | Name of the Lambda function |
| lambda_role_arn | ARN of the Lambda execution role |
