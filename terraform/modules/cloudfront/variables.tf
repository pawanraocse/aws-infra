variable "origin_domain_name" {
  description = "The DNS domain name of your custom origin (e.g., EC2 DNS)"
  type        = string
}

variable "origin_id" {
  description = "Unique identifier for the origin"
  type        = string
}

variable "comment" {
  description = "Comment for the CloudFront distribution"
  type        = string
  default     = "Managed by Terraform"
}
