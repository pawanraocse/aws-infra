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

# Parse arguments
AUTO_APPROVE=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --auto-approve|-y)
            AUTO_APPROVE=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--auto-approve|-y]"
            exit 1
            ;;
    esac
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️  Destroy Budget Environment"
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
