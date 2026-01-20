variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string
}

variable "user_pool_id" {
  description = "Cognito User Pool ID"
  type        = string
}

variable "user_pool_arn" {
  description = "Cognito User Pool ARN"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "aws_account_id" {
  description = "AWS account ID"
  type        = string
}

# ========== SSO Group Sync Variables ==========

variable "platform_service_url" {
  description = "URL of the platform service for group sync"
  type        = string
  default     = "http://localhost:8082"
}

variable "auth_service_url" {
  description = "URL of the auth service for role resolution"
  type        = string
  default     = "http://localhost:8081"
}

variable "enable_group_sync" {
  description = "Whether to enable SSO group sync during login"
  type        = bool
  default     = true
}

# ========== VPC Configuration (Optional) ==========

variable "enable_vpc_mode" {
  description = "Enable VPC mode for Lambda (required for private EC2 access)"
  type        = bool
  default     = false
}

variable "vpc_id" {
  description = "VPC ID for Lambda (required when enable_vpc_mode is true)"
  type        = string
  default     = null
}

variable "subnet_ids" {
  description = "Subnet IDs for Lambda VPC config (use public subnets if no NAT Gateway)"
  type        = list(string)
  default     = []
}
