# ElastiCache Module
# Redis cluster for caching and session management
# Designed for production workloads with optional replication

locals {
  # Common tags for all resources
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "elasticache"
  }

  # Cluster identifier
  cluster_id = "${var.project_name}-${var.environment}"
}

# =============================================================================
# Security Group
# =============================================================================

resource "aws_security_group" "redis" {
  name        = "${var.project_name}-${var.environment}-redis-sg"
  description = "Security group for Redis ElastiCache"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from application"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = var.allowed_security_groups
    cidr_blocks     = var.allowed_cidr_blocks
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-redis-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

# =============================================================================
# Parameter Group
# =============================================================================

resource "aws_elasticache_parameter_group" "redis" {
  name        = "${var.project_name}-${var.environment}-redis-params"
  family      = "redis${split(".", var.redis_version)[0]}"
  description = "Redis parameter group for ${var.project_name}-${var.environment}"

  # Optimize for session storage and caching
  parameter {
    name  = "maxmemory-policy"
    value = var.maxmemory_policy
  }

  tags = local.common_tags
}

# =============================================================================
# Redis Replication Group (Cluster Mode Disabled)
# =============================================================================

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = local.cluster_id
  description          = "Redis cluster for ${var.project_name}-${var.environment}"

  # Engine
  engine               = "redis"
  engine_version       = var.redis_version
  node_type            = var.node_type
  parameter_group_name = aws_elasticache_parameter_group.redis.name

  # Replication
  num_cache_clusters         = var.num_cache_nodes
  automatic_failover_enabled = var.num_cache_nodes > 1 ? var.automatic_failover : false
  multi_az_enabled           = var.num_cache_nodes > 1 ? var.multi_az : false

  # Network
  subnet_group_name  = var.elasticache_subnet_group_name
  security_group_ids = [aws_security_group.redis.id]
  port               = 6379

  # Encryption
  at_rest_encryption_enabled = true
  transit_encryption_enabled = var.transit_encryption
  auth_token                 = var.transit_encryption ? var.auth_token : null

  # Maintenance
  maintenance_window       = var.maintenance_window
  snapshot_window          = var.snapshot_window
  snapshot_retention_limit = var.snapshot_retention_limit

  # Notifications
  notification_topic_arn = var.notification_topic_arn

  # Apply changes immediately in non-prod
  apply_immediately = var.environment != "prod" && var.environment != "production"

  # Auto minor version upgrade
  auto_minor_version_upgrade = true

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-redis"
  })

  lifecycle {
    ignore_changes = [auth_token]
  }
}

# =============================================================================
# SSM Parameters for Service Discovery
# =============================================================================

resource "aws_ssm_parameter" "redis_endpoint" {
  name        = "/${var.project_name}/${var.environment}/redis/endpoint"
  description = "Redis primary endpoint"
  type        = "String"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address

  tags = local.common_tags
}

resource "aws_ssm_parameter" "redis_port" {
  name        = "/${var.project_name}/${var.environment}/redis/port"
  description = "Redis port"
  type        = "String"
  value       = "6379"

  tags = local.common_tags
}

resource "aws_ssm_parameter" "redis_reader_endpoint" {
  count = var.num_cache_nodes > 1 ? 1 : 0

  name        = "/${var.project_name}/${var.environment}/redis/reader_endpoint"
  description = "Redis reader endpoint (for read replicas)"
  type        = "String"
  value       = aws_elasticache_replication_group.redis.reader_endpoint_address

  tags = local.common_tags
}
