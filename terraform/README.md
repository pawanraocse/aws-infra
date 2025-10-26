# AWS Cognito Terraform Module

Production-ready Terraform configuration for AWS Cognito User Pool with **Modern Managed Login UI (v2)**.

## 🎨 Features

- ✅ **Modern Managed Login UI v2** - Beautiful, responsive login interface
- ✅ **Managed Login Branding** - Automatic branding style creation (REQUIRED for v2)
- ✅ **Multi-tenant support** with custom attributes (tenantId, role)
- ✅ **OAuth 2.0 / OIDC** compliant
- ✅ **MFA support** (TOTP - Software Token, FREE tier)
- ✅ **User groups** with role-based access control
- ✅ **SSM Parameter Store** integration for secure configuration
- ✅ **Production-ready** security settings
- ✅ **AWS Free Tier** optimized
- ✅ **Terraform 1.9+** and AWS Provider 6.17+

## 📋 Prerequisites

- Terraform >= 1.9.0
- AWS CLI configured with valid credentials
- jq (for JSON parsing in deploy script)
- Bash shell

## 🚀 Quick Start

### 1. Configure Variables

Create or update `terraform.tfvars`:

```hcl
aws_region   = "us-east-1"
project_name = "myapp"
environment  = "dev"

callback_urls = [
  "http://localhost:8080/login/oauth2/code/cognito",
  "https://myapp.example.com/callback"
]

logout_urls = [
  "http://localhost:8080/logged-out",
  "https://myapp.example.com"
]

# Token validity
access_token_validity  = 60   # minutes
id_token_validity      = 60   # minutes
refresh_token_validity = 30   # days

# Enable UI customization (optional)
enable_ui_customization = false
```

### 2. Deploy

```bash
cd terraform
./deploy.sh
```

The script will:
1. ✅ Validate AWS credentials
2. ✅ Initialize Terraform
3. ✅ Create execution plan
4. ✅ Apply changes (with confirmation)
5. ✅ Export outputs to SSM Parameter Store
6. ✅ Save configuration to `cognito-config.env`

### 3. Access the Modern UI

After deployment, the script will display the Hosted UI URL:

```
https://your-domain.auth.us-east-1.amazoncognito.com/oauth2/authorize?...
```

## 📁 File Structure

```
terraform/
├── main.tf              # Main Terraform configuration
├── variables.tf         # Input variables with validation
├── outputs.tf           # Output values
├── terraform.tfvars     # Variable values (customize this)
├── deploy.sh            # Deployment automation script
├── destroy.sh           # Cleanup script (optional)
└── README.md            # This file
```

## 🔧 Configuration

### Variables

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `aws_region` | string | `us-east-1` | AWS region for resources |
| `project_name` | string | `awsinfra` | Project identifier (3-20 chars) |
| `environment` | string | `dev` | Environment (dev/staging/prod) |
| `callback_urls` | list(string) | `[localhost:8080/...]` | OAuth callback URLs |
| `logout_urls` | list(string) | `[localhost:8080/logout]` | Logout redirect URLs |
| `access_token_validity` | number | `60` | Access token validity (minutes) |
| `id_token_validity` | number | `60` | ID token validity (minutes) |
| `refresh_token_validity` | number | `30` | Refresh token validity (days) |
| `enable_ui_customization` | bool | `false` | Enable UI customization |

### Outputs

All outputs are automatically stored in AWS SSM Parameter Store:

| Output | SSM Path | Description |
|--------|----------|-------------|
| `user_pool_id` | `/{project}/{env}/cognito/user_pool_id` | User Pool ID |
| `client_id` | `/{project}/{env}/cognito/client_id` | Client ID |
| `client_secret` | `/{project}/{env}/cognito/client_secret` | Client Secret (SecureString) |
| `issuer_uri` | `/{project}/{env}/cognito/issuer_uri` | OIDC Issuer URI |
| `cognito_domain` | `/{project}/{env}/cognito/domain` | Hosted UI domain |
| `callback_url` | `/{project}/{env}/cognito/callback_url` | OAuth2 callback URL |
| `logout_redirect_url` | `/{project}/{env}/cognito/logout_redirect_url` | Logout redirect URL |

**Note:** All callback and logout URLs are fetched from SSM at application startup. No hardcoded values in `entrypoint.sh`.

#### Verify SSM Parameters

```bash
# List all Cognito parameters
aws ssm get-parameters-by-path \
  --path "/clone-app/dev/cognito" \
  --recursive \
  --region us-east-1

# Get specific parameter
aws ssm get-parameter \
  --name "/clone-app/dev/cognito/user_pool_id" \
  --region us-east-1

# Get client secret (decrypted)
aws ssm get-parameter \
  --name "/clone-app/dev/cognito/client_secret" \
  --with-decryption \
  --region us-east-1

# View all in table format
aws ssm get-parameters-by-path \
  --path "/clone-app/dev/cognito" \
  --recursive \
  --with-decryption \
  --query 'Parameters[*].[Name,Value]' \
  --output table \
  --region us-east-1
```

**Note:** Ensure AWS CLI is configured with the correct region:
```bash
aws configure set region us-east-1
```

## 🎨 Modern Managed Login UI v2

### What's New?

The Modern Managed Login UI (v2) provides:

- 🎨 **Modern Design** - Clean, professional interface
- 📱 **Responsive** - Works on all devices
- ♿ **Accessible** - WCAG 2.1 compliant
- 🌐 **Internationalization** - Multi-language support
- 🔒 **Enhanced Security** - Built-in security features

### Enabling Modern UI

The modern UI requires TWO resources:

**1. Domain with Modern UI version:**
```hcl
resource "aws_cognito_user_pool_domain" "main" {
  domain                = "your-domain"
  user_pool_id          = aws_cognito_user_pool.main.id
  managed_login_version = 2  # This enables Modern UI
}
```

**2. Managed Login Branding (REQUIRED):**
```hcl
resource "aws_cognito_managed_login_branding" "main" {
  user_pool_id                = aws_cognito_user_pool.main.id
  client_id                   = aws_cognito_user_pool_client.native.id
  use_cognito_provided_values = true  # Use default branding
}
```

⚠️ **IMPORTANT:** Without the `aws_cognito_managed_login_branding` resource, you'll get:
- "403 Forbidden" errors
- "No login page available" message
- The login page won't be visible even though the domain exists

This resource creates the branding style that makes the login page visible. It's automatically created by the AWS Console but must be explicitly defined in Terraform.

### Customizing the UI (Optional)

Set `use_cognito_provided_values = false` and add custom settings:

```hcl
resource "aws_cognito_managed_login_branding" "main" {
  user_pool_id                = aws_cognito_user_pool.main.id
  client_id                   = aws_cognito_user_pool_client.native.id
  use_cognito_provided_values = false

  # Custom settings (JSON)
  settings = jsonencode({
    backgroundColor = "#FFFFFF"
    primaryColor    = "#4A90E2"
  })

  # Custom assets (logos, backgrounds, etc.)
  assets = [
    {
      category   = "PAGE_HEADER_LOGO"
      color_mode = "LIGHT"
      extension  = "PNG"
      bytes      = filebase64("${path.module}/assets/logo.png")
    }
  ]
}
```

## 🔐 Security Features

### Implemented Security Controls

- ✅ **Strong password policy** (12+ chars, mixed case, numbers, symbols)
- ✅ **Email verification** required
- ✅ **MFA support** (TOTP - Software Token)
- ✅ **Token revocation** enabled
- ✅ **Prevent user enumeration** attacks
- ✅ **Device tracking** for suspicious activity
- ✅ **Account recovery** via verified email
- ✅ **Secure token storage** in SSM Parameter Store

### Free Tier Optimizations

All features are optimized for AWS Free Tier:

- ✅ **Email**: Uses `COGNITO_DEFAULT` (50 emails/day free)
- ✅ **MFA**: Software token only (SMS disabled to avoid charges)
- ✅ **Advanced Security**: Disabled (would incur charges)
- ✅ **Custom Domain**: Uses Cognito domain (custom domains require ACM cert)

### Paid Features (Commented Out)

The following features are commented out to avoid charges:

```hcl
# SMS MFA - Requires SNS (PAID)
# sms_configuration { ... }

# Advanced Security Mode - Risk-based adaptive auth (PAID)
# user_pool_add_ons {
#   advanced_security_mode = "ENFORCED"
# }

# Custom KMS Key - For SSM encryption (PAID)
# key_id = "arn:aws:kms:..."
```

## 🔗 Integration with Spring Boot

### Application Configuration

Use the generated SSM parameters in your Spring Boot app:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          cognito:
            client-id: ${COGNITO_CLIENT_ID}
            client-secret: ${COGNITO_CLIENT_SECRET}
            scope: openid,email,profile,phone
            redirect-uri: http://localhost:8080/login/oauth2/code/cognito
            authorization-grant-type: authorization_code
        provider:
          cognito:
            issuer-uri: ${COGNITO_ISSUER_URI}
            user-name-attribute: username
      resourceserver:
        jwt:
          issuer-uri: ${COGNITO_ISSUER_URI}
          jwk-set-uri: ${COGNITO_JWKS_URI}
```

### Fetching from SSM

```bash
# Using AWS CLI
aws ssm get-parameter --name "/awsinfra/dev/cognito/user_pool_id" --query Parameter.Value --output text

# Using AWS SDK in Spring Boot (recommended)
# Add dependency: spring-cloud-starter-aws-parameter-store-config
```

## 📊 User Groups

Three user groups are created by default:

| Group | Precedence | Description |
|-------|------------|-------------|
| `admin` | 1 | Full system access |
| `tenant-admin` | 2 | Tenant-level administration |
| `user` | 3 | Standard user access |

## 🧪 Testing

### Test the Hosted UI

1. Get the Hosted UI URL from outputs:
   ```bash
   terraform output hosted_ui_url
   ```

2. Open in browser - you'll see the Modern UI

3. Create a test user:
   ```bash
   aws cognito-idp admin-create-user \
     --user-pool-id <USER_POOL_ID> \
     --username test@example.com \
     --user-attributes Name=email,Value=test@example.com \
     --temporary-password "TempPass123!" \
     --message-action SUPPRESS
   ```

## 🗑️ Cleanup

To destroy all resources:

```bash
terraform destroy
```

Or use the cleanup script (if created):

```bash
./destroy.sh
```

## 📝 Best Practices

1. **State Management**
   - For production, use S3 backend with DynamoDB locking
   - Uncomment the backend configuration in `main.tf`

2. **Secrets Management**
   - Never commit `cognito-config.env` to version control
   - Add to `.gitignore`
   - Use SSM Parameter Store or AWS Secrets Manager

3. **Multi-Environment**
   - Use workspaces or separate state files per environment
   - Use different `terraform.tfvars` files

4. **Monitoring**
   - Enable CloudWatch logs for Cognito
   - Set up alarms for failed login attempts

## 🐛 Troubleshooting

### Issue: Old UI still showing

**Solution**: The domain needs to be recreated to enable Modern UI v2:

```bash
terraform destroy -target=aws_cognito_user_pool_domain.main
terraform apply
```

### Issue: SSM parameters not found

**Solution**: Check the parameter paths match your project/environment:

```bash
aws ssm describe-parameters --filters "Key=Name,Values=/awsinfra/"
```

### Issue: Callback URL mismatch

**Solution**: Ensure callback URLs in `terraform.tfvars` match your application exactly.

## 📚 Additional Resources

- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [OAuth 2.0 / OIDC Spec](https://openid.net/specs/openid-connect-core-1_0.html)
- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)

## 📄 License

MIT License - See LICENSE file for details

## 🤝 Contributing

Contributions welcome! Please open an issue or PR.

---

**Note**: This configuration is optimized for AWS Free Tier. Monitor your usage to avoid unexpected charges.

