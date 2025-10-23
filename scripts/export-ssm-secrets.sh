#!/bin/bash
# Export all required AWS SSM Parameter Store secrets as environment variables for local development
# Usage: source ./scripts/export-ssm-secrets.sh

set -euo pipefail

# Check for AWS CLI
if ! command -v aws >/dev/null 2>&1; then
  echo "[ERROR] AWS CLI is not installed. Please install and configure it first." >&2
  return 1
fi

# Check AWS authentication
if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "[ERROR] AWS CLI is not authenticated. Run 'aws configure' or set your credentials." >&2
  return 1
fi

# Export secrets from SSM Parameter Store
export GOOGLE_CLIENT_ID=$(aws ssm get-parameter --name "/auth-service/google_client_id" --query Parameter.Value --output text 2>/dev/null || true)
export GOOGLE_CLIENT_SECRET=$(aws ssm get-parameter --name "/auth-service/google_client_secret" --with-decryption --query Parameter.Value --output text 2>/dev/null || true)

if [[ -z "$GOOGLE_CLIENT_ID" ]]; then
  echo "[ERROR] GOOGLE_CLIENT_ID not found in SSM Parameter Store." >&2
  return 1
fi
if [[ -z "$GOOGLE_CLIENT_SECRET" ]]; then
  echo "[ERROR] GOOGLE_CLIENT_SECRET not found in SSM Parameter Store." >&2
  return 1
fi

echo "[INFO] GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET exported as environment variables."
