#!/bin/bash
# =====================================================
# API Key Authentication Test Script
# =====================================================
# Usage: ./test-api-key.sh [API_KEY]
# 
# If no API_KEY argument provided, reads from:
#   1. Environment variable: API_KEY
#   2. File: .api-key (in current directory)
# =====================================================

set -e

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

# Get API key from argument, env var, or file
if [ -n "$1" ]; then
    API_KEY="$1"
elif [ -n "$API_KEY" ]; then
    echo -e "${YELLOW}Using API key from environment variable${NC}"
elif [ -f ".api-key" ]; then
    API_KEY=$(cat .api-key | tr -d '\n')
    echo -e "${YELLOW}Using API key from .api-key file${NC}"
else
    echo -e "${RED}Error: No API key provided${NC}"
    echo "Usage: $0 <API_KEY>"
    echo "  or set API_KEY environment variable"
    echo "  or create .api-key file"
    exit 1
fi

# Mask key for display
KEY_PREFIX="${API_KEY:0:20}..."

echo ""
echo "============================================="
echo "API Key Authentication Test"
echo "============================================="
echo "Gateway: $GATEWAY_URL"
echo "Key: $KEY_PREFIX"
echo "============================================="
echo ""

test_endpoint() {
    local name="$1"
    local endpoint="$2"
    local method="${3:-GET}"
    
    echo -n "Testing $name... "
    
    response=$(curl -s -w "\n%{http_code}" -X "$method" \
        "$GATEWAY_URL$endpoint" \
        -H "X-API-Key: $API_KEY" \
        -H "Content-Type: application/json")
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✓ $http_code${NC}"
        echo "$body" | jq -r '.' 2>/dev/null | head -5 || echo "$body" | head -5
    elif [ "$http_code" -eq 401 ]; then
        echo -e "${RED}✗ 401 Unauthorized${NC}"
    elif [ "$http_code" -eq 403 ]; then
        echo -e "${YELLOW}⚠ 403 Forbidden (missing permission)${NC}"
    elif [ "$http_code" -eq 404 ]; then
        echo -e "${YELLOW}⚠ 404 Not Found${NC}"
    else
        echo -e "${RED}✗ $http_code${NC}"
        echo "$body" | head -3
    fi
    echo ""
}

# =====================================================
# Test Endpoints
# =====================================================

echo "--- Platform Service ---"
test_endpoint "Billing Status" "/platform-service/platform/api/v1/billing/status"
test_endpoint "List API Keys" "/platform-service/platform/api/v1/api-keys"
test_endpoint "Organization Info" "/platform-service/platform/api/v1/organizations"

echo "--- Auth Service ---"
test_endpoint "Current User" "/auth/api/v1/users/me"
test_endpoint "List Roles" "/auth/api/v1/roles"

echo "============================================="
echo "Test complete!"
echo "============================================="
