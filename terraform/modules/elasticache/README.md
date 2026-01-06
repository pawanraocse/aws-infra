# ElastiCache Module

AWS ElastiCache Redis module for caching and session management.

## Features

- ✅ **Replication support** - Single node or with read replicas
- ✅ **Automatic failover** - High availability option
- ✅ **Encryption** - At-rest and in-transit encryption
- ✅ **SSM Parameters** - Service discovery for applications
- ✅ **Configurable eviction** - LRU, LFU, TTL policies

## Usage

### Budget Deployment (Single Node)

```hcl
module "elasticache" {
  source = "../../modules/elasticache"

  project_name = "saas-factory"
  environment  = "budget"

  vpc_id                        = module.vpc.vpc_id
  elasticache_subnet_group_name = module.vpc.elasticache_subnet_group_name

  # Single node - no HA
  node_type       = "cache.t3.micro"
  num_cache_nodes = 1

  # No snapshots for budget
  snapshot_retention_limit = 0

  # Allow from EC2/ECS
  allowed_security_groups = [module.ec2.security_group_id]
}
```

### Production Deployment (With Replication)

```hcl
module "elasticache" {
  source = "../../modules/elasticache"

  project_name = "saas-factory"
  environment  = "production"

  vpc_id                        = module.vpc.vpc_id
  elasticache_subnet_group_name = module.vpc.elasticache_subnet_group_name

  # 2 nodes: 1 primary + 1 replica
  node_type       = "cache.t3.small"
  num_cache_nodes = 2

  # Enable HA
  automatic_failover = true
  multi_az           = true

  # Enable snapshots
  snapshot_retention_limit = 7

  # Allow from ECS
  allowed_security_groups = [module.ecs.security_group_id]
}
```

## Inputs

| Name | Type | Default | Required | Description |
|------|------|---------|----------|-------------|
| `project_name` | string | - | ✅ | Project name |
| `environment` | string | - | ✅ | Environment |
| `vpc_id` | string | - | ✅ | VPC ID |
| `elasticache_subnet_group_name` | string | - | ✅ | Subnet group |
| `redis_version` | string | `7.0` | | Redis version |
| `node_type` | string | `cache.t3.micro` | | Node type |
| `num_cache_nodes` | number | `1` | | Number of nodes |
| `automatic_failover` | bool | `true` | | Auto failover |
| `multi_az` | bool | `false` | | Multi-AZ |

## Outputs

| Name | Description |
|------|-------------|
| `primary_endpoint` | Primary endpoint for read/write |
| `reader_endpoint` | Reader endpoint (if replicas) |
| `port` | Redis port (6379) |
| `security_group_id` | Security group ID |
| `ssm_endpoint_path` | SSM path for endpoint |

## Spring Boot Integration

```yaml
# application-prod.yml
spring:
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT:6379}
```

## Cost Comparison

| Node Type | Single | With Replica | Monthly |
|-----------|--------|--------------|---------|
| cache.t3.micro | ✅ | - | ~$12 |
| cache.t3.small | ✅ | ✅ | ~$24-48 |
| cache.r6g.large | ✅ | ✅ | ~$120+ |

**Budget Recommendation:** For Budget deployment, use Redis in docker-compose instead of ElastiCache to save costs.
