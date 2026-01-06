# Production Environment - Main Configuration
# Full AWS deployment with ECS Fargate, ALB, RDS, ElastiCache
# Estimated cost: ~$150/month

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0.0"
    }
  }

  # Remote state (recommended for production)
  # backend "s3" {
  #   bucket         = "your-terraform-state-bucket"
  #   key            = "production/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
      CostCenter  = "Production"
    }
  }
}

# =============================================================================
# Local Variables
# =============================================================================

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

# =============================================================================
# VPC
# =============================================================================

module "vpc" {
  source = "../../modules/vpc"

  project_name = var.project_name
  environment  = var.environment

  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones

  # Production: Enable NAT Gateway for private subnet internet access
  enable_nat_gateway = true
  single_nat_gateway = var.single_nat_gateway

  # Enable flow logs for security monitoring
  enable_flow_logs         = true
  flow_logs_retention_days = 30
}

# =============================================================================
# RDS PostgreSQL
# =============================================================================

module "rds" {
  source = "../../modules/rds"

  project_name = var.project_name
  environment  = var.environment

  vpc_id               = module.vpc.vpc_id
  db_subnet_group_name = module.vpc.db_subnet_group_name

  # Production settings
  use_aurora     = var.use_aurora
  instance_class = var.rds_instance_class

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage

  database_name   = var.database_name
  master_username = var.database_username

  # Production: Multi-AZ, deletion protection
  multi_az            = var.rds_multi_az
  deletion_protection = true
  skip_final_snapshot = false

  # Allow from private subnets (ECS services)
  allowed_cidr_blocks = module.vpc.private_subnet_cidrs
}

# =============================================================================
# ElastiCache Redis
# =============================================================================

module "elasticache" {
  source = "../../modules/elasticache"

  project_name = var.project_name
  environment  = var.environment

  vpc_id                        = module.vpc.vpc_id
  elasticache_subnet_group_name = module.vpc.elasticache_subnet_group_name

  # Production settings
  node_type          = var.redis_node_type
  num_cache_nodes    = var.redis_num_nodes
  automatic_failover = var.redis_num_nodes > 1

  snapshot_retention_limit = 7

  # Allow from private subnets (ECS services)
  allowed_cidr_blocks = module.vpc.private_subnet_cidrs
}

# =============================================================================
# ECR Repositories
# =============================================================================

module "ecr" {
  source = "../../modules/ecr"

  project_name = var.project_name
  environment  = var.environment

  services = [
    "gateway-service",
    "auth-service",
    "backend-service",
    "platform-service"
  ]

  max_image_count = 30
}

# =============================================================================
# ECS Cluster
# =============================================================================

module "ecs_cluster" {
  source = "../../modules/ecs-cluster"

  project_name = var.project_name
  environment  = var.environment

  vpc_id = module.vpc.vpc_id

  enable_container_insights = true
}

# =============================================================================
# Application Load Balancer
# =============================================================================

module "alb" {
  source = "../../modules/alb"

  project_name = var.project_name
  environment  = var.environment

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.public_subnet_ids

  certificate_arn = var.acm_certificate_arn

  # Target groups for services
  target_groups = {
    gateway = {
      port                 = 8080
      health_check_path    = "/actuator/health"
      health_check_matcher = "200"
      priority             = 100
      path_patterns        = ["/*"]
    }
  }
}

# =============================================================================
# ECS Services
# =============================================================================

module "gateway_service" {
  source = "../../modules/ecs-service"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  service_name = "gateway"
  cluster_id   = module.ecs_cluster.cluster_id
  cluster_name = module.ecs_cluster.cluster_name

  container_image = "${module.ecr.repository_urls["gateway-service"]}:latest"
  container_port  = 8080
  cpu             = 256
  memory          = 512
  desired_count   = 2

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnet_ids

  target_group_arn        = module.alb.target_group_arns["gateway"]
  allowed_security_groups = [module.alb.security_group_id]

  enable_autoscaling = true
  min_capacity       = 1
  max_capacity       = 4
  cpu_target         = 70

  environment_variables = {
    SPRING_APPLICATION_NAME = "gateway-service"
    SERVER_PORT             = "8080"
    EUREKA_CLIENT_ENABLED   = "false"
    REDIS_HOST              = module.elasticache.primary_endpoint
    REDIS_PORT              = "6379"
  }
}

module "auth_service" {
  source = "../../modules/ecs-service"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  service_name = "auth"
  cluster_id   = module.ecs_cluster.cluster_id
  cluster_name = module.ecs_cluster.cluster_name

  container_image = "${module.ecr.repository_urls["auth-service"]}:latest"
  container_port  = 8081
  cpu             = 256
  memory          = 512
  desired_count   = 2

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnet_ids

  allowed_security_groups = [module.alb.security_group_id]
  allowed_cidr_blocks     = module.vpc.private_subnet_cidrs

  enable_autoscaling = true
  min_capacity       = 1
  max_capacity       = 4
  cpu_target         = 70

  environment_variables = {
    SPRING_APPLICATION_NAME = "auth-service"
    SERVER_PORT             = "8081"
    EUREKA_CLIENT_ENABLED   = "false"
    SPRING_DATASOURCE_URL   = "jdbc:postgresql://${module.rds.endpoint}/${var.database_name}"
    REDIS_HOST              = module.elasticache.primary_endpoint
    REDIS_PORT              = "6379"
  }

  secrets = {
    SPRING_DATASOURCE_PASSWORD = module.rds.secret_arn
  }
}

module "backend_service" {
  source = "../../modules/ecs-service"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  service_name = "backend"
  cluster_id   = module.ecs_cluster.cluster_id
  cluster_name = module.ecs_cluster.cluster_name

  container_image = "${module.ecr.repository_urls["backend-service"]}:latest"
  container_port  = 8082
  cpu             = 256
  memory          = 512
  desired_count   = 2

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnet_ids

  allowed_security_groups = [module.alb.security_group_id]
  allowed_cidr_blocks     = module.vpc.private_subnet_cidrs

  enable_autoscaling = true
  min_capacity       = 1
  max_capacity       = 4
  cpu_target         = 70

  environment_variables = {
    SPRING_APPLICATION_NAME = "backend-service"
    SERVER_PORT             = "8082"
    EUREKA_CLIENT_ENABLED   = "false"
    SPRING_DATASOURCE_URL   = "jdbc:postgresql://${module.rds.endpoint}/${var.database_name}"
    REDIS_HOST              = module.elasticache.primary_endpoint
    REDIS_PORT              = "6379"
  }

  secrets = {
    SPRING_DATASOURCE_PASSWORD = module.rds.secret_arn
  }
}

module "platform_service" {
  source = "../../modules/ecs-service"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  service_name = "platform"
  cluster_id   = module.ecs_cluster.cluster_id
  cluster_name = module.ecs_cluster.cluster_name

  container_image = "${module.ecr.repository_urls["platform-service"]}:latest"
  container_port  = 8083
  cpu             = 256
  memory          = 512
  desired_count   = 2

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnet_ids

  allowed_security_groups = [module.alb.security_group_id]
  allowed_cidr_blocks     = module.vpc.private_subnet_cidrs

  enable_autoscaling = true
  min_capacity       = 1
  max_capacity       = 4
  cpu_target         = 70

  environment_variables = {
    SPRING_APPLICATION_NAME = "platform-service"
    SERVER_PORT             = "8083"
    EUREKA_CLIENT_ENABLED   = "false"
    SPRING_DATASOURCE_URL   = "jdbc:postgresql://${module.rds.endpoint}/${var.database_name}"
    REDIS_HOST              = module.elasticache.primary_endpoint
    REDIS_PORT              = "6379"
  }

  secrets = {
    SPRING_DATASOURCE_PASSWORD = module.rds.secret_arn
  }
}

# =============================================================================
# Amplify (Frontend)
# =============================================================================

module "amplify" {
  source = "../../modules/amplify"

  project_name = var.project_name
  environment  = var.environment

  repository_url      = var.frontend_repository_url
  github_access_token = var.github_access_token
  branch_name         = var.frontend_branch
  app_name            = "frontend"
  app_root            = var.frontend_app_root

  environment_variables = {
    ANGULAR_APP_API_URL = "https://${module.alb.alb_dns_name}"
  }

  enable_auto_build = true
}

# =============================================================================
# Bastion (Optional - for DB access)
# =============================================================================

module "bastion" {
  count  = var.create_bastion ? 1 : 0
  source = "../../modules/bastion"

  project_name = var.project_name
  environment  = var.environment

  vpc_id    = module.vpc.vpc_id
  subnet_id = module.vpc.public_subnet_ids[0]

  instance_type           = "t2.micro"
  allowed_ssh_cidr_blocks = var.bastion_allowed_ssh_cidrs
  ssh_public_key          = var.bastion_ssh_public_key

  create_eip = true
}
