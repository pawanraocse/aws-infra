# RDS Module

Production-grade AWS RDS module supporting both PostgreSQL and Aurora PostgreSQL.

## Features

- ✅ **Dual engine support** - PostgreSQL or Aurora PostgreSQL (configurable)
- ✅ **Secrets Manager integration** - Automatic credential management
- ✅ **SSM Parameters** - Service discovery for applications
- ✅ **Aurora Serverless v2** - Optional auto-scaling
- ✅ **Encryption at rest** - KMS encryption
- ✅ **Backup automation** - Configurable retention
- ✅ **Performance Insights** - Optional monitoring

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│  RDS Module                                                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────┐     ┌─────────────────────────────────────┐   │
│  │  RDS PostgreSQL     │ OR  │  Aurora PostgreSQL Cluster          │   │
│  │  (use_aurora=false) │     │  (use_aurora=true)                  │   │
│  │                     │     │  ┌─────────┐ ┌─────────┐            │   │
│  │  Single instance    │     │  │ Writer  │ │ Reader  │ ...        │   │
│  │  Multi-AZ optional  │     │  └─────────┘ └─────────┘            │   │
│  └─────────────────────┘     └─────────────────────────────────────┘   │
│           │                              │                              │
│           └──────────────┬───────────────┘                              │
│                          ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Secrets Manager                                                │   │
│  │  {username, password, host, port, database, engine}             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                          │                                              │
│                          ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  SSM Parameters                                                 │   │
│  │  /project/env/rds/{endpoint, port, database, username, secret}  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Usage

### Budget Deployment (RDS PostgreSQL - Free Tier)

```hcl
module "rds" {
  source = "../../modules/rds"

  project_name = "saas-factory"
  environment  = "budget"

  vpc_id               = module.vpc.vpc_id
  db_subnet_group_name = module.vpc.db_subnet_group_name

  # Use RDS PostgreSQL (Free Tier eligible)
  use_aurora      = false
  instance_class  = "db.t3.micro"  # Free Tier!
  
  # Minimal storage
  allocated_storage     = 20
  max_allocated_storage = 0  # Disable autoscaling
  
  # Budget: No Multi-AZ, skip final snapshot
  multi_az            = false
  deletion_protection = false
  skip_final_snapshot = true

  # Allow from ECS/EC2
  allowed_security_groups = [module.ecs.security_group_id]
}
```

### Production Deployment (Aurora PostgreSQL)

```hcl
module "rds" {
  source = "../../modules/rds"

  project_name = "saas-factory"
  environment  = "production"

  vpc_id               = module.vpc.vpc_id
  db_subnet_group_name = module.vpc.db_subnet_group_name

  # Use Aurora PostgreSQL for production
  use_aurora            = true
  aurora_instance_class = "db.r6g.large"
  aurora_instance_count = 2  # 1 writer + 1 reader
  
  # Production settings
  backup_retention_period     = 30
  deletion_protection         = true
  skip_final_snapshot         = false
  enable_performance_insights = true

  # Allow from ECS
  allowed_security_groups = [module.ecs.security_group_id]
}
```

### Aurora Serverless v2 (Auto-scaling)

```hcl
module "rds" {
  source = "../../modules/rds"

  project_name = "saas-factory"
  environment  = "production"

  vpc_id               = module.vpc.vpc_id
  db_subnet_group_name = module.vpc.db_subnet_group_name

  # Aurora Serverless v2
  use_aurora         = true
  aurora_serverless  = true
  aurora_min_capacity = 0.5   # Scale down to 0.5 ACU
  aurora_max_capacity = 16    # Scale up to 16 ACU
}
```

## Inputs

| Name | Type | Default | Required | Description |
|------|------|---------|----------|-------------|
| `project_name` | string | - | ✅ | Project name |
| `environment` | string | - | ✅ | Environment |
| `vpc_id` | string | - | ✅ | VPC ID |
| `db_subnet_group_name` | string | - | ✅ | DB subnet group |
| `use_aurora` | bool | `false` | | Use Aurora instead of RDS |
| `instance_class` | string | `db.t3.micro` | | RDS instance class |
| `aurora_instance_class` | string | `db.t3.medium` | | Aurora instance class |
| `aurora_serverless` | bool | `false` | | Use Aurora Serverless v2 |
| `database_name` | string | `saas_db` | | Database name |
| `master_username` | string | `postgres` | | Master username |
| `multi_az` | bool | `false` | | Multi-AZ deployment |
| `deletion_protection` | bool | `false` | | Deletion protection |

## Outputs

| Name | Description |
|------|-------------|
| `endpoint` | Database endpoint hostname |
| `port` | Database port (5432) |
| `database_name` | Database name |
| `connection_string` | JDBC connection string |
| `secret_arn` | Secrets Manager ARN for credentials |
| `security_group_id` | RDS security group ID |
| `ssm_endpoint_path` | SSM path for endpoint |
| `ssm_secret_arn_path` | SSM path for secret ARN |

## Secrets Manager Structure

```json
{
  "username": "postgres",
  "password": "auto-generated-32-char",
  "host": "saas-factory-prod.xxxxx.us-east-1.rds.amazonaws.com",
  "port": 5432,
  "database": "saas_db",
  "engine": "aurora-postgresql"
}
```

## Spring Boot Integration

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

ECS pulls these from Secrets Manager automatically - no code changes needed!

## Cost Comparison

| Option | Instance | Monthly Cost |
|--------|----------|--------------|
| RDS db.t3.micro | Free Tier | **$0** (12 months) |
| RDS db.t3.small | Single-AZ | ~$25 |
| Aurora db.t3.medium | 1 instance | ~$60 |
| Aurora Serverless | 0.5-4 ACU | ~$45-180 |

## Security

- **Encryption at rest** - KMS encryption enabled by default
- **Secrets Manager** - Credentials never in plaintext
- **Private subnets** - No public access
- **Security groups** - Explicit allow-list
