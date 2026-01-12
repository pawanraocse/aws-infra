#!/bin/bash
# ============================================================================
# Push Docker Images to ECR
# ============================================================================
# Builds and pushes all service images to ECR
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."

# Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-production}"
PROJECT_NAME="${PROJECT_NAME:-saas-factory}"
ENVIRONMENT="${ENVIRONMENT:-production}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

# Services to build and push
SERVICES=(
    "gateway-service"
    "auth-service"
    "backend-service"
    "platform-service"
)

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
echo "📦 Push Docker Images to ECR"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Region:      $AWS_REGION"
echo "  Profile:     $AWS_PROFILE"
echo "  Image Tag:   $IMAGE_TAG"
echo "  Services:    ${SERVICES[*]}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Get AWS account ID
log_info "Getting AWS account ID..."
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query 'Account' --output text)
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

log_success "ECR Registry: $ECR_REGISTRY"

# Login to ECR
log_info "Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" --profile "$AWS_PROFILE" | \
    docker login --username AWS --password-stdin "$ECR_REGISTRY"
log_success "ECR login successful"

# Build and push each service
cd "$PROJECT_ROOT"

for SERVICE in "${SERVICES[@]}"; do
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_info "Building $SERVICE..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    SERVICE_DIR="$PROJECT_ROOT/$SERVICE"
    
    if [ ! -d "$SERVICE_DIR" ]; then
        log_warn "Directory $SERVICE not found, skipping..."
        continue
    fi
    
    ECR_REPO="${PROJECT_NAME}-${ENVIRONMENT}-${SERVICE}"
    FULL_IMAGE="${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
    
    # Build
    log_info "Building Docker image..."
    docker build -t "$ECR_REPO:$IMAGE_TAG" "$SERVICE_DIR"
    
    # Tag for ECR
    log_info "Tagging image..."
    docker tag "$ECR_REPO:$IMAGE_TAG" "$FULL_IMAGE"
    
    # Push
    log_info "Pushing to ECR..."
    docker push "$FULL_IMAGE"
    
    log_success "$SERVICE pushed to $FULL_IMAGE"
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "All images pushed to ECR!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📚 Next Steps:"
echo "  1. Force new deployment:"
echo "     aws ecs update-service --cluster ${PROJECT_NAME}-${ENVIRONMENT} --service <service-name> --force-new-deployment"
echo ""
echo "  2. Or redeploy all services:"
echo "     for svc in gateway auth backend platform; do"
echo "       aws ecs update-service --cluster ${PROJECT_NAME}-${ENVIRONMENT} --service \${svc}-service --force-new-deployment"
echo "     done"
echo ""
