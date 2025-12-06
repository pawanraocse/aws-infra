#!/bin/bash
set -e

# Configuration
REGION="us-east-1"  # Default, can be overridden by env var
# Project name and environment should match your terraform variables
PROJECT_NAME="cloud-infra" 
ENVIRONMENT="dev"

echo "================================================================"
echo "   AWS SaaS Template - System Admin Bootstrap"
echo "================================================================"
echo "This script creates a 'Super Admin' user in your Cognito User Pool."
echo "This user will have access to the Platform Management features."
echo ""

# 1. Detect User Pool ID
USER_POOL_ID=$(aws ssm get-parameter --name "/$PROJECT_NAME/$ENVIRONMENT/cognito/user_pool_id" --query "Parameter.Value" --output text 2>/dev/null || echo "")

if [ -z "$USER_POOL_ID" ]; then
    echo "❌ Error: Could not find User Pool ID in SSM Parameter Store."
    echo "   Expected path: /$PROJECT_NAME/$ENVIRONMENT/cognito/user_pool_id"
    read -p "   Please enter User Pool ID manually: " USER_POOL_ID
    if [ -z "$USER_POOL_ID" ]; then echo "Exiting."; exit 1; fi
fi

echo "✅ Target User Pool: $USER_POOL_ID"
echo ""

# 2. Prompt for Credentials
read -p "Enter Admin Email: " ADMIN_EMAIL
read -s -p "Enter Admin Password (min 12 chars, numbers, symbols): " ADMIN_PASSWORD
echo ""

# 3. Create User
echo "creating user..."
aws cognito-idp admin-create-user \
    --user-pool-id "$USER_POOL_ID" \
    --username "$ADMIN_EMAIL" \
    --user-attributes Name=email,Value="$ADMIN_EMAIL" Name=email_verified,Value=true \
    --message-action SUPPRESS \
    --region "$REGION"

# 4. Set Password (Permanent)
echo "setting password..."
aws cognito-idp admin-set-user-password \
    --user-pool-id "$USER_POOL_ID" \
    --username "$ADMIN_EMAIL" \
    --password "$ADMIN_PASSWORD" \
    --permanent \
    --region "$REGION"

# 5. Set System Admin Attributes
# custom:role = super-admin (Grants *:* permission)
# custom:tenantId = system (Indicates platform level access)
# custom:tenantType = PLATFORM
echo "assigning super-admin role..."
aws cognito-idp admin-update-user-attributes \
    --user-pool-id "$USER_POOL_ID" \
    --username "$ADMIN_EMAIL" \
    --user-attributes \
        Name="custom:role",Value="super-admin" \
        Name="custom:tenantId",Value="system" \
    --region "$REGION"

# 6. Add to Admin Group (Optional but recommended)
echo "adding to 'admin' group..."
aws cognito-idp admin-add-user-to-group \
    --user-pool-id "$USER_POOL_ID" \
    --username "$ADMIN_EMAIL" \
    --group-name "admin" \
    --region "$REGION"

echo ""
echo "================================================================"
echo "✅ System Admin Created Successfully!"
echo "   Email: $ADMIN_EMAIL"
echo "   Role:  super-admin"
echo "================================================================"
