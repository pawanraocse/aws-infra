#!/bin/bash
# ============================================================================
# Destroy Budget Environment
# ============================================================================
# Safely destroys the Budget AWS infrastructure
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
TERRAFORM_DIR="$PROJECT_ROOT/terraform/envs/budget"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Header
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️  DESTROY Budget Environment"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "This will PERMANENTLY DELETE:"
echo "  - VPC and all subnets"
echo "  - EC2 instance"
echo "  - Amplify app"
echo "  - SSM parameters"
echo ""
echo "This action CANNOT be undone!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check AWS credentials
export AWS_PROFILE=${AWS_PROFILE:-personal}
log_info "Using AWS Profile: $AWS_PROFILE"

if ! aws sts get-caller-identity --profile "$AWS_PROFILE" > /dev/null 2>&1; then
    log_error "AWS credentials not configured"
    exit 1
fi

cd "$TERRAFORM_DIR"

# Check state
if [ ! -f "terraform.tfstate" ] && [ ! -d ".terraform" ]; then
    log_error "No Terraform state found. Nothing to destroy."
    exit 1
fi

# Show destroy plan
log_info "Planning destruction..."
terraform plan -destroy

echo ""
read -p "Are you SURE you want to destroy? Type 'yes' to confirm: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    log_warn "Destruction cancelled"
    exit 0
fi

# Destroy
log_info "Destroying infrastructure..."
terraform destroy -auto-approve

# Clean up
log_info "Cleaning up..."
rm -f tfplan

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "Budget Environment Destroyed!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
log_info "All AWS charges have stopped immediately."
echo ""
