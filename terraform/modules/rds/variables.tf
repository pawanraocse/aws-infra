# RDS Module - Input Variables
# All configurable parameters for the RDS module

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
  description = "VPC ID where RDS will be deployed"
  type        = string
}

variable "db_subnet_group_name" {
  description = "Name of the DB subnet group"
  type        = string
}

# =============================================================================
# Engine Selection
# =============================================================================

variable "use_aurora" {
  description = "Use Aurora PostgreSQL instead of RDS PostgreSQL. Aurora provides better performance and HA but costs more."
  type        = bool
  default     = false
}

# =============================================================================
# RDS PostgreSQL Configuration
# =============================================================================

variable "postgres_version" {
  description = "PostgreSQL engine version for RDS"
  type        = string
  default     = "16"
}

variable "instance_class" {
  description = "RDS instance class (e.g., db.t3.micro, db.t3.small)"
  type        = string
  default     = "db.t3.micro"
}

variable "allocated_storage" {
  description = "Initial storage allocation in GB"
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Maximum storage for autoscaling in GB (0 to disable)"
  type        = number
  default     = 100
}

variable "storage_type" {
  description = "Storage type (gp2, gp3, io1)"
  type        = string
  default     = "gp3"
}

variable "multi_az" {
  description = "Enable Multi-AZ deployment for high availability"
  type        = bool
  default     = false
}

# =============================================================================
# Aurora PostgreSQL Configuration
# =============================================================================

variable "aurora_postgres_version" {
  description = "Aurora PostgreSQL engine version"
  type        = string
  default     = "15.4"
}

variable "aurora_instance_class" {
  description = "Aurora instance class (e.g., db.r6g.large)"
  type        = string
  default     = "db.t3.medium"
}

variable "aurora_instance_count" {
  description = "Number of Aurora instances (1 writer + N-1 readers)"
  type        = number
  default     = 1
}

variable "aurora_serverless" {
  description = "Use Aurora Serverless v2 (auto-scaling)"
  type        = bool
  default     = false
}

variable "aurora_min_capacity" {
  description = "Aurora Serverless v2 minimum ACUs"
  type        = number
  default     = 0.5
}

variable "aurora_max_capacity" {
  description = "Aurora Serverless v2 maximum ACUs"
  type        = number
  default     = 4
}

# =============================================================================
# Database Configuration
# =============================================================================

variable "database_name" {
  description = "Name of the default database to create"
  type        = string
  default     = "saas_db"

  validation {
    condition     = can(regex("^[a-z][a-z0-9_]*$", var.database_name))
    error_message = "Database name must start with a letter and contain only lowercase letters, numbers, and underscores."
  }
}

variable "master_username" {
  description = "Master username for the database"
  type        = string
  default     = "postgres"

  validation {
    condition     = can(regex("^[a-z][a-z0-9_]*$", var.master_username))
    error_message = "Username must start with a letter and contain only lowercase letters, numbers, and underscores."
  }
}

# =============================================================================
# Security Configuration
# =============================================================================

variable "allowed_security_groups" {
  description = "List of security group IDs allowed to connect to RDS"
  type        = list(string)
  default     = []
}

variable "allowed_cidr_blocks" {
  description = "List of CIDR blocks allowed to connect to RDS"
  type        = list(string)
  default     = []
}

variable "kms_key_id" {
  description = "KMS key ID for encryption (null for AWS managed key)"
  type        = string
  default     = null
}

# =============================================================================
# Backup Configuration
# =============================================================================

variable "backup_retention_period" {
  description = "Number of days to retain backups"
  type        = number
  default     = 7

  validation {
    condition     = var.backup_retention_period >= 0 && var.backup_retention_period <= 35
    error_message = "Backup retention must be between 0 and 35 days."
  }
}

variable "backup_window" {
  description = "Preferred backup window (UTC)"
  type        = string
  default     = "03:00-04:00"
}

variable "maintenance_window" {
  description = "Preferred maintenance window (UTC)"
  type        = string
  default     = "Mon:04:00-Mon:05:00"
}

# =============================================================================
# Monitoring Configuration
# =============================================================================

variable "enable_performance_insights" {
  description = "Enable Performance Insights"
  type        = bool
  default     = false
}

variable "cloudwatch_logs_exports" {
  description = "List of logs to export to CloudWatch"
  type        = list(string)
  default     = ["postgresql", "upgrade"]
}

# =============================================================================
# Deletion Protection
# =============================================================================

variable "deletion_protection" {
  description = "Enable deletion protection (recommended for production)"
  type        = bool
  default     = false
}

variable "skip_final_snapshot" {
  description = "Skip final snapshot on deletion (set false for production)"
  type        = bool
  default     = true
}

# =============================================================================
# Tags
# =============================================================================

variable "additional_tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
