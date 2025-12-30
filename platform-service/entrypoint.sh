#!/bin/sh
set -e

echo "========================================="
echo "Platform Service - Starting Entrypoint"
echo "========================================="

# Unset AWS_PROFILE to use environment credentials
unset AWS_PROFILE

# AWS Region
export AWS_REGION=${AWS_REGION:-us-east-1}

# SSM Parameter Path Prefix
SSM_PREFIX="/cloud-infra/dev/cognito"

echo "AWS Region: $AWS_REGION"
echo "SSM Prefix: $SSM_PREFIX"

# Function to fetch SSM parameter
fetch_ssm_param() {
  local param_name=$1
  local param_path="$SSM_PREFIX/$param_name"

  echo "Fetching SSM parameter: $param_path" >&2

  local value=$(aws ssm get-parameter \
    --name "$param_path" \
    --region "$AWS_REGION" \
    --query "Parameter.Value" \
    --output text 2>&1)

  local exit_code=$?

  if [ $exit_code -ne 0 ]; then
    echo "ERROR: Failed to fetch parameter $param_path" >&2
    echo "ERROR: $value" >&2
    exit 1
  fi

  echo "$value"
}

# Fetch Cognito configuration from SSM
echo ""
echo "Fetching Cognito configuration from SSM Parameter Store..."
echo "-----------------------------------------------------------"

export COGNITO_USER_POOL_ID=$(fetch_ssm_param "user_pool_id")
export COGNITO_DOMAIN=$(fetch_ssm_param "domain")

# Optional parameters - continue if not found
COGNITO_CLIENT_ID_RESULT=$(fetch_ssm_param "spa_client_id" 2>/dev/null || echo "")
if [ -n "$COGNITO_CLIENT_ID_RESULT" ]; then
  export COGNITO_CLIENT_ID="$COGNITO_CLIENT_ID_RESULT"
fi

echo ""
echo "✅ Configuration loaded successfully!"
echo "-----------------------------------------------------------"
echo "COGNITO_USER_POOL_ID: $COGNITO_USER_POOL_ID"
echo "COGNITO_DOMAIN: $COGNITO_DOMAIN"
echo "COGNITO_CLIENT_ID: ${COGNITO_CLIENT_ID:-<not set>}"
echo "-----------------------------------------------------------"
echo ""

# Start the application
echo "Starting Platform Service..."
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar
