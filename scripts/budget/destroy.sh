#!/bin/bash
# ============================================================================
# Destroy Budget Environment
# ============================================================================
# Lessons learned from deployment:
# 1. ECR images MUST be deleted BEFORE terraform destroy
# 2. Multi-platform images create multiple digests per tag
# 3. Need to handle repos that don't exist gracefully
# 4. Auto-approve flag for CI/CD automation

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../../terraform/envs/budget"

AWS_PROFILE="${AWS_PROFILE:-personal}"
AWS_REGION="${AWS_REGION:-us-east-1}"
PROJECT_NAME="${PROJECT_NAME:-saas-factory}"
ENVIRONMENT="${ENVIRONMENT:-budget}"

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

# Parse arguments
AUTO_APPROVE=false
SHALLOW=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --auto-approve|-y)
            AUTO_APPROVE=true
            shift
            ;;
        --shallow|-s)
            SHALLOW=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--shallow|-s] [--auto-approve|-y]"
            echo ""
            echo "Options:"
            echo "  --shallow, -s      Stop EC2 and RDS only (keep infrastructure)"
            echo "  --auto-approve, -y Skip confirmation prompts"
            exit 1
            ;;
    esac
done

# =============================================================================
# Shallow Destroy: Stop EC2 and RDS only (keep infrastructure)
# =============================================================================
if [ "$SHALLOW" = true ]; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🔄 Shallow Destroy: Stopping EC2 + RDS (infrastructure preserved)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    # Stop EC2
    log_info "Finding EC2 instance..."
    EC2_ID=$(aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=${PROJECT_NAME}-${ENVIRONMENT}-bastion" \
                  "Name=instance-state-name,Values=running,stopped" \
        --query "Reservations[0].Instances[0].InstanceId" \
        --output text \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" 2>/dev/null || echo "None")
    
    if [ "$EC2_ID" != "None" ] && [ -n "$EC2_ID" ]; then
        EC2_STATE=$(aws ec2 describe-instances --instance-ids "$EC2_ID" \
            --query "Reservations[0].Instances[0].State.Name" \
            --output text \
            --region "$AWS_REGION" \
            --profile "$AWS_PROFILE")
        
        if [ "$EC2_STATE" = "running" ]; then
            log_info "Stopping EC2: $EC2_ID"
            aws ec2 stop-instances --instance-ids "$EC2_ID" \
                --region "$AWS_REGION" \
                --profile "$AWS_PROFILE" >/dev/null
            log_success "EC2 stopping: $EC2_ID"
        else
            log_info "EC2 already $EC2_STATE: $EC2_ID"
        fi
    else
        log_warn "EC2 instance not found"
    fi
    
    # Stop RDS
    log_info "Finding RDS instance..."
    RDS_ID=$(aws rds describe-db-instances \
        --query "DBInstances[?contains(DBInstanceIdentifier, '${PROJECT_NAME}-${ENVIRONMENT}')].DBInstanceIdentifier | [0]" \
        --output text \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" 2>/dev/null || echo "")
    
    if [ -n "$RDS_ID" ] && [ "$RDS_ID" != "None" ]; then
        RDS_STATE=$(aws rds describe-db-instances --db-instance-identifier "$RDS_ID" \
            --query "DBInstances[0].DBInstanceStatus" \
            --output text \
            --region "$AWS_REGION" \
            --profile "$AWS_PROFILE")
        
        if [ "$RDS_STATE" = "available" ]; then
            log_info "Stopping RDS: $RDS_ID"
            aws rds stop-db-instance --db-instance-identifier "$RDS_ID" \
                --region "$AWS_REGION" \
                --profile "$AWS_PROFILE" >/dev/null
            log_success "RDS stopping: $RDS_ID"
        else
            log_info "RDS already $RDS_STATE: $RDS_ID"
        fi
    else
        log_warn "RDS instance not found"
    fi
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_success "Shallow destroy complete!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "💰 Stopped resources will not incur compute charges."
    echo "   Note: Elastic IP (~\$3.60/month) still charged when EC2 is stopped."
    echo ""
    echo "🚀 To restart:      ./scripts/budget/manage.sh start"
    echo "🗑️  Full destroy:    ./scripts/budget/destroy.sh"
    echo ""
    exit 0
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️  Destroy Budget Environment (Full)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# =============================================================================
# Step 1: Clean ECR Images (BEFORE terraform destroy)
# =============================================================================
echo "🧹 Step 1: Cleaning ECR images..."
echo ""

SERVICES=(
    "gateway-service"
    "auth-service"
    "platform-service"
    "backend-service"
    "eureka-server"
    "otel-collector"
)

for svc in "${SERVICES[@]}"; do
    REPO_NAME="${PROJECT_NAME}/${svc}"
    echo "  Checking ${REPO_NAME}..."
    
    # Check if repo exists first
    if ! aws ecr describe-repositories \
        --repository-names "$REPO_NAME" \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" \
        &>/dev/null; then
        echo "    ℹ️  Repository doesn't exist, skipping"
        continue
    fi
    
    # Get all image IDs (handles multi-platform manifests with multiple digests)
    IMAGE_IDS=$(aws ecr list-images \
        --repository-name "$REPO_NAME" \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" \
        --query 'imageIds[*]' \
        --output json)
    
    if [ "$IMAGE_IDS" = "[]" ]; then
        echo "    ℹ️  No images found"
        continue
    fi
    
    # Delete all images (including all digests from multi-platform builds)
    if aws ecr batch-delete-image \
        --repository-name "$REPO_NAME" \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" \
        --image-ids "$IMAGE_IDS" \
        &>/dev/null; then
        echo "    ✅ Cleaned"
    else
        echo "    ⚠️  Failed to clean (may be empty)"
    fi
done

echo ""
echo "✅ ECR images cleaned"
echo ""

# =============================================================================
# Step 2: Destroy Terraform Infrastructure
# =============================================================================
echo "🗑️  Step 2: Destroying infrastructure..."
echo ""

cd "$TERRAFORM_DIR"

if [ "$AUTO_APPROVE" = false ]; then
    terraform plan -destroy
    echo ""
    read -p "Type 'destroy' to confirm: " CONFIRM
    
    if [ "$CONFIRM" != "destroy" ]; then
        echo "Cancelled"
        exit 0
    fi
    
    terraform destroy
else
    echo "ℹ️  Running with --auto-approve flag"
    terraform destroy -auto-approve
fi

echo ""
echo "✅ Infrastructure destroyed"
echo ""

# =============================================================================
# Summary
# =============================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Budget environment destroyed"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💰 AWS charges stopped (~\$20/month → \$0/month)"
echo ""
echo "Resources cleaned:"
echo "  • RDS PostgreSQL (saas_db)"
echo "  • ElastiCache Redis"
echo "  • EC2 Instance (bastion)"
echo "  • CloudFront Distribution"
echo "  • VPC & Networking"
echo "  • ECR Repositories (6)"
echo "  • Cognito User Pool"
echo "  • Lambda Functions (2)"
echo ""
echo "Next deployment: ./scripts/budget/deploy.sh"
echo ""
