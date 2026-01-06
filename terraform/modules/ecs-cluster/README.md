# ECS Cluster Module

Fargate-enabled ECS cluster with optional Spot capacity.

## Usage

```hcl
module "ecs_cluster" {
  source = "../../modules/ecs-cluster"

  project_name = "saas-factory"
  environment  = "production"

  enable_container_insights = true
  enable_fargate_spot       = true  # Cost savings
  
  # Service discovery
  vpc_id                   = module.vpc.vpc_id
  enable_service_discovery = true
}
```

## Outputs

| Name | Description |
|------|-------------|
| `cluster_id` | ECS Cluster ID |
| `cluster_arn` | ECS Cluster ARN |
| `cluster_name` | ECS Cluster name |
