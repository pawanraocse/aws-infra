output "lambda_arn" {
  description = "ARN of the PreTokenGeneration Lambda function"
  value       = aws_lambda_function.pre_token_generation.arn
}

output "lambda_function_name" {
  description = "Name of the PreTokenGeneration Lambda function"
  value       = aws_lambda_function.pre_token_generation.function_name
}

output "lambda_invoke_arn" {
  description = "Invoke ARN of the PreTokenGeneration Lambda function"
  value       = aws_lambda_function.pre_token_generation.invoke_arn
}

output "security_group_id" {
  description = "Lambda security group ID (null if not in VPC mode)"
  value       = var.enable_vpc_mode ? aws_security_group.lambda[0].id : null
}
