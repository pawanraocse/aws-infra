#!/bin/bash
set -euo pipefail

PROJECT_NAME="${TF_VAR_project_name:-cloud-infra}"
ENVIRONMENT="${TF_VAR_environment:-dev}"
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-default}"

aws sts get-caller-identity --profile "$AWS_PROFILE" --region "$AWS_REGION" >/dev/null 2>&1 || {
  echo "Invalid AWS session"
  exit 1
}

BASE="/$PROJECT_NAME/$ENVIRONMENT/cognito"

PARAMS=(
  "$BASE/user_pool_id"
  "$BASE/client_id"
  "$BASE/client_secret"
  "$BASE/issuer_uri"
  "$BASE/jwks_uri"
  "$BASE/domain"
  "$BASE/hosted_ui_url"
  "$BASE/branding_id"
  "$BASE/callback_url"
  "$BASE/logout_redirect_url"
  "$BASE/aws_region"
)

for param in "${PARAMS[@]}"; do
  echo "Deleting $param"
  aws ssm delete-parameter \
    --name "$param" \
    --region "$AWS_REGION" \
    --profile "$AWS_PROFILE" \
    >/dev/null 2>&1 \
    && echo "Deleted" \
    || echo "Skip"
done

echo "Done"
