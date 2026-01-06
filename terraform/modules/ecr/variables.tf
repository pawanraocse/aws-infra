# ECR Module - Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "services" {
  description = "List of service names to create repositories for"
  type        = list(string)
  default     = []
}

variable "image_tag_mutability" {
  description = "Allow image tags to be overwritten (MUTABLE or IMMUTABLE)"
  type        = string
  default     = "MUTABLE"
}

variable "scan_on_push" {
  description = "Scan images for vulnerabilities on push"
  type        = bool
  default     = true
}

variable "encryption_type" {
  description = "Encryption type (AES256 or KMS)"
  type        = string
  default     = "AES256"
}

variable "kms_key_arn" {
  description = "KMS key ARN for encryption (required if encryption_type is KMS)"
  type        = string
  default     = null
}

variable "max_image_count" {
  description = "Maximum number of images to retain per repository"
  type        = number
  default     = 30
}

variable "untagged_image_days" {
  description = "Days to keep untagged images before deletion"
  type        = number
  default     = 7
}

variable "allow_cross_account" {
  description = "Allow cross-account access to repositories"
  type        = bool
  default     = false
}

variable "allowed_account_ids" {
  description = "AWS account IDs allowed to pull images"
  type        = list(string)
  default     = []
}
