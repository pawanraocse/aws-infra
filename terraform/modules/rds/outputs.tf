# RDS Module - Outputs
# All values exported for use by other modules

# =============================================================================
# Connection Information
# =============================================================================

output "endpoint" {
  description = "Database endpoint (hostname)"
  value       = local.db_endpoint
}

output "port" {
  description = "Database port"
  value       = 5432
}

output "database_name" {
  description = "Name of the default database"
  value       = var.database_name
}

output "master_username" {
  description = "Master username"
  value       = var.master_username
}

# =============================================================================
# Connection String Helpers
# =============================================================================

output "connection_string" {
  description = "JDBC connection string (without credentials)"
  value       = "jdbc:postgresql://${local.db_endpoint}:5432/${var.database_name}"
}

output "spring_datasource_url" {
  description = "Spring Boot datasource URL"
  value       = "jdbc:postgresql://${local.db_endpoint}:5432/${var.database_name}"
}

# =============================================================================
# Secrets Manager
# =============================================================================

output "secret_arn" {
  description = "ARN of the Secrets Manager secret containing DB credentials"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "secret_name" {
  description = "Name of the Secrets Manager secret"
  value       = aws_secretsmanager_secret.db_credentials.name
}

# =============================================================================
# SSM Parameters
# =============================================================================

output "ssm_endpoint_path" {
  description = "SSM parameter path for database endpoint"
  value       = aws_ssm_parameter.db_endpoint.name
}

output "ssm_port_path" {
  description = "SSM parameter path for database port"
  value       = aws_ssm_parameter.db_port.name
}

output "ssm_database_path" {
  description = "SSM parameter path for database name"
  value       = aws_ssm_parameter.db_name.name
}

output "ssm_username_path" {
  description = "SSM parameter path for database username"
  value       = aws_ssm_parameter.db_username.name
}

output "ssm_secret_arn_path" {
  description = "SSM parameter path for Secrets Manager ARN"
  value       = aws_ssm_parameter.db_secret_arn.name
}

# =============================================================================
# Security
# =============================================================================

output "security_group_id" {
  description = "Security group ID for the RDS instance"
  value       = aws_security_group.rds.id
}

# =============================================================================
# Resource Identifiers
# =============================================================================

output "instance_id" {
  description = "RDS instance identifier (null for Aurora)"
  value       = local.is_aurora ? null : aws_db_instance.postgres[0].id
}

output "cluster_id" {
  description = "Aurora cluster identifier (null for RDS)"
  value       = local.is_aurora ? aws_rds_cluster.aurora[0].id : null
}

output "cluster_arn" {
  description = "Aurora cluster ARN (null for RDS)"
  value       = local.is_aurora ? aws_rds_cluster.aurora[0].arn : null
}

output "instance_arn" {
  description = "RDS instance ARN (null for Aurora)"
  value       = local.is_aurora ? null : aws_db_instance.postgres[0].arn
}

# =============================================================================
# Engine Information
# =============================================================================

output "engine" {
  description = "Database engine (postgres or aurora-postgresql)"
  value       = local.is_aurora ? "aurora-postgresql" : "postgres"
}

output "engine_version" {
  description = "Database engine version"
  value       = local.is_aurora ? var.aurora_postgres_version : var.postgres_version
}

output "is_aurora" {
  description = "Whether Aurora is being used"
  value       = local.is_aurora
}
