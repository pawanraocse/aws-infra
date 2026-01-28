#!/bin/bash
# ============================================================================
# Full Budget Deployment - One Shot (JAR Rsync Mode)
# ============================================================================
# Deploys infrastructure AND application in one command
# Uses local Docker builds on EC2 - no ECR dependency
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
TERRAFORM_DIR="$PROJECT_ROOT/terraform/envs/budget"

AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-personal}"
SSH_KEY="${SSH_KEY:-}"
PROJECT_NAME="${PROJECT_NAME:-cloud-infra}"

# Load environment variables from .env if present
# Load environment variables from .env if present
if [ -f "$PROJECT_ROOT/.env" ]; then
    log_info "Loading environment variables from .env"
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
    
    # Override defaults with .env values
    PROJECT_NAME="${PROJECT_NAME:-$PROJECT_NAME}"
    AWS_REGION="${AWS_REGION:-$AWS_REGION}"
    AWS_PROFILE="${AWS_PROFILE:-$AWS_PROFILE}"
fi

# Ensure PROJECT_NAME is set
if [ -z "${PROJECT_NAME:-}" ]; then
    log_warn "PROJECT_NAME not set in .env. Defaulting to 'cloud-infra' (legacy behavior)."
    PROJECT_NAME="cloud-infra"
    # Alternatively, uncomment to enforce strict mode:
    # log_error "PROJECT_NAME is not set. Please check your .env file."
    # exit 1
fi

# Export for Terraform
export TF_VAR_project_name="$PROJECT_NAME"
export TF_VAR_aws_region="$AWS_REGION"
export TF_VAR_environment="${ENVIRONMENT:-dev}"

if [ -n "${GOOGLE_CLIENT_ID:-}" ]; then
    export TF_VAR_google_client_id="$GOOGLE_CLIENT_ID"
fi
if [ -n "${GOOGLE_CLIENT_SECRET:-}" ]; then
    export TF_VAR_google_client_secret="$GOOGLE_CLIENT_SECRET"
fi

log_info "Using Project Name: $PROJECT_NAME"

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
echo "🚀 Full Budget Deployment (JAR Rsync Mode)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Auto-detect SSH key if not set
if [ -z "$SSH_KEY" ]; then
    for key in ~/.ssh/pawankeys ~/.ssh/id_rsa ~/.ssh/id_ed25519 ~/.ssh/*.pem; do
        if [ -f "$key" ]; then
            SSH_KEY="$key"
            log_info "Auto-detected SSH key: $SSH_KEY"
            break
        fi
    done
fi

if [ -z "$SSH_KEY" ]; then
    log_warn "SSH_KEY not set and no key auto-detected."
    log_warn "Usage: SSH_KEY=~/.ssh/mykey.pem ./deploy.sh"
    log_info "Continuing without SSH key - will skip EC2 deployment steps"
fi

# Check for --rebuild flag
FORCE_REBUILD=false
for arg in "$@"; do
    if [ "$arg" == "--rebuild" ]; then
        FORCE_REBUILD=true
        log_info "Force rebuild enabled (--rebuild flag)"
    fi
done

# Always rebuild common-infra first to ensure library changes are picked up
log_info "Building common-infra (shared library)..."
cd "$PROJECT_ROOT"
(cd common-infra && mvn clean install -DskipTests -q) || { log_error "Failed to build common-infra"; exit 1; }
log_success "common-infra built and installed to local Maven repository"

# Check that JAR files exist (required for Docker build)
SERVICES_TO_BUILD=""
for svc in eureka-server gateway-service auth-service backend-service platform-service; do
    if [ "$FORCE_REBUILD" == "true" ]; then
        SERVICES_TO_BUILD="$SERVICES_TO_BUILD $svc"
    elif ! ls "$PROJECT_ROOT/$svc/target/"*.jar 1> /dev/null 2>&1; then
        SERVICES_TO_BUILD="$SERVICES_TO_BUILD $svc"
    fi
done

if [ -n "$SERVICES_TO_BUILD" ]; then
    if [ "$FORCE_REBUILD" == "true" ]; then
        log_info "Rebuilding all services (--rebuild flag)..."
    else
        log_warn "JAR files missing for:$SERVICES_TO_BUILD"
        log_info "Building JARs with Maven (this may take a few minutes)..."
    fi
    cd "$PROJECT_ROOT"
    for svc in $SERVICES_TO_BUILD; do
        log_info "Building $svc..."
        (cd "$svc" && mvn clean package -DskipTests -q) || { log_error "Failed to build $svc"; exit 1; }
    done
    log_success "All JARs built!"
else
    log_info "All JAR files exist. Use --rebuild to force rebuild."
fi

# Check AWS credentials
if ! aws sts get-caller-identity --profile "$AWS_PROFILE"; then
    log_error "AWS credentials not configured for profile: $AWS_PROFILE"
    exit 1
fi
log_success "AWS credentials verified"

# ============================================================================
# Phase 1: Infrastructure
# ============================================================================
echo ""
log_info "━━━ Phase 1: Infrastructure ━━━"

cd "$TERRAFORM_DIR"

# Initialize and validate
terraform init -upgrade
terraform validate

# Plan and apply (using common.auto.tfvars for shared settings)
COMMON_VARS="-var-file=../../common.auto.tfvars"
terraform plan $COMMON_VARS -out=tfplan
terraform apply tfplan

# Get outputs
EC2_IP=$(terraform output -raw ec2_public_ip 2>/dev/null || echo "")
FRONTEND_URL=$(terraform output -raw frontend_url 2>/dev/null || echo "https://localhost")

if [ -z "$EC2_IP" ]; then
    log_error "Failed to get EC2 IP from Terraform"
    exit 1
fi

log_success "Infrastructure deployed! EC2 IP: $EC2_IP"

# ============================================================================
# Phase 2: Wait for EC2 to be ready
# ============================================================================
echo ""
log_info "━━━ Phase 2: Waiting for EC2 ━━━"

if [ -n "$SSH_KEY" ]; then
    log_info "Waiting for EC2 to be reachable..."
    
    for i in {1..30}; do
        if ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -i "$SSH_KEY" ec2-user@"$EC2_IP" "echo ready" 2>/dev/null; then
            log_success "EC2 is reachable!"
            break
        fi
        echo -n "."
        sleep 10
    done
    echo ""
fi

# ============================================================================
# Phase 3: Deploy Application
# ============================================================================
echo ""
log_info "━━━ Phase 3: Deploy Application ━━━"

if [ -n "$SSH_KEY" ]; then
    log_info "Preparing EC2 environment..."
    
    # Install required packages including Docker
    ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" ec2-user@"$EC2_IP" << 'REMOTE_SETUP'
        set -e
        
        # Install basic tools
        sudo yum install -y rsync git jq
        
        # Install Docker CE
        sudo yum install -y docker
        sudo systemctl start docker
        sudo systemctl enable docker
        sudo usermod -aG docker ec2-user
        
        # Install docker-compose v2
        sudo mkdir -p /usr/local/lib/docker/cli-plugins
        sudo curl -SL "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-linux-$(uname -m)" -o /usr/local/lib/docker/cli-plugins/docker-compose
        sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
        
        # Also install standalone docker-compose
        sudo curl -SL "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-linux-$(uname -m)" -o /usr/local/bin/docker-compose
        sudo chmod +x /usr/local/bin/docker-compose
        
        sudo mkdir -p /app && sudo chown ec2-user:ec2-user /app
        
        echo "Docker installed successfully"
REMOTE_SETUP
    
    log_success "EC2 environment ready"
    log_info "Copying application to EC2 (including JARs for local build)..."
    
    # Copy essential files including target/*.jar for Docker builds
    rsync -avz --progress -e "ssh -o StrictHostKeyChecking=no -i $SSH_KEY" \
        --exclude '.terraform' \
        --exclude 'node_modules' \
        --exclude '.git' \
        --exclude '.idea' \
        --exclude 'frontend' \
        --exclude '*.class' \
        --exclude 'target/classes' \
        --exclude 'target/test-classes' \
        --exclude 'target/generated-sources' \
        --exclude 'target/maven-status' \
        --include 'target/*.jar' \
        "$PROJECT_ROOT/" ec2-user@"$EC2_IP":/app/
    
    log_success "Application copied to EC2"
    
    # Start services
    log_info "Starting services (building Docker images on EC2)..."
    ssh -o StrictHostKeyChecking=no -i "$SSH_KEY" ec2-user@"$EC2_IP" "cd /app && chmod +x scripts/budget/*.sh && ./scripts/budget/start.sh"
    
    log_success "Services started!"
else
    log_warn "SSH_KEY not set - skipping EC2 deployment"
    log_info "To complete deployment, run manually:"
    echo "  scp -i <key.pem> -r . ec2-user@$EC2_IP:/app/"
    echo "  ssh -i <key.pem> ec2-user@$EC2_IP 'cd /app && ./scripts/budget/start.sh'"
fi

# ============================================================================
# Phase 4: Trigger Amplify Frontend Build
# ============================================================================
echo ""
log_info "━━━ Phase 4: Trigger Frontend Build ━━━"

cd "$TERRAFORM_DIR"
AMPLIFY_APP_ID=$(terraform output -raw frontend_url 2>/dev/null | sed 's/.*main\.\([^\.]*\)\.amplifyapp.*/\1/' || echo "")

if [ -n "$AMPLIFY_APP_ID" ] && [ "$AMPLIFY_APP_ID" != "" ]; then
    log_info "Triggering Amplify build for app: $AMPLIFY_APP_ID"
    
    if aws amplify start-job --app-id "$AMPLIFY_APP_ID" --branch-name main --job-type RELEASE --region "$AWS_REGION" 2>/dev/null; then
        log_success "Amplify build triggered! Frontend will be ready in ~3-5 minutes."
    else
        log_warn "Failed to trigger Amplify build. You may need to trigger manually:"
        echo "  aws amplify start-job --app-id $AMPLIFY_APP_ID --branch-name main --job-type RELEASE"
    fi
else
    log_warn "Could not find Amplify App ID. Check if frontend_url output exists."
fi

# ============================================================================
# Done!
# ============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "Budget Deployment Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🌐 Access:"
echo "  Frontend: $FRONTEND_URL (building...)"
echo "  API:      http://$EC2_IP:8080"
echo ""
echo "💡 Instance Type: t3.small (2GB RAM)"
echo "💰 Estimated Cost: ~\$30-40/month"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

