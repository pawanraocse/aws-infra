# Cognito Pre-Token-Generation Lambda

## Purpose

This Lambda function is triggered by AWS Cognito before generating JWT tokens during authentication. It enables the **multi-tenant login flow** by allowing users to select which tenant to access when they belong to multiple tenants.

## How It Works

```
User Login → Select Tenant → Cognito Auth → PreTokenGeneration Lambda → JWT with selected tenantId
```

1. User logs in and selects a specific tenant
2. Frontend passes `selectedTenantId` in `clientMetadata`
3. This Lambda intercepts token generation
4. Overrides `custom:tenantId` claim with the selected tenant
5. JWT is issued with the correct tenant context

## Event Flow

### Input Event Example

```json
{
  "userName": "john@acme.com",
  "triggerSource": "TokenGeneration_Authentication",
  "request": {
    "userAttributes": {
      "custom:tenantId": "t_personal_123",
      "custom:role": "owner",
      "custom:tenantType": "PERSONAL"
    },
    "clientMetadata": {
      "selectedTenantId": "t_acme_org"
    }
  },
  "response": {}
}
```

### Output Event (Modified)

```json
{
  "response": {
    "claimsOverrideDetails": {
      "claimsToAddOrOverride": {
        "custom:tenantId": "t_acme_org",
        "custom:role": "owner",
        "custom:tenantType": "PERSONAL"
      }
    }
  }
}
```

## Trigger Configuration

This Lambda should be attached to the Cognito User Pool as a **Pre Token Generation** trigger:

```hcl
resource "aws_cognito_user_pool" "main" {
  lambda_config {
    pre_token_generation = aws_lambda_function.pre_token_generation.arn
  }
}
```

## IAM Permissions

The Lambda only needs basic execution permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
```

## Files

| File | Description |
|------|-------------|
| `handler.py` | Main Lambda function |
| `requirements.txt` | Python dependencies (none) |
| `requirements-test.txt` | Test dependencies |
| `test_handler.py` | Unit tests |

## Testing

```bash
# Install test dependencies
pip install -r requirements-test.txt

# Run tests
pytest test_handler.py -v

# Run with coverage
pytest test_handler.py --cov=handler --cov-report=term-missing
```

## Deployment

```bash
# Create deployment package
cd terraform/lambdas/cognito-pre-token-generation
zip -r lambda_function.zip handler.py

# Deploy via Terraform
cd ../..
terraform apply
```

## Security Considerations

1. **No External Calls**: This Lambda makes no network calls, reducing latency and attack surface
2. **Graceful Degradation**: Errors don't block authentication - falls back to stored tenant
3. **Logging**: All operations are logged for audit purposes
4. **Input Validation**: Handles missing/malformed input gracefully

## Related Components

- **PostConfirmation Lambda**: Sets initial tenant attributes after signup
- **Auth Service**: `/api/v1/auth/lookup` endpoint for tenant discovery
- **Frontend**: Login component with tenant selector UI
