# VPC Module - Input Variables
# All configurable parameters for the VPC module

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

# =============================================================================
# Network Configuration
# =============================================================================

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "VPC CIDR must be a valid CIDR block."
  }
}

variable "availability_zones" {
  description = "List of availability zones to use"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]

  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "At least 2 availability zones are required for high availability."
  }
}

# =============================================================================
# NAT Gateway Configuration
# =============================================================================

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway for private subnet internet access. Required for ECS/Fargate to pull images."
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Use a single NAT Gateway instead of one per AZ (cost savings vs. high availability)"
  type        = bool
  default     = true # Default to single NAT for cost efficiency
}

# =============================================================================
# Flow Logs Configuration
# =============================================================================

variable "enable_flow_logs" {
  description = "Enable VPC Flow Logs for network debugging and security"
  type        = bool
  default     = false # Disabled by default for cost savings
}

variable "flow_logs_retention_days" {
  description = "Number of days to retain flow logs in CloudWatch"
  type        = number
  default     = 14

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653], var.flow_logs_retention_days)
    error_message = "Flow logs retention must be a valid CloudWatch Logs retention period."
  }
}

# =============================================================================
# Tags
# =============================================================================

variable "additional_tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}
