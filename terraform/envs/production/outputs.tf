# Production Environment - Outputs

# =============================================================================
# VPC
# =============================================================================

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

# =============================================================================
# Database
# =============================================================================

output "rds_endpoint" {
  description = "RDS endpoint"
  value       = module.rds.endpoint
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint"
  value       = module.elasticache.primary_endpoint
}

# =============================================================================
# ECR Repositories
# =============================================================================

output "ecr_repository_urls" {
  description = "ECR repository URLs"
  value       = module.ecr.repository_urls
}

# =============================================================================
# ECS
# =============================================================================

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs_cluster.cluster_name
}

# =============================================================================
# Load Balancer
# =============================================================================

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = module.alb.alb_dns_name
}

output "alb_zone_id" {
  description = "ALB Zone ID (for Route53)"
  value       = module.alb.alb_zone_id
}

output "api_url" {
  description = "API URL"
  value       = "https://${module.alb.alb_dns_name}"
}

# =============================================================================
# Frontend
# =============================================================================

output "frontend_url" {
  description = "Frontend URL (Amplify)"
  value       = module.amplify.branch_url
}

# =============================================================================
# Bastion
# =============================================================================

output "bastion_public_ip" {
  description = "Bastion public IP"
  value       = var.create_bastion ? module.bastion[0].public_ip : null
}

# =============================================================================
# Summary
# =============================================================================

output "deployment_info" {
  description = "Deployment summary"
  value       = <<-EOT
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    ✅ Production Deployment Complete!
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    🌐 Endpoints:
      API:      https://${module.alb.alb_dns_name}
      Frontend: ${module.amplify.branch_url}
    
    📦 Services:
      ECS Cluster: ${module.ecs_cluster.cluster_name}
    
    🗃️ Data Stores:
      RDS:   ${module.rds.endpoint}
      Redis: ${module.elasticache.primary_endpoint}
    
    💰 Estimated Cost: ~$150/month
    
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EOT
}
