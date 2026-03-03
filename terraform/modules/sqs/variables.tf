variable "project_name" {
  description = "Project name for resource naming"
  type        = string
}

variable "environment" {
  description = "Environment name (budget, production)"
  type        = string
}

variable "visibility_timeout_seconds" {
  description = "Time (seconds) a message is hidden after being received. Should exceed max processing time."
  type        = number
  default     = 120
}

variable "retention_seconds" {
  description = "How long (seconds) messages are retained in the main queue"
  type        = number
  default     = 86400 # 24 hours
}

variable "dlq_retention_seconds" {
  description = "How long (seconds) messages are retained in the DLQ"
  type        = number
  default     = 1209600 # 14 days
}

variable "max_receive_count" {
  description = "Number of times a message can be received before being sent to DLQ"
  type        = number
  default     = 3
}

variable "receive_wait_time_seconds" {
  description = "Long polling wait time (seconds). 0 = short polling."
  type        = number
  default     = 10
}
