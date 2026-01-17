#!/bin/bash
# =============================================================================
# Budget Start Script (JAR Rsync Mode - Sequential Startup)
# =============================================================================
# Builds and starts services on EC2 using local Docker builds.
# Services are started ONE BY ONE to reduce memory pressure on t3.small.
# Prerequisites: JARs + Dockerfiles rsynced to /app
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."

# Logging functions
log_info() { echo "[INFO] $1"; }
log_success() { echo "[SUCCESS] $1"; }
log_error() { echo "[ERROR] $1"; }
log_warn() { echo "[WARN] $1"; }

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Starting Budget Environment Services (Sequential Mode)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Configuration from environment or SSM
AWS_REGION="${AWS_REGION:-us-east-1}"
ENVIRONMENT="${ENVIRONMENT:-budget}"
PROJECT_NAME="${PROJECT_NAME:-saas-factory}"

# =============================================================================
# Optimize Swap Settings
# =============================================================================
log_info "Optimizing swap settings for t3.small..."
sudo sysctl -w vm.swappiness=10 2>/dev/null || true
sudo sysctl -w vm.vfs_cache_pressure=50 2>/dev/null || true

# =============================================================================
# Fetch Configuration from SSM
# =============================================================================
log_info "Fetching configuration from SSM Parameter Store..."

# Database
export DB_HOST=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/endpoint" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/database" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_USERNAME=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/username" --query 'Parameter.Value' --output text --region "$AWS_REGION")

# Get password from Secrets Manager
SECRET_ARN=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/rds/secret_arn" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id "$SECRET_ARN" --query 'SecretString' --output text --region "$AWS_REGION" | jq -r '.password')

# Redis
export REDIS_HOST=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/redis/endpoint" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export REDIS_PORT=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/redis/port" --query 'Parameter.Value' --output text --region "$AWS_REGION")

# Cognito
export COGNITO_USER_POOL_ID=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/user_pool_id" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export COGNITO_CLIENT_ID=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/client_id" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export COGNITO_CLIENT_SECRET=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/client_secret" --with-decryption --query 'Parameter.Value' --output text --region "$AWS_REGION" 2>/dev/null || echo "")
export COGNITO_SPA_CLIENT_ID=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/spa_client_id" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export COGNITO_DOMAIN=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/domain" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export COGNITO_ISSUER_URI="https://cognito-idp.${AWS_REGION}.amazonaws.com/${COGNITO_USER_POOL_ID}"
export COGNITO_JWKS_URI="${COGNITO_ISSUER_URI}/.well-known/jwks.json"
export COGNITO_REDIRECT_URI=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/callback_url" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export COGNITO_LOGOUT_REDIRECT_URL=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/cognito/logout_redirect_url" --query 'Parameter.Value' --output text --region "$AWS_REGION")

# CORS and Frontend
export FRONTEND_URL=$(aws ssm get-parameter --name "/${PROJECT_NAME}/${ENVIRONMENT}/frontend/url" --query 'Parameter.Value' --output text --region "$AWS_REGION")
export CORS_ALLOWED_ORIGINS="${FRONTEND_URL},http://localhost:4200"

log_success "Configuration loaded from SSM"
echo ""
echo "📦 Configuration:"
echo "  DB_HOST:    $DB_HOST"
echo "  DB_NAME:    $DB_NAME"
echo "  REDIS_HOST: $REDIS_HOST"
echo "  COGNITO:    $COGNITO_USER_POOL_ID"
echo "  CORS:       $CORS_ALLOWED_ORIGINS"
echo ""

# =============================================================================
# Database Initialization (OpenFGA)
# =============================================================================
log_info "Initializing Database..."

# Install PostgreSQL 15 client if not present
if ! command -v psql &> /dev/null; then
  log_info "Installing PostgreSQL 15 client..."
  sudo dnf install -y postgresql15
fi

# Create OpenFGA database if it doesn't exist
log_info "Checking for 'openfga' database..."
export PGPASSWORD="$DB_PASSWORD"

if psql -h "$DB_HOST" -U "$DB_USERNAME" -d "$DB_NAME" -tAc "SELECT 1 FROM pg_database WHERE datname='openfga'" | grep -q 1; then
  log_success "Database 'openfga' already exists."
else
  log_info "Database 'openfga' not found. Creating..."
  if psql -h "$DB_HOST" -U "$DB_USERNAME" -d "$DB_NAME" -c "CREATE DATABASE openfga;"; then
    log_success "Database 'openfga' created successfully!"
  else
    echo "⚠️ Failed to create database. Continuing anyway..."
  fi
fi

# =============================================================================
# Build Docker Images
# =============================================================================
log_info "Building Docker images (this takes ~5 min first time)..."
cd "$PROJECT_ROOT"

docker-compose -f docker-compose.budget.yml build --no-cache 2>&1 || {
    log_error "Failed to build Docker images"
    exit 1
}
log_success "Docker images built!"

# =============================================================================
# Helper: Wait for Service Health (Fast Polling)
# =============================================================================
wait_for_healthy() {
    local service_name=$1
    local max_wait=${2:-180}  # Default 3 min
    local interval=5          # Poll every 5 seconds (faster!)
    local elapsed=0
    
    while [ $elapsed -lt $max_wait ]; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "$service_name" 2>/dev/null || echo "not_found")
        
        if [ "$status" = "healthy" ]; then
            log_success "$service_name is healthy! (${elapsed}s)"
            return 0
        elif [ "$status" = "unhealthy" ]; then
            log_warn "$service_name is unhealthy, waiting..."
        fi
        
        sleep $interval
        elapsed=$((elapsed + interval))
    done
    
    log_warn "$service_name did not become healthy in ${max_wait}s (current: $status)"
    return 1
}

wait_for_container() {
    local service_name=$1
    local max_wait=${2:-30}
    local elapsed=0
    
    while [ $elapsed -lt $max_wait ]; do
        if docker ps --format '{{.Names}}' | grep -q "^${service_name}$"; then
            log_success "$service_name container started"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    
    log_error "$service_name container failed to start"
    return 1
}

# =============================================================================
# Parallel + Sequential Startup (Optimized)
# =============================================================================
log_info "Starting services with PARALLEL strategy where possible..."
echo ""

# Phase 1: Infrastructure (OpenFGA + OTEL in parallel)
log_info "Phase 1/3: Starting infrastructure services (parallel)..."
docker-compose -f docker-compose.budget.yml up -d openfga-migrate 2>&1
sleep 5
docker-compose -f docker-compose.budget.yml up -d openfga otel-collector 2>&1
wait_for_healthy "openfga" 120 || true
wait_for_container "otel-collector" 30 || true

# Phase 2: Service Discovery (Eureka - must be healthy before Java services)
log_info "Phase 2/3: Starting Eureka (required for service discovery)..."
docker-compose -f docker-compose.budget.yml up -d eureka-server 2>&1
wait_for_healthy "eureka-server" 300 || {
    log_error "Eureka failed to start. Cannot continue."
    docker logs eureka-server --tail 50
    exit 1
}

# Phase 3: All Java Services (in parallel - they all depend only on Eureka)
log_info "Phase 3/3: Starting all Java services (parallel)..."
docker-compose -f docker-compose.budget.yml up -d gateway-service auth-service backend-service platform-service 2>&1

# Wait for all services with fast polling
log_info "Waiting for services to become healthy (polling every 5s)..."
SERVICES_TO_CHECK=("gateway-service" "auth-service" "backend-service" "platform-service")
MAX_WAIT=300
elapsed=0

while [ $elapsed -lt $MAX_WAIT ]; do
    all_healthy=true
    status_line=""
    
    for svc in "${SERVICES_TO_CHECK[@]}"; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "starting")
        status_line="$status_line $svc:$status"
        
        if [ "$status" != "healthy" ]; then
            all_healthy=false
        fi
    done
    
    echo -ne "\r  [${elapsed}s]$status_line"
    
    if [ "$all_healthy" = true ]; then
        echo ""
        log_success "All services healthy!"
        break
    fi
    
    sleep 5
    elapsed=$((elapsed + 5))
done

if [ "$all_healthy" != true ]; then
    echo ""
    log_warn "Some services may still be starting. Check with: docker ps"
fi

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_success "Budget Environment Started!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🐳 Services:"
docker-compose -f docker-compose.budget.yml ps
echo ""
echo "🌐 Endpoints:"
echo "  Gateway:  http://localhost:8080"
echo "  Eureka:   http://localhost:8761"
echo ""
echo "💡 Tip: Services may still be warming up. Check health with:"
echo "  docker ps --format 'table {{.Names}}\t{{.Status}}'"
echo ""

