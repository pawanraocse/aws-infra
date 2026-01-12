#!/bin/bash
# ============================================================================
# Destroy Production Environment
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../terraform/envs/production"

AWS_PROFILE="${AWS_PROFILE:-production}"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️  DESTROY Production Environment"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "This will PERMANENTLY DELETE all production resources!"
echo ""

cd "$TERRAFORM_DIR"

terraform plan -destroy
echo ""
read -p "Type 'destroy-production' to confirm: " CONFIRM

if [ "$CONFIRM" != "destroy-production" ]; then
    echo "Cancelled"
    exit 0
fi

terraform destroy -auto-approve

echo ""
echo "✅ Production environment destroyed. AWS charges stopped."
