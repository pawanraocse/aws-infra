output "queue_url" {
  description = "URL of the tenant provisioning SQS queue"
  value       = aws_sqs_queue.tenant_provisioning.url
}

output "queue_arn" {
  description = "ARN of the tenant provisioning SQS queue"
  value       = aws_sqs_queue.tenant_provisioning.arn
}

output "queue_name" {
  description = "Name of the tenant provisioning SQS queue"
  value       = aws_sqs_queue.tenant_provisioning.name
}

output "dlq_url" {
  description = "URL of the tenant provisioning DLQ"
  value       = aws_sqs_queue.tenant_provisioning_dlq.url
}

output "dlq_arn" {
  description = "ARN of the tenant provisioning DLQ"
  value       = aws_sqs_queue.tenant_provisioning_dlq.arn
}

# --- Tenant Deletion Outputs ---

output "sns_topic_arn" {
  description = "ARN of the tenant-deleted SNS topic"
  value       = aws_sns_topic.tenant_deleted.arn
}

output "cleanup_queue_url" {
  description = "URL of the tenant cleanup SQS queue"
  value       = aws_sqs_queue.tenant_cleanup.url
}

output "cleanup_queue_arn" {
  description = "ARN of the tenant cleanup SQS queue"
  value       = aws_sqs_queue.tenant_cleanup.arn
}

output "cleanup_dlq_arn" {
  description = "ARN of the tenant cleanup DLQ"
  value       = aws_sqs_queue.tenant_cleanup_dlq.arn
}
