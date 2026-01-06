# ECS Cluster Module - Outputs

output "cluster_id" {
  description = "ECS Cluster ID"
  value       = aws_ecs_cluster.main.id
}

output "cluster_arn" {
  description = "ECS Cluster ARN"
  value       = aws_ecs_cluster.main.arn
}

output "cluster_name" {
  description = "ECS Cluster name"
  value       = aws_ecs_cluster.main.name
}

output "log_group_name" {
  description = "CloudWatch log group for cluster"
  value       = aws_cloudwatch_log_group.cluster.name
}

output "service_discovery_namespace_id" {
  description = "Service discovery namespace ID"
  value       = var.enable_service_discovery ? aws_service_discovery_private_dns_namespace.main[0].id : null
}

output "service_discovery_namespace_arn" {
  description = "Service discovery namespace ARN"
  value       = var.enable_service_discovery ? aws_service_discovery_private_dns_namespace.main[0].arn : null
}
