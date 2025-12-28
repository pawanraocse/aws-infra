# =============================================================================
# SSM Parameters Module - Outputs
# =============================================================================

output "user_pool_id_parameter_arn" {
  description = "ARN of user_pool_id SSM parameter"
  value       = aws_ssm_parameter.user_pool_id.arn
}

output "client_id_parameter_arn" {
  description = "ARN of client_id SSM parameter"
  value       = aws_ssm_parameter.client_id.arn
}

output "client_secret_parameter_arn" {
  description = "ARN of client_secret SSM parameter"
  value       = aws_ssm_parameter.client_secret.arn
}

output "base_path" {
  description = "Base SSM path for this project/environment"
  value       = local.base_path
}

output "cognito_path" {
  description = "SSM path for Cognito parameters"
  value       = "${local.base_path}/cognito"
}
