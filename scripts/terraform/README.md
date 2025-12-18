# Terraform Scripts

Terraform deployment and management scripts for AWS Cognito infrastructure with Lambda triggers.

---

## Scripts

### `deploy.sh`

Deploys AWS Cognito User Pool with Lambda pre-token generation trigger.

**Usage:**
```bash
./scripts/terraform/deploy.sh
```

**What it does:**
1. Validates AWS credentials (`personal` profile)
2. Initializes and validates Terraform
3. Creates execution plan
4. Deploys 23 resources:
   - Cognito User Pool with custom attributes
   - Lambda function for token customization
   - User groups (admin, admin, user)
   - SSM parameters for configuration
5. **Saves Configuration Locally**: Creates `cognito-config.env` with all Cognito details
6. **Updates Frontend Environment Files**: Automatically updates `frontend/src/environments/environment*.ts` with Cognito configuration
7. **Verifies SSM Parameters**: Confirms all parameters are stored in AWS Parameter Store
8. **Displays Next Steps**: Shows you what to do next

**Resources Created:**
- User Pool: `cloud-infra-dev-user-pool`
- Lambda: `cloud-infra-dev-pre-token-generation`
- 11 SSM parameters in `/cloud-infra/dev/cognito/*`

---

### `destroy.sh`

Destroys all Cognito infrastructure.

**Usage:**
```bash
./scripts/terraform/destroy.sh
```

**Safety:**
- Requires double confirmation
- Shows destroy plan before proceeding
- Cleans up local configuration files

⚠️ **WARNING:** This permanently deletes all resources!

---

### `export-ssm.sh`

Exports Cognito configuration to AWS SSM Parameter Store.

**Usage:**
```bash
cd terraform
../scripts/terraform/export-ssm.sh
```

**Prerequisites:**
- `terraform/cognito-config.env` must exist

**Parameters Created:**
- `/cloud-infra/dev/cognito/user_pool_id`
- `/cloud-infra/dev/cognito/client_id`
- `/cloud-infra/dev/cognito/client_secret` (SecureString)
- And 8 more...

---

### `delete-ssm.sh`

Deletes Cognito SSM parameters.

**Usage:**
```bash
./scripts/terraform/delete-ssm.sh
```

**Environment Variables:**
- `TF_VAR_project_name` - defaults to `cloud-infra`
- `TF_VAR_environment` - defaults to `dev`
- `AWS_PROFILE` - defaults to `personal`

---

## Quick Reference

```bash
# Deploy infrastructure
./scripts/terraform/deploy.sh

# Check deployment
aws cognito-idp describe-user-pool \
  --user-pool-id us-east-1_6RGxkqTmA \
  --profile personal

# View all SSM parameters
aws ssm get-parameters-by-path \
  --path "/cloud-infra/dev/cognito" \
  --profile personal

# Get specific SSM parameters
aws ssm get-parameter \
  --name "/cloud-infra/dev/cognito/user_pool_id" \
  --profile personal

aws ssm get-parameter \
  --name "/cloud-infra/dev/cognito/client_id" \
  --profile personal

# Get secure parameter (client secret)
aws ssm get-parameter \
  --name "/cloud-infra/dev/cognito/client_secret" \
  --with-decryption \
  --profile personal

# Get multiple parameters at once
aws ssm get-parameters \
  --names \
    "/cloud-infra/dev/cognito/user_pool_id" \
    "/cloud-infra/dev/cognito/client_id" \
    "/cloud-infra/dev/cognito/domain" \
  --profile personal

# Example output
# {
#   "Value": "us-east-1_6RGxkqTmA",
#   "Type": "String"
# }

# Destroy everything
./scripts/terraform/destroy.sh
```

---

## Configuration

All scripts use:
- **AWS Profile:** `personal` (for safety)
- **Region:** `us-east-1` (from terraform.tfvars)
- **Project:** `cloud-infra`
- **Environment:** `dev`

See [../../terraform/README.md](../../terraform/README.md) for detailed Terraform documentation.
