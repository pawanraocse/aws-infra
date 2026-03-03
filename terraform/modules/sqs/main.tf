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
