# Quick Start Guide - AWS Cognito with Modern UI v2

## 🚀 5-Minute Setup

### 1. Prerequisites Check

```bash
# Check Terraform
terraform version  # Should be >= 1.9.0

# Check AWS CLI
aws --version

# Verify AWS credentials
aws sts get-caller-identity
```

### 2. Configure Variables

Edit `terraform.tfvars`:

```hcl
aws_region   = "us-east-1"
project_name = "myapp"
environment  = "dev"

callback_urls = [
  "http://localhost:8081/login/oauth2/code/cognito"
]

logout_urls = [
  "http://localhost:8081/logged-out"
]
```

### 3. Deploy

```bash
cd terraform
./deploy.sh
```

### 4. Test the Modern UI

The script will output a URL like:
```
https://myapp-dev-abc12345.auth.us-east-1.amazoncognito.com/oauth2/authorize?...
```

Open it in your browser to see the **Modern Managed Login UI v2**! 🎨

### 5. Create a Test User

```bash
# Get User Pool ID from output
USER_POOL_ID=$(terraform output -raw user_pool_id)

# Create test user
aws cognito-idp admin-create-user \
  --user-pool-id $USER_POOL_ID \
  --username test@example.com \
  --user-attributes Name=email,Value=test@example.com \
  --temporary-password "TempPass123!" \
  --message-action SUPPRESS
```

### 6. Integrate with Spring Boot

Use the generated `cognito-config.env` file:

```bash
# View configuration
cat cognito-config.env

# Or get from SSM
aws ssm get-parameter --name "/myapp/dev/cognito/user_pool_id"
```

Add to `application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          cognito:
            client-id: ${COGNITO_CLIENT_ID}
            client-secret: ${COGNITO_CLIENT_SECRET}
            scope: openid,email,profile
            redirect-uri: http://localhost:8081/login/oauth2/code/cognito
        provider:
          cognito:
            issuer-uri: ${COGNITO_ISSUER_URI}
```

---

## 🎨 What's Different with Modern UI v2?

### Before (Classic UI)
- ❌ Old, dated design
- ❌ Not responsive
- ❌ Limited customization
- ❌ Poor mobile experience

### After (Modern UI v2)
- ✅ Beautiful, modern design
- ✅ Fully responsive
- ✅ Better accessibility
- ✅ Professional appearance
- ✅ Improved UX

---

## 📋 Common Commands

### View Outputs

```bash
# All outputs
terraform output

# Specific output
terraform output user_pool_id
terraform output hosted_ui_url

# Sensitive output
terraform output -raw client_secret
```

### Update Configuration

```bash
# Edit variables
vim terraform.tfvars

# Apply changes
terraform plan
terraform apply
```

### Manage Users

```bash
USER_POOL_ID=$(terraform output -raw user_pool_id)

# List users
aws cognito-idp list-users --user-pool-id $USER_POOL_ID

# Add user to group
aws cognito-idp admin-add-user-to-group \
  --user-pool-id $USER_POOL_ID \
  --username test@example.com \
  --group-name admin

# Delete user
aws cognito-idp admin-delete-user \
  --user-pool-id $USER_POOL_ID \
  --username test@example.com
```

### SSM Parameters

```bash
# List all parameters
aws ssm describe-parameters --filters "Key=Name,Values=/myapp/"

# Get parameter value
aws ssm get-parameter --name "/myapp/dev/cognito/user_pool_id"

# Get secure parameter
aws ssm get-parameter \
  --name "/myapp/dev/cognito/client_secret" \
  --with-decryption
```

### Cleanup

```bash
# Destroy all resources
./destroy.sh

# Or manually
terraform destroy
```

---

## 🐛 Troubleshooting

### Issue: Old UI Still Showing

**Solution:** Recreate the domain

```bash
terraform destroy -target=aws_cognito_user_pool_domain.main
terraform apply
```

### Issue: Callback URL Mismatch

**Error:** `redirect_mismatch`

**Solution:** Ensure callback URLs match exactly

```bash
# Check current callback URLs
terraform output -json | jq '.cognito_config_summary.value.callback_urls'

# Update in terraform.tfvars
callback_urls = [
  "http://localhost:8081/login/oauth2/code/cognito"  # Must match exactly
]

terraform apply
```

### Issue: SSM Parameters Not Found

**Solution:** Check the parameter path

```bash
# List all parameters
aws ssm describe-parameters

# Check specific path
aws ssm get-parameter --name "/myapp/dev/cognito/user_pool_id"
```

### Issue: AWS Credentials Error

**Solution:** Configure AWS CLI

```bash
# Configure credentials
aws configure

# Or use environment variables
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="us-east-1"
```

---

## 🔐 Security Best Practices

### 1. Never Commit Secrets

```bash
# Add to .gitignore (already done)
echo "terraform/cognito-config.env" >> .gitignore
echo "terraform/*.tfvars" >> .gitignore
echo ".env" >> .gitignore
```

### 2. Use SSM Parameters

```bash
# In your application, fetch from SSM
aws ssm get-parameter \
  --name "/myapp/dev/cognito/client_secret" \
  --with-decryption \
  --query Parameter.Value \
  --output text
```

### 3. Rotate Secrets Regularly

```bash
# Recreate client to get new secret
terraform taint aws_cognito_user_pool_client.native
terraform apply
```

### 4. Enable MFA for Admin Users

```bash
# Set MFA to required for admin group
aws cognito-idp update-group \
  --user-pool-id $USER_POOL_ID \
  --group-name admin \
  --description "Admin group - MFA required"
```

---

## 📊 Monitoring

### CloudWatch Logs

```bash
# View Cognito logs (if enabled)
aws logs tail /aws/cognito/$USER_POOL_ID --follow
```

### Metrics

```bash
# Get authentication metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/Cognito \
  --metric-name UserAuthentication \
  --dimensions Name=UserPool,Value=$USER_POOL_ID \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum
```

---

## 🎯 Next Steps

1. **Customize the UI** (Optional)
   - Uncomment UI customization in `main.tf`
   - Add your logo
   - Customize colors and CSS

2. **Set Up Production**
   - Configure S3 backend
   - Create separate environments
   - Set up monitoring

3. **Integrate with Application**
   - Update Spring Boot config
   - Test OAuth2 flow
   - Implement JWT validation

4. **Add More Features**
   - Social login (Google, Facebook)
   - Custom email templates
   - Lambda triggers

---

## 📚 Resources

- [Terraform AWS Provider Docs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cognito_user_pool)
- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [Modern Managed Login](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-managed-login.html)
- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)

---

**Need Help?** Check `README.md` for detailed documentation or `REVIEW.md` for production readiness assessment.

