#!/bin/bash
# ============================================================================
# Destroy Production Environment
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../terraform/envs/production"

PROJECT_ROOT="$SCRIPT_DIR/../../"

# Load environment variables from .env if present
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo "Loading environment variables from .env"
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
    
    # Override defaults with .env values
    PROJECT_NAME="${PROJECT_NAME:-$PROJECT_NAME}"
    AWS_PROFILE="${AWS_PROFILE:-$AWS_PROFILE}"
    
    # Export for Terraform
    export TF_VAR_project_name="$PROJECT_NAME"
    export TF_VAR_aws_region="${AWS_REGION:-us-east-1}"
    export TF_VAR_environment="${ENVIRONMENT:-prod}"
    
    echo "Using Project Name: $PROJECT_NAME"
fi

AWS_PROFILE="${AWS_PROFILE:-production}"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️  DESTROY Production Environment"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "This will PERMANENTLY DELETE all production resources!"
echo ""

cd "$TERRAFORM_DIR"

# Use common.auto.tfvars for shared settings
COMMON_VARS="-var-file=../../common.auto.tfvars"

terraform plan $COMMON_VARS -destroy
echo ""
read -p "Type 'destroy-production' to confirm: " CONFIRM

if [ "$CONFIRM" != "destroy-production" ]; then
    echo "Cancelled"
    exit 0
fi

terraform destroy $COMMON_VARS -auto-approve

echo ""
echo "✅ Production environment destroyed. AWS charges stopped."
