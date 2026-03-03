#!/bin/bash
# =============================================================================
# LocalStack SQS Initialization Script
# =============================================================================
# Auto-creates SQS queues on LocalStack container startup.
# Mounted at /etc/localstack/init/ready.d/ via docker-compose volume.
# =============================================================================

echo "Creating SQS queues..."

# Dead Letter Queue (must be created first)
awslocal sqs create-queue \
  --queue-name tenant-provisioning-dlq \
  --attributes '{"MessageRetentionPeriod":"1209600"}'

# Main provisioning queue with DLQ redrive policy
DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/tenant-provisioning-dlq \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)

awslocal sqs create-queue \
  --queue-name tenant-provisioning \
  --attributes "{\"VisibilityTimeout\":\"120\",\"MessageRetentionPeriod\":\"86400\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

echo "SQS queues created successfully!"
awslocal sqs list-queues
