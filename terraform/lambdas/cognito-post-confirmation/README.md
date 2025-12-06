# Lambda PostConfirmation Handler

AWS Lambda function triggered by Cognito after email verification.

## Purpose

Sets custom user attributes (`custom:tenantId`, `custom:role`) after a user confirms their email address during signup.

## Trigger

- **Event:** Cognito PostConfirmation
- **Trigger Source:** `PostConfirmation_ConfirmSignUp`

## Flow

1. User signs up via `/auth/signup`
2. Cognito sends verification email
3. User clicks verification link
4. Cognito confirms user account
5. **This Lambda is triggered**
6. Lambda sets `custom:tenantId` and `custom:role`
7. User can now login with full tenant context

## Environment Variables

- `USER_POOL_ID` - Cognito User Pool ID (set by Terraform)
- `ENVIRONMENT` - Environment name (dev/staging/prod)

## IAM Permissions Required

```json
{
  "Effect": "Allow",
  "Action": [
    "cognito-idp:AdminUpdateUserAttributes",
    "cognito-idp:AdminGetUser"
  ],
  "Resource": "arn:aws:cognito-idp:*:*:userpool/*"
}
```

## Testing

```bash
# Install dependencies
pip install -r requirements.txt -r requirements-test.txt

# Run tests
pytest test_handler.py -v

# Run with coverage
pytest test_handler.py --cov=index --cov-report=html
```

## Deployment

Deployed via Terraform (see `terraform/lambda.tf`)

## Error Handling

- **Missing tenantId:** Logs error, returns event (user can still login)
- **Cognito API error:** Logs error, returns event (graceful degradation)
- **All errors:** Never blocks user confirmation

## Monitoring

- CloudWatch Logs: `/aws/lambda/cognito-post-confirmation`
- Metrics: Invocations, Errors, Duration
- Alarms: Error rate > 5%
