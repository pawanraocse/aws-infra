# SQS Module
# Tenant provisioning queue with dead-letter queue
# Used for async Organization tenant provisioning

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "sqs"
  }

  queue_name     = "${var.project_name}-${var.environment}-tenant-provisioning"
  dlq_queue_name = "${var.project_name}-${var.environment}-tenant-provisioning-dlq"

  # Tenant deletion / cleanup
  sns_topic_name     = "${var.project_name}-${var.environment}-tenant-deleted"
  cleanup_queue_name = "${var.project_name}-${var.environment}-tenant-cleanup"
  cleanup_dlq_name   = "${var.project_name}-${var.environment}-tenant-cleanup-dlq"
}

# =============================================================================
# Dead Letter Queue (must be created first)
# =============================================================================

resource "aws_sqs_queue" "tenant_provisioning_dlq" {
  name                      = local.dlq_queue_name
  message_retention_seconds = var.dlq_retention_seconds # 14 days default

  tags = merge(local.common_tags, {
    Name = local.dlq_queue_name
  })
}

# =============================================================================
# Main Provisioning Queue
# =============================================================================

resource "aws_sqs_queue" "tenant_provisioning" {
  name                       = local.queue_name
  visibility_timeout_seconds = var.visibility_timeout_seconds # 120s default
  message_retention_seconds  = var.retention_seconds          # 24h default
  receive_wait_time_seconds  = var.receive_wait_time_seconds  # Long polling

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.tenant_provisioning_dlq.arn
    maxReceiveCount     = var.max_receive_count # 3 retries default
  })

  tags = merge(local.common_tags, {
    Name = local.queue_name
  })
}

# =============================================================================
# SSM Parameters for Service Discovery
# =============================================================================

resource "aws_ssm_parameter" "sqs_queue_url" {
  name        = "/${var.project_name}/${var.environment}/sqs/tenant-provisioning/url"
  description = "Tenant provisioning SQS queue URL"
  type        = "String"
  value       = aws_sqs_queue.tenant_provisioning.url

  tags = local.common_tags
}

resource "aws_ssm_parameter" "sqs_queue_arn" {
  name        = "/${var.project_name}/${var.environment}/sqs/tenant-provisioning/arn"
  description = "Tenant provisioning SQS queue ARN"
  type        = "String"
  value       = aws_sqs_queue.tenant_provisioning.arn

  tags = local.common_tags
}

resource "aws_ssm_parameter" "sqs_dlq_url" {
  name        = "/${var.project_name}/${var.environment}/sqs/tenant-provisioning-dlq/url"
  description = "Tenant provisioning DLQ URL"
  type        = "String"
  value       = aws_sqs_queue.tenant_provisioning_dlq.url

  tags = local.common_tags
}

# =============================================================================
# Tenant Deletion — SNS Topic (Fanout)
# =============================================================================


resource "aws_sns_topic" "tenant_deleted" {
  name = local.sns_topic_name

  tags = merge(local.common_tags, {
    Name = local.sns_topic_name
  })
}

# =============================================================================
# Tenant Cleanup Queue (subscribed to SNS topic)
# =============================================================================

resource "aws_sqs_queue" "tenant_cleanup_dlq" {
  name                      = local.cleanup_dlq_name
  message_retention_seconds = var.dlq_retention_seconds

  tags = merge(local.common_tags, {
    Name = local.cleanup_dlq_name
  })
}

resource "aws_sqs_queue" "tenant_cleanup" {
  name                       = local.cleanup_queue_name
  visibility_timeout_seconds = var.cleanup_visibility_timeout_seconds
  message_retention_seconds  = var.retention_seconds
  receive_wait_time_seconds  = var.receive_wait_time_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.tenant_cleanup_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })

  tags = merge(local.common_tags, {
    Name = local.cleanup_queue_name
  })
}

# Allow SNS to send messages to the cleanup SQS queue
resource "aws_sqs_queue_policy" "cleanup_sns_policy" {
  queue_url = aws_sqs_queue.tenant_cleanup.url

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "sns.amazonaws.com" }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.tenant_cleanup.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_sns_topic.tenant_deleted.arn
          }
        }
      }
    ]
  })
}

# Subscribe cleanup queue to SNS topic
resource "aws_sns_topic_subscription" "cleanup_subscription" {
  topic_arn            = aws_sns_topic.tenant_deleted.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.tenant_cleanup.arn
  raw_message_delivery = true
}

# =============================================================================
# SSM Parameters — Tenant Deletion
# =============================================================================

resource "aws_ssm_parameter" "sns_topic_arn" {
  name        = "/${var.project_name}/${var.environment}/sns/tenant-deleted/arn"
  description = "Tenant deleted SNS topic ARN"
  type        = "String"
  value       = aws_sns_topic.tenant_deleted.arn

  tags = local.common_tags
}

resource "aws_ssm_parameter" "cleanup_queue_url" {
  name        = "/${var.project_name}/${var.environment}/sqs/tenant-cleanup/url"
  description = "Tenant cleanup SQS queue URL"
  type        = "String"
  value       = aws_sqs_queue.tenant_cleanup.url

  tags = local.common_tags
}
