# Production Environment - Variables

# =============================================================================
# Project Configuration
# =============================================================================

variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "saas-factory"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

# =============================================================================
# VPC Configuration
# =============================================================================

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "single_nat_gateway" {
  description = "Use single NAT Gateway (cheaper, less HA)"
  type        = bool
  default     = true # Set false for production HA
}

# =============================================================================
# RDS Configuration
# =============================================================================

variable "use_aurora" {
  description = "Use Aurora PostgreSQL instead of RDS"
  type        = bool
  default     = false
}

variable "rds_instance_class" {
  description = "RDS instance class (db.t3.micro for testing, db.t3.small+ for production)"
  type        = string
  default     = "db.t3.micro" # TESTING: Free tier. PRODUCTION: db.t3.small or db.r6g.large
}

variable "rds_allocated_storage" {
  description = "Initial storage in GB"
  type        = number
  default     = 20
}

variable "rds_max_allocated_storage" {
  description = "Max storage for autoscaling (0 to disable)"
  type        = number
  default     = 100
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ deployment"
  type        = bool
  default     = false # Set true for production HA
}

variable "database_name" {
  description = "Database name"
  type        = string
  default     = "saas_db"
}

variable "database_username" {
  description = "Database master username"
  type        = string
  default     = "postgres"
}

# =============================================================================
# ElastiCache Configuration
# =============================================================================

variable "redis_node_type" {
  description = "ElastiCache node type (cache.t2.micro for testing, cache.t3.small+ for production)"
  type        = string
  default     = "cache.t2.micro" # TESTING: Free tier. PRODUCTION: cache.t3.small or cache.r6g.large
}

variable "redis_num_nodes" {
  description = "Number of cache nodes (1 for single, 2+ for cluster)"
  type        = number
  default     = 1
}

# =============================================================================
# SSL Certificate
# =============================================================================

variable "acm_certificate_arn" {
  description = "ACM certificate ARN for HTTPS"
  type        = string
}

# =============================================================================
# Amplify Configuration
# =============================================================================

variable "frontend_repository_url" {
  description = "GitHub repository URL for frontend"
  type        = string
}

variable "github_access_token" {
  description = "GitHub personal access token"
  type        = string
  sensitive   = true
}

variable "frontend_branch" {
  description = "Git branch to deploy"
  type        = string
  default     = "main"
}

variable "frontend_app_root" {
  description = "Root path of frontend app in repository"
  type        = string
  default     = "frontend"
}

variable "frontend_custom_domain" {
  description = "Custom domain for frontend (optional)"
  type        = string
  default     = null
}

# =============================================================================
# Bastion Configuration
# =============================================================================

variable "create_bastion" {
  description = "Create bastion host for DB access"
  type        = bool
  default     = true
}

variable "bastion_allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH to bastion"
  type        = list(string)
  default     = []
}

variable "bastion_ssh_public_key" {
  description = "SSH public key for bastion"
  type        = string
  default     = null
}
