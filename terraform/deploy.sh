#!/bin/zsh
set -a
[ -f ../.env ] && . ../.env
set +a
# Script to deploy AWS Cognito resources using Terraform with the 'personal' AWS profile

echo "[INFO] Using AWS_PROFILE=$AWS_PROFILE"

terraform init
terraform plan
terraform apply -auto-approve

# Export Cognito outputs to AWS SSM Parameter Store for auth-service
USER_POOL_ID=$(terraform output -raw user_pool_id)
NATIVE_CLIENT_ID=$(terraform output -raw native_client_id)
# FEDERATED_CLIENT_ID=$(terraform output -raw federated_client_id)
AWS_REGION=$(terraform output -raw aws_region 2>/dev/null)

# Validate outputs
if [[ -z "$USER_POOL_ID" ]]; then
  echo "[ERROR] user_pool_id output is missing or empty."
  exit 1
fi
if [[ -z "$NATIVE_CLIENT_ID" ]]; then
  echo "[ERROR] native_client_id output is missing or empty."
  exit 1
fi
#if [[ -z "$FEDERATED_CLIENT_ID" ]]; then
#  echo "[ERROR] federated_client_id output is missing or empty."
#  exit 1
#fi
if [[ -z "$AWS_REGION" ]]; then
  echo "[ERROR] aws_region output is missing or empty."
  exit 1
fi

echo "[INFO] Using AWS_REGION=$AWS_REGION"

# Store parameters in SSM Parameter Store (region explicitly defined)
aws ssm put-parameter \
  --name "/auth-service/user_pool_id" \
  --value "$USER_POOL_ID" \
  --type "String" \
  --region "$AWS_REGION" \
  --overwrite

aws ssm put-parameter \
  --name "/auth-service/native_client_id" \
  --value "$NATIVE_CLIENT_ID" \
  --type "String" \
  --region "$AWS_REGION" \
  --overwrite

# aws ssm put-parameter \
#   --name "/auth-service/federated_client_id" \
#   --value "$FEDERATED_CLIENT_ID" \
#   --type "String" \
#   --region "$AWS_REGION" \
#   --overwrite

aws ssm put-parameter \
  --name "/auth-service/aws_region" \
  --value "$AWS_REGION" \
  --type "String" \
  --region "$PARAM_REGION" \
  --overwrite

# Optional client secret
CLIENT_SECRET=$(terraform output -raw client_secret 2>/dev/null)
if [[ -n "$CLIENT_SECRET" ]]; then
  aws ssm put-parameter \
    --name "/auth-service/client_secret" \
    --value "$CLIENT_SECRET" \
    --type "SecureString" \
    --region "$AWS_REGION" \
    --overwrite
fi

echo "[INFO] Cognito outputs stored in AWS SSM Parameter Store."
