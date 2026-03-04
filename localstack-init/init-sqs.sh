#!/bin/bash
# =============================================================================
# LocalStack SQS/SNS Initialization Script
# =============================================================================
# Auto-creates SQS queues and SNS topics on LocalStack container startup.
# Mounted at /etc/localstack/init/ready.d/ via docker-compose volume.
# =============================================================================

echo "Creating SQS queues and SNS topics..."

# =============================================================================
# Tenant Provisioning (Phase 9.1.2)
# =============================================================================

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

# =============================================================================
# Tenant Cleanup / Deletion (Phase 9.1.3)
# =============================================================================

# SNS topic for tenant deletion fanout
awslocal sns create-topic --name tenant-deleted

TOPIC_ARN=$(awslocal sns list-topics \
  --query 'Topics[?ends_with(TopicArn, `:tenant-deleted`)].TopicArn' \
  --output text)

# Cleanup DLQ
awslocal sqs create-queue \
  --queue-name tenant-cleanup-dlq \
  --attributes '{"MessageRetentionPeriod":"1209600"}'

CLEANUP_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/tenant-cleanup-dlq \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)

# Cleanup queue with DLQ redrive
awslocal sqs create-queue \
  --queue-name tenant-cleanup \
  --attributes "{\"VisibilityTimeout\":\"120\",\"MessageRetentionPeriod\":\"86400\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${CLEANUP_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

CLEANUP_QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/tenant-cleanup \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)

# Subscribe cleanup queue to SNS topic (raw message delivery)
awslocal sns subscribe \
  --topic-arn "$TOPIC_ARN" \
  --protocol sqs \
  --notification-endpoint "$CLEANUP_QUEUE_ARN" \
  --attributes '{"RawMessageDelivery":"true"}'

echo "SQS queues and SNS topics created successfully!"
awslocal sqs list-queues
awslocal sns list-topics
awslocal sns list-subscriptions
