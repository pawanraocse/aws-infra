#!/bin/sh
set -e

echo "========================================="
echo "Gateway Service - Starting Entrypoint"
echo "========================================="

# Unset AWS_PROFILE to use environment credentials
unset AWS_PROFILE

# AWS Region
export AWS_REGION=${AWS_REGION:-us-east-1}

# SSM Parameter Path Prefix
# SSM Parameter Path Prefix
PROJECT_NAME=${PROJECT_NAME:-cloudinfra}
ENVIRONMENT=${ENVIRONMENT:-dev}
SSM_PREFIX="/${PROJECT_NAME}/${ENVIRONMENT}/cognito"

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

if [ -z "$COGNITO_USER_POOL_ID" ]; then
  export COGNITO_USER_POOL_ID=$(fetch_ssm_param "user_pool_id")
fi

if [ -z "$COGNITO_SPA_CLIENT_ID" ]; then
  export COGNITO_SPA_CLIENT_ID=$(fetch_ssm_param "spa_client_id")
fi

if [ -z "$COGNITO_ISSUER_URI" ]; then
  export COGNITO_ISSUER_URI=$(fetch_ssm_param "issuer_uri")
fi

if [ -z "$COGNITO_JWKS_URI" ]; then
  export COGNITO_JWKS_URI=$(fetch_ssm_param "jwks_uri")
fi

echo ""
echo "✅ Configuration loaded successfully!"
echo "-----------------------------------------------------------"
echo "COGNITO_USER_POOL_ID: $COGNITO_USER_POOL_ID"
echo "COGNITO_SPA_CLIENT_ID: $COGNITO_SPA_CLIENT_ID"
echo "COGNITO_ISSUER_URI: $COGNITO_ISSUER_URI"
echo "COGNITO_JWKS_URI: $COGNITO_JWKS_URI"
echo "-----------------------------------------------------------"
echo ""

# Start the application
echo "Starting Gateway Service..."
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar

