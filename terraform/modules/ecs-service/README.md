# ECS Service Module (Generic & Reusable)

Deploy any containerized service to ECS Fargate. Template-friendly design for easy scaling and extensibility.

## Features

- ✅ **Reusable** - Same module for all services
- ✅ **Scalable** - Change `desired_count` for replicas
- ✅ **Auto-scaling** - CPU/Memory target tracking
- ✅ **Secrets injection** - SSM and Secrets Manager support
- ✅ **ALB integration** - Optional load balancer attachment

## Usage

### Basic Service

```hcl
module "gateway" {
  source = "../../modules/ecs-service"

  project_name = "saas-factory"
  environment  = "production"
  service_name = "gateway"

  cluster_id   = module.ecs_cluster.cluster_id
  cluster_name = module.ecs_cluster.cluster_name
  vpc_id       = module.vpc.vpc_id
  subnet_ids   = module.vpc.private_subnet_ids

  container_image = module.ecr.repository_urls["gateway-service"]
  container_port  = 8080
  desired_count   = 2

  # Secrets from SSM/Secrets Manager
  secrets = {
    COGNITO_USER_POOL_ID = "arn:aws:ssm:us-east-1:123:parameter/project/prod/cognito/user_pool_id"
    DB_PASSWORD          = module.rds.secret_arn
  }

  # ALB integration
  target_group_arn        = module.alb.target_group_arns["gateway"]
  allowed_security_groups = [module.alb.security_group_id]
}
```

### With Auto-Scaling

```hcl
module "backend" {
  source = "../../modules/ecs-service"

  # ... basic config ...

  desired_count      = 2
  enable_autoscaling = true
  min_capacity       = 2
  max_capacity       = 10
  cpu_target         = 70  # Scale when CPU > 70%
}
```

### OpenFGA (Docker Hub Image)

```hcl
module "openfga" {
  source = "../../modules/ecs-service"

  service_name    = "openfga"
  container_image = "openfga/openfga:latest"  # From Docker Hub!
  container_port  = 8080

  environment_variables = {
    OPENFGA_DATASTORE_ENGINE = "postgres"
    OPENFGA_DATASTORE_URI    = "postgres://user:pass@host:5432/openfga"
  }
}
```

## Inputs

| Name | Type | Default | Description |
|------|------|---------|-------------|
| `service_name` | string | required | Service name |
| `container_image` | string | required | Docker image URL |
| `container_port` | number | `8080` | Container port |
| `cpu` | number | `256` | CPU units |
| `memory` | number | `512` | Memory in MB |
| `desired_count` | number | `1` | Number of replicas |
| `enable_autoscaling` | bool | `false` | Enable auto-scaling |
| `min_capacity` | number | `1` | Min tasks |
| `max_capacity` | number | `10` | Max tasks |
| `cpu_target` | number | `70` | CPU scaling target |

## Outputs

| Name | Description |
|------|-------------|
| `service_id` | ECS Service ID |
| `security_group_id` | Service security group |
| `task_definition_arn` | Task definition ARN |
