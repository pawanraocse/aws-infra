#!/bin/zsh
# Script to deploy AWS Cognito resources using Terraform with the 'personal' AWS profile

export AWS_PROFILE=personal

echo "[INFO] Using AWS_PROFILE=$AWS_PROFILE"

terraform init
terraform plan
terraform apply -auto-approve

