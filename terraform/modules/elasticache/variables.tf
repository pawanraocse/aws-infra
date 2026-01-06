# ElastiCache Module - Input Variables

# =============================================================================
# Required Variables
# =============================================================================

variable "project_name" {
  description = "Name of the project (used for resource naming)"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.project_name))
    error_message = "Project name must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod", "budget", "production"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod, budget, production."
  }
}

variable "vpc_id" {
  description = "VPC ID where ElastiCache will be deployed"
  type        = string
}

variable "elasticache_subnet_group_name" {
  description = "Name of the ElastiCache subnet group"
  type        = string
}

# =============================================================================
# Redis Configuration
# =============================================================================

variable "redis_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.0"
}

variable "node_type" {
  description = "ElastiCache node type (e.g., cache.t3.micro)"
  type        = string
  default     = "cache.t3.micro"
}

variable "num_cache_nodes" {
  description = "Number of cache nodes (1 for single, 2+ for replication)"
  type        = number
  default     = 1

  validation {
    condition     = var.num_cache_nodes >= 1 && var.num_cache_nodes <= 6
    error_message = "Number of cache nodes must be between 1 and 6."
  }
}

variable "maxmemory_policy" {
  description = "Redis maxmemory eviction policy"
  type        = string
  default     = "volatile-lru"

  validation {
    condition     = contains(["volatile-lru", "allkeys-lru", "volatile-lfu", "allkeys-lfu", "volatile-random", "allkeys-random", "volatile-ttl", "noeviction"], var.maxmemory_policy)
    error_message = "Invalid maxmemory policy."
  }
}

# =============================================================================
# High Availability
# =============================================================================

variable "automatic_failover" {
  description = "Enable automatic failover (requires num_cache_nodes > 1)"
  type        = bool
  default     = true
}

variable "multi_az" {
  description = "Enable Multi-AZ (requires num_cache_nodes > 1)"
  type        = bool
  default     = false
}

# =============================================================================
# Security
# =============================================================================

variable "allowed_security_groups" {
  description = "List of security group IDs allowed to connect"
  type        = list(string)
  default     = []
}

variable "allowed_cidr_blocks" {
  description = "List of CIDR blocks allowed to connect"
  type        = list(string)
  default     = []
}

variable "transit_encryption" {
  description = "Enable in-transit encryption (TLS)"
  type        = bool
  default     = false
}

variable "auth_token" {
  description = "Auth token for Redis (required if transit_encryption is true)"
  type        = string
  default     = null
  sensitive   = true
}

# =============================================================================
# Maintenance
# =============================================================================

variable "maintenance_window" {
  description = "Weekly maintenance window (UTC)"
  type        = string
  default     = "sun:05:00-sun:06:00"
}

variable "snapshot_window" {
  description = "Daily snapshot window (UTC)"
  type        = string
  default     = "04:00-05:00"
}

variable "snapshot_retention_limit" {
  description = "Number of days to retain snapshots (0 to disable)"
  type        = number
  default     = 0
}

variable "notification_topic_arn" {
  description = "SNS topic ARN for notifications"
  type        = string
  default     = null
}

# =============================================================================
# Tags
# =============================================================================

variable "additional_tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
