# AWS Deployment Guide

**Version:** 2.0  
**Updated:** 2026-01-21

> **Related Docs:**
> - [CONFIGURATION.md](./CONFIGURATION.md) - Central config reference
> - [DEBUGGING.md](./DEBUGGING.md) - Troubleshooting guide

---

## Deployment Options

| Option | Cost | Use Case |
|--------|------|----------|
| **Local** | Free | Development |
| **Budget EC2** | ~$15-30/month | Hobby, testing |
| **ECS Fargate** | ~$150/month | Production |
| **Kubernetes** | Varies | Multi-cloud, K8s teams |

---

## Quick Start

```bash
# Local Development
docker-compose up

# AWS Budget (~$15-30/month)
cd terraform/envs/budget && terraform apply

# AWS Production (~$150/month)  
cd terraform/envs/production && terraform apply
```

---

## Architecture by Environment

### Local (Docker Compose)
```
┌─────────────────────────────────────────────────────────┐
│  Your Machine                                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Gateway  │ │ Auth     │ │ Platform │ │ Backend  │   │
│  │ :8080    │ │ :8081    │ │ :8083    │ │ :8082    │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘   │
│       └────────────┴────────────┴────────────┘          │
│                         │                               │
│       ┌─────────────────┴─────────────────┐            │
│       ▼                                   ▼            │
│  ┌──────────┐                       ┌──────────┐       │
│  │ Postgres │                       │  Redis   │       │
│  │ :5432    │                       │  :6379   │       │
│  └──────────┘                       └──────────┘       │
└─────────────────────────────────────────────────────────┘
```

### Budget EC2 (~$15-30/month)
```
┌─────────────────────────────────────────────────────────┐
│  AWS                                                    │
│  ┌────────────────────────────────────────────────────┐│
│  │  EC2 t3.small (runs docker-compose)                ││
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐      ││
│  │  │Gateway │ │ Auth   │ │Platform│ │Backend │      ││
│  │  └────────┘ └────────┘ └────────┘ └────────┘      ││
│  │  ┌────────┐ ┌────────┐ ┌────────┐                 ││
│  │  │Postgres│ │ Redis  │ │OpenFGA │ ← Containers    ││
│  │  └────────┘ └────────┘ └────────┘                 ││
│  └────────────────────────────────────────────────────┘│
│                                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐               │
│  │ Cognito  │ │ Amplify  │ │  Lambda  │ ← Managed     │
│  └──────────┘ └──────────┘ └──────────┘               │
└─────────────────────────────────────────────────────────┘
```

### ECS Fargate Production (~$150/month)
```
┌─────────────────────────────────────────────────────────┐
│  AWS                                                    │
│                                                         │
│  Internet → ALB ─┬─→ Gateway (ECS)                     │
│                  ├─→ Auth (ECS)                        │
│                  ├─→ Platform (ECS)                    │
│                  └─→ Backend (ECS)                     │
│                           │                             │
│            ┌──────────────┴──────────────┐             │
│            ▼                             ▼             │
│     ┌──────────────┐            ┌──────────────┐       │
│     │ RDS Postgres │            │ ElastiCache  │       │
│     │ (Managed)    │            │ (Redis)      │       │
│     └──────────────┘            └──────────────┘       │
└─────────────────────────────────────────────────────────┘
```

---

## Database Strategy

| Environment | PostgreSQL | Redis |
|-------------|------------|-------|
| Local | Container | Container |
| Budget EC2 | Container | Container |
| ECS Fargate | **RDS** | **ElastiCache** |
| Kubernetes | Managed (RDS/CloudSQL) | Managed |

**Rule:** Production = Use managed services (RDS, ElastiCache)

---

## Terraform Module Status

| Module | Status | Description |
|--------|--------|-------------|
| `cognito-user-pool` | ✅ Done | Cognito with Lambda triggers |
| `cognito-pre-token-generation` | ✅ Done | Lambda for JWT enrichment |
| `ssm-parameters` | ✅ Done | SSM parameter store |
| `vpc` | ✅ Done | VPC, subnets, security groups |
| `rds` | ✅ Done | PostgreSQL database |
| `elasticache` | ✅ Done | Redis cluster |
| `ecr` | ✅ Done | Docker image registry |
| `ecs-cluster` | ✅ Done | ECS Fargate cluster |
| `ecs-service` | ✅ Done | Per-microservice deployment |
| `alb` | ✅ Done | Load balancer |
| `bastion` | ✅ Done | EC2 bastion for budget env |
| `amplify` | ✅ Done | Frontend hosting |

---

## Debugging AWS Deployments

### Budget Environment (EC2)

```bash
# SSH into EC2
ssh -i ~/.ssh/your-key.pem ec2-user@$(terraform output -raw bastion_public_ip)

# View service logs
cd /opt/app
docker-compose -f docker-compose.budget.yml logs -f

# Check service health
docker-compose -f docker-compose.budget.yml ps

# Restart a service
docker-compose -f docker-compose.budget.yml restart auth-service
```

### Production (ECS)

```bash
# View logs in CloudWatch
aws logs tail /ecs/cloud-infra-prod/auth-service --follow

# Check ECS service status
aws ecs describe-services --cluster cloud-infra-prod --services auth-service

# Force new deployment
aws ecs update-service --cluster cloud-infra-prod --service auth-service --force-new-deployment
```

### Lambda (Pre-Token Generation)

```bash
# View Lambda logs
aws logs tail /aws/lambda/cloud-infra-budget-pre-token-generation --follow

# Test Lambda (requires test event)
aws lambda invoke --function-name cloud-infra-budget-pre-token-generation \
  --payload file://test-event.json response.json
```

---

## Free Testing Options

| Tool | What It Does | Command |
|------|--------------|---------
| **Docker Compose** | Local dev | `docker-compose up` |
| **LocalStack** | AWS simulator | `localstack start` |
| **Minikube** | Local K8s | `minikube start` |
| **Kind** | K8s in Docker | `kind create cluster` |

No AWS costs for testing!

