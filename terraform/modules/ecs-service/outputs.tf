# ECS Service Module - Outputs

output "service_id" {
  description = "ECS Service ID"
  value       = aws_ecs_service.service.id
}

output "service_name" {
  description = "ECS Service name"
  value       = aws_ecs_service.service.name
}

output "service_arn" {
  description = "ECS Service ARN"
  value       = aws_ecs_service.service.cluster
}

output "task_definition_arn" {
  description = "Task definition ARN"
  value       = aws_ecs_task_definition.service.arn
}

output "task_definition_family" {
  description = "Task definition family"
  value       = aws_ecs_task_definition.service.family
}

output "security_group_id" {
  description = "Service security group ID"
  value       = aws_security_group.service.id
}

output "task_execution_role_arn" {
  description = "Task execution role ARN"
  value       = aws_iam_role.task_execution.arn
}

output "task_role_arn" {
  description = "Task role ARN"
  value       = aws_iam_role.task.arn
}

output "log_group_name" {
  description = "CloudWatch log group name"
  value       = aws_cloudwatch_log_group.service.name
}
