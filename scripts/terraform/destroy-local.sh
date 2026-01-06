#!/bin/bash
# ============================================================================
# Terraform Destroy Script for AWS Cognito
# ============================================================================
# Description: Safely destroys AWS Cognito resources created by Terraform
# Author: DevOps Team
# Version: 1.0.0
# ============================================================================

set -euo pipefail

# Change to terraform directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../../terraform"
cd "$TERRAFORM_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Load environment variables
if [ -f ../.env ]; then
    log_info "Loading environment variables from ../.env"
    set -a
    source ../.env
    set +a
else
    log_warn "No .env file found, using defaults"
fi

# Set AWS profile (hardcoded to 'personal' for safety)
export AWS_PROFILE=${AWS_PROFILE:-personal}
log_info "Using AWS Profile: $AWS_PROFILE"

# Verify AWS credentials
if ! aws sts get-caller-identity --profile "$AWS_PROFILE" > /dev/null 2>&1; then
    log_error "AWS credentials not configured or invalid for profile: $AWS_PROFILE"
    exit 1
fi

log_info "AWS credentials verified"

# Warning message
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️  WARNING: DESTRUCTIVE OPERATION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "This will PERMANENTLY DELETE the following resources:"
echo "  - Cognito User Pool and all users"
echo "  - Cognito User Pool Domain"
echo "  - Cognito User Pool Clients"
echo "  - User Groups"
echo "  - SSM Parameters"
echo ""
echo "This action CANNOT be undone!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Get current resources
log_info "Checking current Terraform state..."
if [ ! -f terraform.tfstate ]; then
    log_error "No terraform.tfstate file found. Nothing to destroy."
    exit 1
fi

# Show what will be destroyed
log_info "Running Terraform plan to show what will be destroyed..."
terraform plan -destroy

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Confirmation prompt
read -p "Are you ABSOLUTELY SURE you want to destroy these resources? Type 'yes' to confirm: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    log_warn "Destruction cancelled by user"
    exit 0
fi

# Second confirmation for production
if [ "${TF_VAR_environment:-dev}" == "prod" ]; then
    echo ""
    log_warn "You are about to destroy PRODUCTION resources!"
    read -p "Type the environment name 'prod' to confirm: " ENV_CONFIRM
    
    if [ "$ENV_CONFIRM" != "prod" ]; then
        log_warn "Destruction cancelled - confirmation failed"
        exit 0
    fi
fi

# Destroy resources
log_info "Destroying Terraform resources..."
terraform destroy -auto-approve

if [ $? -eq 0 ]; then
    log_success "Terraform resources destroyed successfully"

    # Clean up SSM parameters
    log_info "Cleaning up SSM parameters..."

    PROJECT_NAME=${TF_VAR_project_name:-clone-app}
    ENVIRONMENT=${TF_VAR_environment:-dev}
    AWS_REGION=${AWS_REGION:-us-east-1}

    SSM_PARAMS=(
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/user_pool_id"
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/client_id"
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/client_secret"
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/issuer_uri"
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/domain"
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/callback_url"
        "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/logout_redirect_url"
    )

    for param in "${SSM_PARAMS[@]}"; do
        if aws ssm get-parameter --name "$param" --region "$AWS_REGION" > /dev/null 2>&1; then
            aws ssm delete-parameter --name "$param" --region "$AWS_REGION" > /dev/null 2>&1
            log_info "Deleted SSM parameter: $param"
        fi
    done

    log_success "SSM parameters cleaned up"
else
    log_error "Failed to destroy resources"
    exit 1
fi

# Clean up local files
log_info "Cleaning up local files..."

if [ -f cognito-config.env ]; then
    rm -f cognito-config.env
    log_info "Removed cognito-config.env"
fi

if [ -f tfplan ]; then
    rm -f tfplan
    log_info "Removed tfplan"
fi

# Optional: Clean up state files (commented out for safety)
# log_warn "State files are preserved. To remove them manually:"
# echo "  rm -f terraform.tfstate terraform.tfstate.backup"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "Cleanup completed!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
log_info "All resources and SSM parameters have been removed"
echo ""

