# VPC Module

Production-grade AWS VPC module with public, private, and database subnets.

## Features

- ✅ **Multi-AZ deployment** - High availability across availability zones
- ✅ **Three-tier architecture** - Public, private, and database subnets
- ✅ **NAT Gateway support** - Optional, single or per-AZ configuration
- ✅ **Database isolation** - Dedicated subnet group for RDS/ElastiCache
- ✅ **VPC Flow Logs** - Optional network traffic logging
- ✅ **Production-ready defaults** - Sensible defaults for security and cost

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│  VPC (10.0.0.0/16)                                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  PUBLIC SUBNETS (Internet accessible)                          │   │
│  │  10.0.0.0/20 (AZ-a)  │  10.0.16.0/20 (AZ-b)                    │   │
│  │  ↓ ALB, NAT Gateway, Bastion                                   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                               │                                         │
│                          NAT Gateway                                    │
│                               ↓                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  PRIVATE SUBNETS (NAT access only)                             │   │
│  │  10.0.48.0/20 (AZ-a)  │  10.0.64.0/20 (AZ-b)                   │   │
│  │  ↓ ECS Tasks, Lambda, Internal Services                        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  DATABASE SUBNETS (Isolated - no internet)                     │   │
│  │  10.0.96.0/20 (AZ-a)  │  10.0.112.0/20 (AZ-b)                  │   │
│  │  ↓ RDS, ElastiCache                                            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Usage

### Budget Deployment (No NAT Gateway)

```hcl
module "vpc" {
  source = "../../modules/vpc"

  project_name  = "saas-factory"
  environment   = "budget"
  
  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["us-east-1a", "us-east-1b"]
  
  # Budget: No NAT Gateway (EC2 in public subnet)
  enable_nat_gateway = false
  
  # Optional: Enable flow logs for debugging
  enable_flow_logs = false
}
```

### Production Deployment (Single NAT Gateway)

```hcl
module "vpc" {
  source = "../../modules/vpc"

  project_name  = "saas-factory"
  environment   = "production"
  
  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["us-east-1a", "us-east-1b"]
  
  # Production: Single NAT Gateway (cost-effective)
  enable_nat_gateway = true
  single_nat_gateway = true
  
  # Enable flow logs for security
  enable_flow_logs         = true
  flow_logs_retention_days = 30
}
```

### High-Availability (Multi-AZ NAT)

```hcl
module "vpc" {
  source = "../../modules/vpc"

  project_name  = "saas-factory"
  environment   = "production"
  
  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]
  
  # HA: NAT Gateway per AZ
  enable_nat_gateway = true
  single_nat_gateway = false
}
```

## Inputs

| Name | Type | Default | Required | Description |
|------|------|---------|----------|-------------|
| `project_name` | string | - | ✅ | Project name for resource naming |
| `environment` | string | - | ✅ | Environment (dev, staging, prod, budget, production) |
| `vpc_cidr` | string | `10.0.0.0/16` | | VPC CIDR block |
| `availability_zones` | list(string) | `["us-east-1a", "us-east-1b"]` | | AZs to use (min 2) |
| `enable_nat_gateway` | bool | `true` | | Enable NAT Gateway |
| `single_nat_gateway` | bool | `true` | | Use single NAT vs per-AZ |
| `enable_flow_logs` | bool | `false` | | Enable VPC Flow Logs |
| `flow_logs_retention_days` | number | `14` | | Flow logs retention |

## Outputs

| Name | Description |
|------|-------------|
| `vpc_id` | VPC ID |
| `vpc_cidr` | VPC CIDR block |
| `public_subnet_ids` | List of public subnet IDs |
| `private_subnet_ids` | List of private subnet IDs |
| `database_subnet_ids` | List of database subnet IDs |
| `db_subnet_group_name` | RDS subnet group name |
| `elasticache_subnet_group_name` | ElastiCache subnet group name |
| `nat_gateway_ids` | List of NAT Gateway IDs |
| `nat_gateway_public_ips` | NAT Gateway public IPs |

## Cost Considerations

| Resource | Cost (estimate) |
|----------|-----------------|
| VPC | Free |
| Subnets | Free |
| Internet Gateway | Free |
| NAT Gateway | ~$32/month + data transfer |
| VPC Flow Logs | ~$0.50/GB ingested |

**Budget Deployment:** Disable NAT Gateway = **$0/month**

**Production Deployment:** Single NAT Gateway = **~$32/month**

## Security

- Database subnets have **no internet access** (isolated)
- Private subnets can only access internet via **NAT Gateway**
- All resources tagged with `ManagedBy = Terraform`
- Optional VPC Flow Logs for network auditing
