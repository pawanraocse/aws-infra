#!/bin/bash
# ============================================================================
# Deploy Budget Environment
# ============================================================================
# Deploys AWS infrastructure (VPC, RDS, ElastiCache, EC2, Amplify)
# Configuration is stored in SSM - fetched at runtime by start-budget.sh
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

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Deploy Budget Environment"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check AWS credentials
export AWS_PROFILE=${AWS_PROFILE:-personal}
log_info "Using AWS Profile: $AWS_PROFILE"

if ! aws sts get-caller-identity --profile "$AWS_PROFILE" > /dev/null 2>&1; then
    log_error "AWS credentials not configured for profile: $AWS_PROFILE"
    exit 1
fi
log_success "AWS credentials verified"

# Check for terraform.tfvars
cd "$TERRAFORM_DIR"

if [ ! -f "terraform.tfvars" ]; then
    if [ -f "terraform.tfvars.example" ]; then
        log_warn "terraform.tfvars not found. Copying from example..."
        cp terraform.tfvars.example terraform.tfvars
        log_warn "Please edit terraform/envs/budget/terraform.tfvars"
        exit 1
    fi
fi

# Terraform operations
log_info "Initializing Terraform..."
terraform init -upgrade

log_info "Validating configuration..."
terraform validate

log_info "Planning deployment..."
terraform plan -out=tfplan

# Confirmation
if [ -t 0 ]; then
    echo ""
    read -p "Apply these changes? (yes/no): " CONFIRM
    if [ "$CONFIRM" != "yes" ]; then
        log_warn "Deployment cancelled"
        rm -f tfplan
        exit 0
    fi
fi

log_info "Applying changes..."
terraform apply tfplan
rm -f tfplan

# Get outputs
RDS_ENDPOINT=$(terraform output -raw rds_endpoint 2>/dev/null || echo "N/A")
REDIS_ENDPOINT=$(terraform output -raw redis_endpoint 2>/dev/null || echo "N/A")
EC2_IP=$(terraform output -raw ec2_public_ip 2>/dev/null || echo "N/A")
FRONTEND_URL=$(terraform output -raw frontend_url 2>/dev/null || echo "N/A")

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "Budget Deployment Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📦 Infrastructure:"
echo "  RDS:   $RDS_ENDPOINT"
echo "  Redis: $REDIS_ENDPOINT"
echo "  EC2:   $EC2_IP"
echo ""
echo "📚 Next Steps:"
echo "  1. Copy code to EC2:"
echo "     scp -r . ec2-user@$EC2_IP:/app/"
echo ""
echo "  2. SSH and start services:"
echo "     ssh -i <key.pem> ec2-user@$EC2_IP"
echo "     cd /app && ./scripts/start-budget.sh"
echo ""
echo "  3. Access:"
echo "     Frontend: $FRONTEND_URL"
echo "     API:      http://$EC2_IP:8080"
echo ""
echo "💡 Config is fetched from SSM at runtime - no .env file needed!"
echo "💰 Estimated Cost: ~\$15-30/month"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
