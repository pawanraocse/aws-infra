# Project Scripts Documentation

This directory contains all utility scripts for building, deploying, and managing the AWS Infrastructure project.

---

## 📁 Directory Structure

```
scripts/
├── README.md              # This file
├── build/
│   └── build-all.sh      # Build Maven services + Docker images
├── terraform/
│   ├── deploy.sh         # Deploy Cognito resources to AWS
│   ├── destroy.sh        # Destroy Cognito resources
│   ├── export-ssm.sh     # Export Cognito config to SSM Parameter Store
│   └── delete-ssm.sh     # Delete Cognito parameters from SSM
├── env/
│   └── export-envs.sh    # Export environment variables from multiple .env files
└── local/
    └── README.md         # Local development scripts
```

---

## 🏗️ Build Scripts

### `build/build-all.sh`

Builds all Maven microservices and creates Docker images.

**Usage:**
```bash
./scripts/build/build-all.sh
```

**What it does:**
1. Runs `mvn clean package -DskipTests`
2. Builds Docker images for:
   - eureka-server
   - auth-service
   - backend-service
   - gateway-service (when ready)

**Prerequisites:**
- Maven installed
- Docker installed and running

---

## ☁️ Terraform Scripts

All terraform scripts use the `personal` AWS profile by default for safety.

### `terraform/deploy.sh`

Deploys AWS Cognito resources including Lambda trigger.

**Usage:**
```bash
./scripts/terraform/deploy.sh
```

**What it does:**
1. Validates AWS credentials (personal profile)
2. Initializes Terraform
3. Validates and formats configuration
4. Creates execution plan
5. Prompts for confirmation
6. Applies changes
7. Exports outputs to SSM Parameter Store
8. Saves configuration to `cognito-config.env`

**Environment Variables:**
- `AWS_PROFILE` - defaults to `personal`
- `AWS_REGION` - defaults to value in terraform.tfvars

**Output:**
- Creates 23 AWS resources
- SSM parameters in `/cloud-infra/dev/cognito/*`
- Local file: `terraform/cognito-config.env`

---

### `terraform/destroy.sh`

Safely destroys all Cognito resources.

**Usage:**
```bash
./scripts/terraform/destroy.sh
```

**What it does:**
1. Validates AWS credentials
2. Shows destroy plan
3. Requires double confirmation
4. Destroys all Terraform-managed resources
5. Cleans up local files

**Safety Features:**
- Double confirmation required
- Additional confirmation for production
- Shows what will be destroyed before proceeding

⚠️ **WARNING:** This is destructive and cannot be undone!

---

### `terraform/export-ssm.sh`

Exports Cognito configuration to AWS SSM Parameter Store.

**Usage:**
```bash
cd terraform
../scripts/terraform/export-ssm.sh
```

**Prerequisites:**
- `cognito-config.env` file must exist in `terraform/` directory

**What it does:**
- Reads `terraform/cognito-config.env`
- Validates all required variables
- Creates/updates SSM parameters
- Uses SecureString for client_secret

**SSM Parameters Created:**
```
/cloud-infra/dev/cognito/user_pool_id
/cloud-infra/dev/cognito/client_id
/cloud-infra/dev/cognito/client_secret (SecureString)
/cloud-infra/dev/cognito/issuer_uri
/cloud-infra/dev/cognito/jwks_uri
/cloud-infra/dev/cognito/domain
/cloud-infra/dev/cognito/hosted_ui_url
/cloud-infra/dev/cognito/branding_id
/cloud-infra/dev/cognito/callback_url
/cloud-infra/dev/cognito/logout_redirect_url
/cloud-infra/dev/aws/region
```

---

### `terraform/delete-ssm.sh`

Deletes Cognito SSM parameters from AWS.

**Usage:**
```bash
./scripts/terraform/delete-ssm.sh
```

**Environment Variables:**
- `TF_VAR_project_name` - defaults to `cloud-infra`
- `TF_VAR_environment` - defaults to `dev`
- `AWS_REGION` - defaults to `us-east-1`
- `AWS_PROFILE` - defaults to `personal`

**What it does:**
- Validates AWS credentials
- Deletes all Cognito-related SSM parameters
- Skips parameters that don't exist (no error)

---

## 🔧 Environment Scripts

### `env/export-envs.sh`

Exports environment variables from multiple .env files.

**Usage:**
```bash
source ./scripts/env/export-envs.sh
```

**Note:** Must use `source` to export variables to current shell.

**What it loads:**
1. Root `.env` (if exists)
2. `auth-service/.env` (if exists)
3. `terraform/cognito-config.env` (if exists)

**Example:**
```bash
# Load all environment variables
source ./scripts/env/export-envs.sh

# Verify variables are loaded
echo $COGNITO_USER_POOL_ID
echo $COGNITO_CLIENT_ID
```

---

## 🚀 Quick Start Examples

### Initial Setup

```bash
# 1. Deploy Cognito infrastructure
./scripts/terraform/deploy.sh

# 2. Build all services
./scripts/build/build-all.sh

# 3. Load environment variables
source ./scripts/env/export-envs.sh

# 4. Start services
docker-compose up -d
```

### Daily Development

```bash
# Build and restart a service
./scripts/build/build-all.sh
docker-compose restart backend-service

# Check Cognito configuration
aws ssm get-parameters-by-path \
  --path "/cloud-infra/dev/cognito" \
  --profile personal \
  --region us-east-1
```

### Cleanup

```bash
# Stop services
docker-compose down

# Destroy infrastructure
./scripts/terraform/destroy.sh
```

---

## 🔐 Security Notes

1. **AWS Profile:** All terraform scripts use `personal` profile by default to prevent accidental changes to work resources.

2. **Sensitive Data:** Never commit:
   - `cognito-config.env`
   - `.env` files
   - SSM parameter values

3. **SSM Parameters:** Client secret is stored as `SecureString` type in SSM.

---

## 📝 Script Conventions

All scripts follow these conventions:

- **Shebang:** `#!/bin/bash`
- **Error handling:** `set -euo pipefail`
- **Logging:** Colored output (GREEN=info, YELLOW=warn, RED=error)
- **AWS Profile:** Defaults to `personal`
- **Confirmation:** Destructive operations require user confirmation

---

## 🐛 Troubleshooting

### Issue: "AWS credentials not configured"

**Solution:**
```bash
aws configure --profile personal
# OR
export AWS_PROFILE=personal
```

### Issue: "Command not found: terraform"

**Solution:**
```bash
brew install terraform
# OR download from terraform.io
```

### Issue: "SSM parameter not found"

**Solution:**
```bash
# Check if parameters exist
aws ssm describe-parameters \
  --filters "Key=Name,Values=/cloud-infra/" \
  --profile personal

# Re-run deploy to create them
./scripts/terraform/deploy.sh
```

---

## 📚 Additional Resources

- [Terraform README](../terraform/README.md) - Detailed Terraform documentation
- [Docker Compose](../docker-compose.yml) - Service orchestration
- [HLD](../HLD.md) - High-level design documentation

---

**Last Updated:** 2025-11-25  
**Maintained By:** DevOps Team
