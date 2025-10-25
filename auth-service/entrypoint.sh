#!/bin/sh
set -e

# Source global .env if present (project root)
set -a
[ -f /app/../.env ] && . /app/../.env
set +a

# Region where SSM parameters are stored (config region)
export PARAM_REGION=${PARAM_REGION:-us-east-1}

# Region where Cognito resources exist
export AWS_REGION=${AWS_REGION:-us-east-1}

echo "[INFO] Fetching SSM parameters from region: $PARAM_REGION"

# Fetch SSM parameters
export COGNITO_USER_POOL_ID=$(aws ssm get-parameter \
  --name "/auth-service/user_pool_id" \
  --region "$AWS_REGION" \
  --query "Parameter.Value" \
  --output text)

export COGNITO_CLIENT_ID=$(aws ssm get-parameter \
  --name "/auth-service/native_client_id" \
  --region "$AWS_REGION" \
  --query "Parameter.Value" \
  --output text)

# Optional client secret
# Optional client secret
if aws ssm get-parameter --name "/auth-service/client_secret" --region "$AWS_REGION" --with-decryption --query "Parameter.Value" --output text 2>/dev/null; then
  export COGNITO_CLIENT_SECRET=$(aws ssm get-parameter \
    --name "/auth-service/client_secret" \
    --region "$AWS_REGION" \
    --with-decryption \
    --query "Parameter.Value" \
    --output text)
fi


# Cognito issuer URI depends on actual AWS region of Cognito resources
export COGNITO_ISSUER_URI="https://cognito-idp.${AWS_REGION}.amazonaws.com/${COGNITO_USER_POOL_ID}"

# Redirect URI
export COGNITO_REDIRECT_URI=${COGNITO_REDIRECT_URI:-"http://localhost:8080/login/oauth2/code/cognito"}

echo "[INFO] Starting Spring Boot app..."
exec java -jar /app/app.jar
