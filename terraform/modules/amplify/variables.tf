# Amplify Module - Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "repository_url" {
  description = "GitHub repository URL"
  type        = string
}

variable "github_access_token" {
  description = "GitHub personal access token for repository access"
  type        = string
  sensitive   = true
}

variable "branch_name" {
  description = "Git branch to deploy"
  type        = string
  default     = "main"
}

variable "app_name" {
  description = "Name of the Angular app (used in build output path)"
  type        = string
  default     = "frontend"
}

variable "app_root" {
  description = "Root path of the app in monorepo"
  type        = string
  default     = "frontend"
}

variable "build_spec" {
  description = "Custom build specification (null for default)"
  type        = string
  default     = null
}

variable "environment_variables" {
  description = "Environment variables for the app"
  type        = map(string)
  default     = {}
}

variable "branch_environment_variables" {
  description = "Branch-specific environment variables"
  type        = map(string)
  default     = {}
}

variable "enable_auto_build" {
  description = "Enable automatic builds on push"
  type        = bool
  default     = true
}

variable "enable_pr_preview" {
  description = "Enable preview environments for PRs"
  type        = bool
  default     = false
}

variable "domain_name" {
  description = "Custom domain name (optional)"
  type        = string
  default     = null
}

variable "subdomain_prefix" {
  description = "Subdomain prefix (e.g., 'app' for app.example.com)"
  type        = string
  default     = ""
}

variable "wait_for_verification" {
  description = "Wait for domain verification"
  type        = bool
  default     = false
}
