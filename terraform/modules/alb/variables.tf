# ALB Module - Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the ALB (should be public)"
  type        = list(string)
}

variable "internal" {
  description = "Internal ALB (not internet-facing)"
  type        = bool
  default     = false
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS"
  type        = string
  default     = null
}

variable "ssl_policy" {
  description = "SSL policy for HTTPS listener"
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = false
}

variable "access_logs_bucket" {
  description = "S3 bucket for access logs"
  type        = string
  default     = null
}

variable "access_logs_prefix" {
  description = "S3 prefix for access logs"
  type        = string
  default     = "alb-logs"
}

variable "default_health_check_path" {
  description = "Health check path for default target group"
  type        = string
  default     = "/health"
}

variable "target_groups" {
  description = "Map of target groups to create"
  type = map(object({
    port                 = number
    health_check_path    = string
    health_check_matcher = string
    priority             = number
    path_patterns        = list(string)
  }))
  default = {}
}
