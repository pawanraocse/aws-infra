#!/bin/bash
set -euo pipefail

ENV_FILE="cognito-config.env"

PROJECT_NAME="cloud-infra"
ENVIRONMENT="dev"
AWS_REGION="${AWS_REGION:-us-east-1}"

if [ ! -f "$ENV_FILE" ]; then echo "File $ENV_FILE not found"; exit 1; fi

while IFS='=' read -r key raw; do
  [[ "$key" =~ ^#|^$ ]] && continue
  value=$(echo "$raw" | tr -d '\r')
  export "$key"="$value"
done < "$ENV_FILE"

required=(
  COGNITO_USER_POOL_ID
  COGNITO_CLIENT_ID
  COGNITO_CLIENT_SECRET
  COGNITO_ISSUER_URI
  COGNITO_JWKS_URI
  COGNITO_DOMAIN
  COGNITO_HOSTED_UI_URL
  COGNITO_MANAGED_LOGIN_BRANDING_ID
  COGNITO_REDIRECT_URI
  COGNITO_LOGOUT_REDIRECT_URI
)

for v in "${required[@]}"; do
  [ -z "${!v:-}" ] && { echo "Missing: $v"; exit 1; }
done

put_ssm() {
  aws ssm put-parameter \
    --name "$1" \
    --value "$2" \
    --type "$3" \
    --overwrite \
    --region "$AWS_REGION"
}

BASE="/$PROJECT_NAME/$ENVIRONMENT/cognito"

params=(
  "user_pool_id|$COGNITO_USER_POOL_ID|String"
  "client_id|$COGNITO_CLIENT_ID|String"
  "client_secret|$COGNITO_CLIENT_SECRET|SecureString"
  "issuer_uri|$COGNITO_ISSUER_URI|String"
  "jwks_uri|$COGNITO_JWKS_URI|String"
  "domain|$COGNITO_DOMAIN|String"
  "hosted_ui_url|$COGNITO_HOSTED_UI_URL|String"
  "branding_id|$COGNITO_MANAGED_LOGIN_BRANDING_ID|String"
  "callback_url|$COGNITO_REDIRECT_URI|String"
  "logout_redirect_url|$COGNITO_LOGOUT_REDIRECT_URI|String"
  "aws_region|$AWS_REGION|String"
)

for entry in "${params[@]}"; do
  IFS='|' read -r key value type <<< "$entry"
  put_ssm "$BASE/$key" "$value" "$type"
done

echo "SSM sync complete"
