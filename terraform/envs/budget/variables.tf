# Budget Environment - Variables

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
  default     = "budget"
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

variable "enable_flow_logs" {
  description = "Enable VPC flow logs"
  type        = bool
  default     = false
}

# =============================================================================
# Database Configuration
# =============================================================================

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
# EC2 Configuration
# =============================================================================

variable "ec2_instance_type" {
  description = "EC2 instance type (t2.micro for Free Tier)"
  type        = string
  default     = "t2.micro"
}

variable "allowed_ssh_cidr_blocks" {
  description = "CIDR blocks allowed to SSH"
  type        = list(string)
  default     = []
}

variable "ssh_public_key" {
  description = "SSH public key for EC2 access"
  type        = string
  default     = null
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

variable "enable_amplify_auto_build" {
  description = "Enable auto-build on push"
  type        = bool
  default     = true
}
