# ElastiCache Module - Outputs

# =============================================================================
# Connection Information
# =============================================================================

output "primary_endpoint" {
  description = "Primary endpoint for read/write operations"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "reader_endpoint" {
  description = "Reader endpoint for read operations (null if single node)"
  value       = var.num_cache_nodes > 1 ? aws_elasticache_replication_group.redis.reader_endpoint_address : null
}

output "port" {
  description = "Redis port"
  value       = 6379
}

# =============================================================================
# Connection String Helpers
# =============================================================================

output "spring_redis_host" {
  description = "Spring Boot Redis host configuration"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_url" {
  description = "Redis connection URL"
  value       = "redis://${aws_elasticache_replication_group.redis.primary_endpoint_address}:6379"
}

# =============================================================================
# Security
# =============================================================================

output "security_group_id" {
  description = "Security group ID for Redis"
  value       = aws_security_group.redis.id
}

# =============================================================================
# SSM Parameters
# =============================================================================

output "ssm_endpoint_path" {
  description = "SSM parameter path for Redis endpoint"
  value       = aws_ssm_parameter.redis_endpoint.name
}

output "ssm_port_path" {
  description = "SSM parameter path for Redis port"
  value       = aws_ssm_parameter.redis_port.name
}

# =============================================================================
# Resource Identifiers
# =============================================================================

output "replication_group_id" {
  description = "ElastiCache replication group ID"
  value       = aws_elasticache_replication_group.redis.id
}

output "replication_group_arn" {
  description = "ElastiCache replication group ARN"
  value       = aws_elasticache_replication_group.redis.arn
}
