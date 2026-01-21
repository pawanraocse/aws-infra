# Bastion Module - Variables

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

variable "subnet_id" {
  description = "Subnet ID for the bastion (should be public)"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t2.micro" # Free Tier eligible
}

variable "ami_id" {
  description = "AMI ID (null for latest Amazon Linux 2023)"
  type        = string
  default     = null
}

variable "allowed_ssh_cidr_blocks" {
  description = "CIDR blocks allowed to SSH to bastion"
  type        = list(string)
  default     = []
}

variable "ssh_public_key" {
  description = "SSH public key for key pair (null to skip)"
  type        = string
  default     = null
}

variable "root_volume_size" {
  description = "Root volume size in GB"
  type        = number
  default     = 30
}

variable "create_eip" {
  description = "Create Elastic IP for consistent address"
  type        = bool
  default     = false
}

# ========== Lambda Access Configuration ==========

variable "lambda_security_group_id" {
  description = "Lambda security group ID for internal service access (ports 8081/8083)"
  type        = string
  default     = null
}

# ========== Cognito Configuration ==========

variable "cognito_user_pool_arn" {
  description = "Cognito User Pool ARN for auth-service to manage users and identity providers"
  type        = string
  default     = null
}

