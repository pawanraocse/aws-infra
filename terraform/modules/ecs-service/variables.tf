# ECS Service Module - Variables

# =============================================================================
# Required Variables
# =============================================================================

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "service_name" {
  description = "Name of the service (e.g., gateway-service, auth-service)"
  type        = string
}

variable "cluster_id" {
  description = "ECS Cluster ID"
  type        = string
}

variable "cluster_name" {
  description = "ECS Cluster name (for auto-scaling resource ID)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the service"
  type        = list(string)
}

variable "container_image" {
  description = "Docker image URL (ECR or Docker Hub)"
  type        = string
}

variable "aws_region" {
  description = "AWS region for CloudWatch logs"
  type        = string
  default     = "us-east-1"
}

# =============================================================================
# Container Configuration
# =============================================================================

variable "container_port" {
  description = "Container port"
  type        = number
  default     = 8080
}

variable "cpu" {
  description = "CPU units (256, 512, 1024, 2048, 4096)"
  type        = number
  default     = 256
}

variable "memory" {
  description = "Memory in MB (512, 1024, 2048, etc.)"
  type        = number
  default     = 512
}

variable "desired_count" {
  description = "Number of task replicas"
  type        = number
  default     = 1
}

variable "environment_variables" {
  description = "Environment variables for the container"
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Secrets from SSM or Secrets Manager (key = env var name, value = ARN)"
  type        = map(string)
  default     = {}
}

variable "container_health_check" {
  description = "Container health check configuration"
  type = object({
    command      = list(string)
    interval     = number
    timeout      = number
    retries      = number
    start_period = number
  })
  default = null
}

# =============================================================================
# Networking
# =============================================================================

variable "allowed_security_groups" {
  description = "Security groups allowed to access this service"
  type        = list(string)
  default     = []
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access this service"
  type        = list(string)
  default     = []
}

variable "additional_security_groups" {
  description = "Additional security groups to attach"
  type        = list(string)
  default     = []
}

variable "assign_public_ip" {
  description = "Assign public IP to tasks"
  type        = bool
  default     = false
}

# =============================================================================
# Load Balancer
# =============================================================================

variable "target_group_arn" {
  description = "ALB target group ARN (optional)"
  type        = string
  default     = null
}

# =============================================================================
# IAM
# =============================================================================

variable "task_role_policy_arns" {
  description = "Additional IAM policy ARNs for the task role"
  type        = list(string)
  default     = []
}

# =============================================================================
# Auto Scaling
# =============================================================================

variable "enable_autoscaling" {
  description = "Enable auto-scaling"
  type        = bool
  default     = false
}

variable "min_capacity" {
  description = "Minimum number of tasks"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks"
  type        = number
  default     = 10
}

variable "cpu_target" {
  description = "Target CPU utilization for scaling (%)"
  type        = number
  default     = 70
}

variable "memory_target" {
  description = "Target memory utilization for scaling (%) - null to disable"
  type        = number
  default     = null
}

variable "scale_in_cooldown" {
  description = "Scale in cooldown in seconds"
  type        = number
  default     = 300
}

variable "scale_out_cooldown" {
  description = "Scale out cooldown in seconds"
  type        = number
  default     = 60
}

# =============================================================================
# Logging
# =============================================================================

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

# =============================================================================
# Service Discovery
# =============================================================================

variable "service_discovery_namespace_id" {
  description = "Cloud Map namespace ID for service discovery (optional)"
  type        = string
  default     = null
}
