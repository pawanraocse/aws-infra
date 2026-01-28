#!/bin/bash
# ============================================================================
# Full Production Deployment - One Shot
# ============================================================================
# Deploys infrastructure, pushes images, and triggers ECS deployment
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
TERRAFORM_DIR="$PROJECT_ROOT/terraform/envs/production"

AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-production}"
PROJECT_NAME="${PROJECT_NAME:-cloud-infra}"
ENVIRONMENT="${ENVIRONMENT:-production}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

# Load environment variables from .env if present
if [ -f "$PROJECT_ROOT/.env" ]; then
    log_info "Loading environment variables from .env"
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
    
    # Override defaults with .env values
    PROJECT_NAME="${PROJECT_NAME:-$PROJECT_NAME}"
    AWS_REGION="${AWS_REGION:-$AWS_REGION}"
    
    # Export for Terraform
    export TF_VAR_project_name="$PROJECT_NAME"
    export TF_VAR_aws_region="$AWS_REGION"
    export TF_VAR_environment="$ENVIRONMENT"

    if [ -n "${GOOGLE_CLIENT_ID:-}" ]; then
        export TF_VAR_google_client_id="$GOOGLE_CLIENT_ID"
    fi
    if [ -n "${GOOGLE_CLIENT_SECRET:-}" ]; then
        export TF_VAR_google_client_secret="$GOOGLE_CLIENT_SECRET"
    fi
fi

SERVICES=("gateway-service" "auth-service" "backend-service" "platform-service" "eureka-server")

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
echo "🚀 Full Production Deployment"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Phases:"
echo "    1. Deploy Infrastructure (Terraform)"
echo "    2. Build & Push Docker Images (ECR)"
echo "    3. Deploy Services (ECS)"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check AWS credentials
if ! aws sts get-caller-identity --profile "$AWS_PROFILE" > /dev/null 2>&1; then
    log_error "AWS credentials not configured for profile: $AWS_PROFILE"
    exit 1
fi
log_success "AWS credentials verified"

# ============================================================================
# Phase 1: Deploy Infrastructure
# ============================================================================
echo ""
log_info "━━━ Phase 1/3: Infrastructure ━━━"

cd "$TERRAFORM_DIR"

if [ ! -f "terraform.tfvars" ]; then
    log_warn "terraform.tfvars not found. Let's create it!"
    echo ""
    
    # Prompt for values
    read -p "Project name [saas-factory]: " INPUT_PROJECT
    INPUT_PROJECT="${INPUT_PROJECT:-saas-factory}"
    
    read -p "AWS Region [us-east-1]: " INPUT_REGION
    INPUT_REGION="${INPUT_REGION:-us-east-1}"
    
    echo ""
    echo "ACM Certificate ARN (for HTTPS):"
    echo "  Get from: AWS Console → ACM → Certificates"
    read -p "  ARN: " ACM_CERT_ARN
    
    echo ""
    echo "GitHub repository URL for frontend:"
    read -p "  (e.g., https://github.com/your-org/your-repo): " FRONTEND_REPO
    
    echo ""
    echo "GitHub Personal Access Token (for Amplify):"
    echo "  Get from: GitHub → Settings → Developer → Personal Access Tokens"
    read -sp "  Token (hidden): " GITHUB_TOKEN
    echo ""
    
    echo ""
    echo "Your public IP for bastion SSH (run 'curl ifconfig.me'):"
    read -p "  IP Address: " MY_IP
    
    echo ""
    echo "SSH public key path:"
    read -p "  Path [~/.ssh/id_rsa.pub]: " SSH_KEY_PATH
    SSH_KEY_PATH="${SSH_KEY_PATH:-~/.ssh/id_rsa.pub}"
    SSH_KEY_PATH="${SSH_KEY_PATH/#\~/$HOME}"
    
    if [ -f "$SSH_KEY_PATH" ]; then
        SSH_PUBLIC_KEY=$(cat "$SSH_KEY_PATH")
    else
        log_warn "SSH key not found at $SSH_KEY_PATH"
        read -p "  Paste SSH public key: " SSH_PUBLIC_KEY
    fi
    
    # Generate terraform.tfvars
    cat > terraform.tfvars << EOF
# Auto-generated on $(date)
project_name = "$INPUT_PROJECT"
environment  = "production"
aws_region   = "$INPUT_REGION"

# SSL Certificate
acm_certificate_arn = "$ACM_CERT_ARN"

# Frontend
frontend_repository_url = "$FRONTEND_REPO"
github_access_token     = "$GITHUB_TOKEN"
frontend_branch         = "main"
frontend_app_root       = "frontend"

# Bastion
create_bastion            = true
bastion_allowed_ssh_cidrs = ["${MY_IP}/32"]
bastion_ssh_public_key    = "$SSH_PUBLIC_KEY"

# RDS
rds_instance_class = "db.t3.small"
rds_multi_az       = false

# Redis
redis_node_type = "cache.t3.small"
EOF
    
    log_success "Created terraform.tfvars"
    echo ""
fi

terraform init -upgrade
terraform validate

# Use common.auto.tfvars for shared settings
COMMON_VARS="-var-file=../../common.auto.tfvars"

# Check if this is first deploy (requires confirmation)
if ! terraform state list > /dev/null 2>&1 || [ -z "$(terraform state list 2>/dev/null)" ]; then
    log_warn "⚠️  First deployment to PRODUCTION"
    terraform plan $COMMON_VARS
    echo ""
    read -p "Apply changes? Type 'production' to confirm: " CONFIRM
    if [ "$CONFIRM" != "production" ]; then
        log_warn "Deployment cancelled"
        exit 0
    fi
    terraform apply $COMMON_VARS -auto-approve
else
    # Subsequent deploys - just apply
    terraform apply $COMMON_VARS -auto-approve
fi

# Get outputs
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query 'Account' --output text)
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
CLUSTER_NAME=$(terraform output -raw ecs_cluster_name 2>/dev/null || echo "${PROJECT_NAME}-${ENVIRONMENT}")
ALB_DNS=$(terraform output -raw alb_dns_name 2>/dev/null || echo "N/A")
FRONTEND_URL=$(terraform output -raw frontend_url 2>/dev/null || echo "N/A")

log_success "Infrastructure deployed!"

# ============================================================================
# Phase 2: Build & Push Docker Images
# ============================================================================
echo ""
log_info "━━━ Phase 2/3: Build & Push Images ━━━"

# Login to ECR
aws ecr get-login-password --region "$AWS_REGION" --profile "$AWS_PROFILE" | \
    docker login --username AWS --password-stdin "$ECR_REGISTRY"

cd "$PROJECT_ROOT"

for SERVICE in "${SERVICES[@]}"; do
    if [ -d "$PROJECT_ROOT/$SERVICE" ]; then
        log_info "Building $SERVICE..."
        
        ECR_REPO="${PROJECT_NAME}/${SERVICE}"
        FULL_IMAGE="${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
        
        docker build -t "$ECR_REPO:$IMAGE_TAG" "$PROJECT_ROOT/$SERVICE"
        docker tag "$ECR_REPO:$IMAGE_TAG" "$FULL_IMAGE"
        docker push "$FULL_IMAGE"
        
        log_success "$SERVICE pushed"
    else
        log_warn "$SERVICE directory not found, skipping..."
    fi
done

log_success "All images pushed to ECR!"

# ============================================================================
# Phase 3: Deploy to ECS
# ============================================================================
echo ""
log_info "━━━ Phase 3/3: Deploy to ECS ━━━"

for SERVICE in "${SERVICES[@]}"; do
    SERVICE_SHORT="${SERVICE%-service}"  # Remove -service suffix
    ECS_SERVICE="${SERVICE_SHORT}"
    
    log_info "Deploying $ECS_SERVICE..."
    
    # Check if service exists before updating
    if aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$ECS_SERVICE" --profile "$AWS_PROFILE" --region "$AWS_REGION" --query 'services[0].serviceName' --output text 2>/dev/null | grep -q "$ECS_SERVICE"; then
        aws ecs update-service \
            --cluster "$CLUSTER_NAME" \
            --service "$ECS_SERVICE" \
            --force-new-deployment \
            --profile "$AWS_PROFILE" \
            --region "$AWS_REGION" \
            --no-cli-pager > /dev/null
        log_success "$ECS_SERVICE deployment triggered"
    else
        log_warn "$ECS_SERVICE not found in ECS, skipping..."
    fi
done

# ============================================================================
# Done!
# ============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "Production Deployment Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🌐 Endpoints:"
echo "  API:      https://$ALB_DNS"
echo "  Frontend: $FRONTEND_URL"
echo ""
echo "📦 Cluster: $CLUSTER_NAME"
echo ""
echo "⏱️  ECS deployments may take 2-5 minutes to complete."
echo "   Check status: aws ecs describe-services --cluster $CLUSTER_NAME --services gateway"
echo ""
echo "💰 Estimated Cost: ~\$150/month"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
