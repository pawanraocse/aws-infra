# ECS Cluster Module
# Fargate-enabled ECS cluster with CloudWatch Container Insights

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "ecs-cluster"
  }

  cluster_name = "${var.project_name}-${var.environment}"
}

# =============================================================================
# ECS Cluster
# =============================================================================

resource "aws_ecs_cluster" "main" {
  name = local.cluster_name

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  configuration {
    execute_command_configuration {
      logging = "OVERRIDE"

      log_configuration {
        cloud_watch_encryption_enabled = true
        cloud_watch_log_group_name     = aws_cloudwatch_log_group.ecs_exec.name
      }
    }
  }

  tags = merge(local.common_tags, {
    Name = local.cluster_name
  })
}

# =============================================================================
# Capacity Providers
# =============================================================================

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    base              = var.fargate_base
    weight            = var.fargate_weight
    capacity_provider = "FARGATE"
  }

  dynamic "default_capacity_provider_strategy" {
    for_each = var.enable_fargate_spot ? [1] : []
    content {
      weight            = var.fargate_spot_weight
      capacity_provider = "FARGATE_SPOT"
    }
  }
}

# =============================================================================
# CloudWatch Log Groups
# =============================================================================

resource "aws_cloudwatch_log_group" "ecs_exec" {
  name              = "/aws/ecs/${local.cluster_name}/exec"
  retention_in_days = var.log_retention_days

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "cluster" {
  name              = "/aws/ecs/${local.cluster_name}/cluster"
  retention_in_days = var.log_retention_days

  tags = local.common_tags
}

# =============================================================================
# Service Discovery Namespace (optional)
# =============================================================================

resource "aws_service_discovery_private_dns_namespace" "main" {
  count = var.enable_service_discovery ? 1 : 0

  name        = "${var.project_name}.local"
  description = "Service discovery namespace for ${var.project_name}"
  vpc         = var.vpc_id

  tags = local.common_tags
}
