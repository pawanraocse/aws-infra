#!/bin/sh
set -e

echo "========================================="
echo "Payment Service - Starting Entrypoint"
echo "========================================="

# Unset AWS_PROFILE to use environment credentials
unset AWS_PROFILE

export AWS_REGION=${AWS_REGION:-us-east-1}

echo "Starting Payment Service..."
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar
