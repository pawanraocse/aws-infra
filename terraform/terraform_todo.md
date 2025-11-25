# Terraform TODO: Add Cognito Pre-Token Generation Lambda Trigger

**Goal:** Add a Lambda function that injects `tenantId` into JWT tokens during Cognito authentication.

---

## Implementation Checklist

### 1. Lambda Function Code
- [ ] **Create Lambda handler file**
  - Path: `terraform/lambda/pre-token-generation/index.mjs`
  - Runtime: Node.js 20.x (free tier)
  - Use ES module syntax (`export const handler`)
  - Extract `custom:tenantId` from user attributes
  - Inject into both access token and ID token using V2 event format
  - Add error handling and CloudWatch logging

### 2. IAM Role for Lambda
- [ ] **Create Lambda execution role**
  - Resource: `aws_iam_role.lambda_pre_token`
  - Trust policy: Allow Lambda service to assume role
  - Attach AWS managed policy: `AWSLambdaBasicExecutionRole` (CloudWatch logs)

### 3. Lambda Function Resource
- [ ] **Create Lambda function**
  - Resource: `aws_lambda_function.pre_token_generation`
  - Runtime: `nodejs20.x`
  - Handler: `index.handler`
  - Source: Archive of `lambda/pre-token-generation/` directory
  - Environment: None needed (reads from event)
  - Timeout: 3 seconds (sufficient for token manipulation)
  - Memory: 128 MB (minimum, keeps costs low)

### 4. Lambda Permission for Cognito
- [ ] **Allow Cognito to invoke Lambda**
  - Resource: `aws_lambda_permission.cognito_invoke`
  - Principal: `cognito-idp.amazonaws.com`
  - Source ARN: User pool ARN
  - Action: `lambda:InvokeFunction`

### 5. Attach Trigger to Cognito User Pool
- [ ] **Update Cognito User Pool**
  - Modify existing `aws_cognito_user_pool.main` resource
  - Add `lambda_config` block:
    ```hcl
    lambda_config {
      pre_token_generation = aws_lambda_function.pre_token_generation.arn
      lambda_version       = "V2_0"  # Required for access token customization
    }
    ```
  - Add `depends_on` to ensure Lambda is created first

### 6. Terraform Cleanup
- [ ] **Ensure clean destroy**
  - Verify no `prevent_destroy = true` on Lambda resources
  - Test `terraform destroy` removes all resources
  - Lambda logs in CloudWatch will persist (manual cleanup if needed)

---

## Free Tier Compliance

✅ **Lambda:** 1M requests/month free (typical auth usage: <10K/month)
✅ **CloudWatch Logs:** 5GB ingestion free (Lambda logs are minimal)
✅ **No additional costs** expected

---

## Testing Plan

1. **Deploy:** `terraform apply`
2. **Create test user** with `custom:tenantId` attribute
3. **Login** via Cognito Hosted UI or API
4. **Decode JWT** access token (use jwt.io)
5. **Verify** `tenantId` claim exists in token
6. **Check logs** in CloudWatch for Lambda execution

---

## Lambda Code Structure

```javascript
export const handler = async (event) => {
  console.log('Pre Token Generation V2 Event:', JSON.stringify(event, null, 2));
  
  try {
      const userAttributes = event.request.userAttributes;
      const tenantId = userAttributes['custom:tenantId'] || 'default';
      
      console.log('Found tenantId:', tenantId);
      
      if (!event.response) {
          event.response = {};
      }
      
      // V2 Response Format
      event.response.claimsAndScopeOverrideDetails = {
          accessTokenGeneration: {
              claimsToAddOrOverride: {
                  'tenantId': tenantId,
                  'custom:tenantId': tenantId
              }
          },
          idTokenGeneration: {
              claimsToAddOrOverride: {
                  'tenantId': tenantId
              }
          }
      };
      
      console.log('V2 Response:', JSON.stringify(event.response, null, 2));
      return event;
      
  } catch (error) {
      console.error('Error in Pre Token Generation V2:', error);
      return event;
  }
};
```

---

## Files to Create/Modify

### New Files:
- `terraform/lambda/pre-token-generation/index.mjs` (Lambda handler)

### Modified Files:
- `terraform/main.tf` (add Lambda resources + update Cognito user pool)

---

**Last Updated:** 2025-11-24
