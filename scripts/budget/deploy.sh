#!/bin/bash
# ============================================================================
# Full Budget Deployment - One Shot
# ============================================================================
# Deploys infrastructure AND application in one command
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
TERRAFORM_DIR="$PROJECT_ROOT/terraform/envs/budget"

AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_PROFILE="${AWS_PROFILE:-personal}"
SSH_KEY="${SSH_KEY:-}"

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
echo "🚀 Full Budget Deployment (Infrastructure + Application)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check SSH key
if [ -z "$SSH_KEY" ]; then
    log_warn "SSH_KEY not set. Usage: SSH_KEY=~/.ssh/mykey.pem ./deploy-budget-full.sh"
    log_info "Continuing without SSH key - will skip EC2 deployment steps"
fi

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
log_info "━━━ Phase 1: Infrastructure ━━━"

cd "$TERRAFORM_DIR"

# Check terraform.tfvars - create interactively if missing
if [ ! -f "terraform.tfvars" ]; then
    log_warn "terraform.tfvars not found. Let's create it!"
    echo ""
    
    # Prompt for values
    read -p "Project name [saas-factory]: " PROJECT_NAME
    PROJECT_NAME="${PROJECT_NAME:-saas-factory}"
    
    read -p "AWS Region [us-east-1]: " INPUT_REGION
    INPUT_REGION="${INPUT_REGION:-us-east-1}"
    
    echo ""
    echo "GitHub repository URL for frontend:"
    read -p "  (e.g., https://github.com/your-org/your-repo): " FRONTEND_REPO
    
    echo ""
    echo "GitHub Personal Access Token (for Amplify):"
    echo "  Get from: GitHub → Settings → Developer → Personal Access Tokens"
    read -sp "  Token (hidden): " GITHUB_TOKEN
    echo ""
    
    echo ""
    echo "Your public IP for SSH access (run 'curl ifconfig.me'):"
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
project_name = "$PROJECT_NAME"
environment  = "budget"
aws_region   = "$INPUT_REGION"

frontend_repository_url = "$FRONTEND_REPO"
github_access_token     = "$GITHUB_TOKEN"
frontend_branch         = "main"
frontend_app_root       = "frontend"

create_bastion            = true
bastion_allowed_ssh_cidrs = ["${MY_IP}/32"]
bastion_ssh_public_key    = "$SSH_PUBLIC_KEY"
EOF
    
    log_success "Created terraform.tfvars"
    echo ""
fi

terraform init -upgrade
terraform validate
terraform apply -auto-approve

# Get outputs
EC2_IP=$(terraform output -raw ec2_public_ip 2>/dev/null || echo "")
FRONTEND_URL=$(terraform output -raw frontend_url 2>/dev/null || echo "")

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
    log_info "Copying application to EC2..."
    
    # Create /app directory and copy files
    ssh -i "$SSH_KEY" ec2-user@"$EC2_IP" "sudo mkdir -p /app && sudo chown ec2-user:ec2-user /app"
    
    # Copy essential files
    rsync -avz --progress -e "ssh -i $SSH_KEY" \
        --exclude '.terraform' \
        --exclude 'node_modules' \
        --exclude '.git' \
        --exclude 'target' \
        --exclude '.idea' \
        "$PROJECT_ROOT/" ec2-user@"$EC2_IP":/app/
    
    log_success "Application copied to EC2"
    
    # Start services
    log_info "Starting services..."
    ssh -i "$SSH_KEY" ec2-user@"$EC2_IP" "cd /app && chmod +x scripts/*.sh && ./scripts/start-budget.sh"
    
    log_success "Services started!"
else
    log_warn "SSH_KEY not set - skipping EC2 deployment"
    log_info "To complete deployment, run manually:"
    echo "  scp -i <key.pem> -r . ec2-user@$EC2_IP:/app/"
    echo "  ssh -i <key.pem> ec2-user@$EC2_IP 'cd /app && ./scripts/start-budget.sh'"
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
echo "  Frontend: $FRONTEND_URL"
echo "  API:      http://$EC2_IP:8080"
echo ""
echo "💰 Estimated Cost: ~\$15-30/month"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
