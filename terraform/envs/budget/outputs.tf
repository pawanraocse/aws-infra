# Budget Environment - Outputs

# =============================================================================
# VPC Outputs
# =============================================================================

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.vpc.public_subnet_ids
}

# =============================================================================
# Database Outputs
# =============================================================================

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = module.rds.endpoint
}

output "rds_database_name" {
  description = "RDS database name"
  value       = module.rds.database_name
}

output "rds_secret_arn" {
  description = "Secrets Manager ARN for RDS credentials"
  value       = module.rds.secret_arn
  sensitive   = true
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint"
  value       = module.elasticache.primary_endpoint
}

output "cloudfront_domain" {
  description = "CloudFront Domain (HTTPS API)"
  value       = module.cloudfront.domain_name
}

# =============================================================================
# ECR Outputs
# =============================================================================

output "ecr_registry" {
  description = "ECR Registry URL"
  value       = split("/", module.ecr.repository_urls["gateway-service"])[0]
}

output "ecr_repositories" {
  description = "ECR Repository URLs"
  value       = module.ecr.repository_urls
}


# =============================================================================
# EC2 Outputs
# =============================================================================

output "ec2_instance_id" {
  description = "EC2 instance ID"
  value       = module.bastion.instance_id
}

output "ec2_public_ip" {
  description = "EC2 public IP"
  value       = module.bastion.public_ip
}

output "ssh_command" {
  description = "SSH command to connect"
  value       = module.bastion.ssh_command
}

output "ssm_command" {
  description = "SSM Session Manager command"
  value       = module.bastion.ssm_command
}

# =============================================================================
# Amplify Outputs
# =============================================================================

output "frontend_url" {
  description = "Frontend URL (Amplify)"
  value       = module.amplify.branch_url
}

# =============================================================================
# Cognito Outputs
# =============================================================================

output "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  value       = module.cognito_user_pool.user_pool_id
}

output "cognito_client_id" {
  description = "Cognito App Client ID (with secret)"
  value       = module.cognito_user_pool.native_client_id
}

output "cognito_spa_client_id" {
  description = "Cognito SPA Client ID (no secret)"
  value       = module.cognito_user_pool.spa_client_id
}

output "cognito_hosted_ui_url" {
  description = "Cognito Hosted UI URL"
  value       = module.cognito_user_pool.hosted_ui_url
}

output "cognito_domain" {
  description = "Cognito domain"
  value       = module.cognito_user_pool.domain
}

# =============================================================================
# Quick Start
# =============================================================================

output "next_steps" {
  description = "Next steps after deployment"
  value       = <<-EOT
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    ✅ Budget Deployment Complete!
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    📦 Infrastructure:
      RDS:   ${module.rds.endpoint}
      Redis: ${module.elasticache.primary_endpoint}
      EC2:   ${module.bastion.public_ip}
    
    🚀 Deploy Services:
      1. SSH: ${module.bastion.ssh_command}
      2. cd /app && docker-compose -f docker-compose.budget.yml up -d
    
    🌐 Access:
      Frontend: ${module.amplify.branch_url}
      API:      https://${module.cloudfront.domain_name}
    
    💰 Cost: ~$15-30/month
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EOT
}
