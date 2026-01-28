#!/bin/sh
set -e
set -x

# Source global .env if present (project root)
set -a
[ -f /app/../.env ] && . /app/../.env
set +a

# AWS Region
# Unset AWS_PROFILE to use environment credentials
unset AWS_PROFILE
export AWS_REGION=${AWS_REGION:-us-east-1}

echo "[DEBUG] Checking AWS Identity..."
aws sts get-caller-identity || echo "[ERROR] Identity check failed"

# SSM Parameter Path Prefix
PROJECT_NAME=${PROJECT_NAME:-cloud-infra}
ENVIRONMENT=${ENVIRONMENT:-dev}
SSM_PREFIX="/${PROJECT_NAME}/${ENVIRONMENT}/cognito"

echo "[INFO] Fetching SSM parameters from: $SSM_PREFIX (region: $AWS_REGION)"

# Function to fetch SSM parameter with error handling
fetch_ssm_param() {
  local param_name=$1
  local param_path="$SSM_PREFIX/$param_name"
  local decrypt_flag=$2

  local result
  if [ "$decrypt_flag" = "decrypt" ]; then
    result=$(aws ssm get-parameter \
      --name "$param_path" \
      --region "$AWS_REGION" \
      --with-decryption \
      --query "Parameter.Value" \
      --output text 2>&1)
  else
    result=$(aws ssm get-parameter \
      --name "$param_path" \
      --region "$AWS_REGION" \
      --query "Parameter.Value" \
      --output text 2>&1)
  fi

  if [ $? -ne 0 ]; then
    echo "[ERROR] Failed to fetch SSM parameter: $param_path"
    echo "[ERROR] $result"
    exit 1
  fi

  echo "$result"
}

# Fetch SSM parameters with error handling
echo "[INFO] Fetching user_pool_id..."
export COGNITO_USER_POOL_ID=$(fetch_ssm_param "user_pool_id")

echo "[INFO] Fetching client_id..."
export COGNITO_CLIENT_ID=$(fetch_ssm_param "client_id")

echo "[INFO] Fetching client_secret..."
export COGNITO_CLIENT_SECRET=$(fetch_ssm_param "client_secret" "decrypt")

echo "[INFO] Fetching issuer_uri..."
export COGNITO_ISSUER_URI=$(fetch_ssm_param "issuer_uri")

echo "[INFO] Fetching domain..."
export COGNITO_DOMAIN=$(fetch_ssm_param "domain")

echo "[INFO] Fetching callback_url..."
export COGNITO_REDIRECT_URI=$(fetch_ssm_param "callback_url")

echo "[INFO] Fetching logout_redirect_url..."
export COGNITO_LOGOUT_REDIRECT_URL=$(fetch_ssm_param "logout_redirect_url")

# Validate required parameters
if [ -z "$COGNITO_USER_POOL_ID" ] || [ -z "$COGNITO_CLIENT_ID" ] || [ -z "$COGNITO_ISSUER_URI" ] || [ -z "$COGNITO_REDIRECT_URI" ] || [ -z "$COGNITO_LOGOUT_REDIRECT_URL" ]; then
  echo "[ERROR] Missing required Cognito configuration"
  exit 1
fi

echo "[INFO] ✓ Cognito Configuration Loaded:"
echo "  User Pool ID: $COGNITO_USER_POOL_ID"
echo "  Client ID: $COGNITO_CLIENT_ID"
echo "  Issuer URI: $COGNITO_ISSUER_URI"
echo "  Domain: $COGNITO_DOMAIN"
echo "  Redirect URI: $COGNITO_REDIRECT_URI"
echo "  Logout Redirect URL: $COGNITO_LOGOUT_REDIRECT_URL"
echo "  Region: $AWS_REGION"

echo "[INFO] Starting Spring Boot application..."
exec java -jar /app/app.jar
