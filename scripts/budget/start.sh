#!/bin/bash
# =============================================================================
# Budget Environment Entrypoint Script
# =============================================================================
# Run on EC2 to fetch SSM parameters and start Docker Compose
# 
# Usage:
#   ./start-budget.sh
# =============================================================================

set -euo pipefail

# Configuration
PROJECT_NAME="${PROJECT_NAME:-saas-factory}"
ENVIRONMENT="${ENVIRONMENT:-budget}"
AWS_REGION="${AWS_REGION:-us-east-1}"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Starting Budget Environment Services"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# =============================================================================
# Fetch SSM Parameters
# =============================================================================
log_info "Fetching configuration from SSM Parameter Store..."

# RDS Configuration
export DB_HOST=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/endpoint" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_PORT=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/port" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_NAME=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/database" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_USERNAME=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/username" --query 'Parameter.Value' --output text --region "$AWS_REGION")

# Get DB Password from Secrets Manager via SSM reference
RDS_SECRET_ARN=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/secret_arn" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id "$RDS_SECRET_ARN" --query 'SecretString' --output text --region "$AWS_REGION" | jq -r '.password')

# Redis Configuration
export REDIS_HOST=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/redis/endpoint" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export REDIS_PORT=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/redis/port" --query 'Parameter.Value' --output text --region "$AWS_REGION")

log_success "Configuration loaded from SSM"
echo ""
echo "📦 Configuration:"
echo "  DB_HOST:    $DB_HOST"
echo "  DB_NAME:    $DB_NAME"
echo "  REDIS_HOST: $REDIS_HOST"
echo ""

# =============================================================================
# Start Docker Compose
# =============================================================================
log_info "Starting services with Docker Compose..."

cd "$(dirname "$0")"

docker-compose -f docker-compose.budget.yml up -d

echo ""
log_success "Services started!"
echo ""
echo "📊 Check status: docker-compose -f docker-compose.budget.yml ps"
echo "📋 View logs:    docker-compose -f docker-compose.budget.yml logs -f"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
