# Budget Environment - Main Configuration
# AWS deployment with RDS and ElastiCache (~$15-30/month)
# Uses Free Tier where possible

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0.0"
    }
  }

  # Uncomment for remote state
  # backend "s3" {
  #   bucket         = "your-terraform-state-bucket"
  #   key            = "budget/terraform.tfstate"
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
      CostCenter  = "Budget"
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
# VPC Module
# =============================================================================

module "vpc" {
  source = "../../modules/vpc"

  project_name = var.project_name
  environment  = var.environment

  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones

  # Budget: No NAT Gateway (EC2 in public subnet has direct internet access)
  enable_nat_gateway = false

  # Optional: Enable flow logs for debugging
  enable_flow_logs = var.enable_flow_logs
}

# =============================================================================
# RDS PostgreSQL (Free Tier: db.t3.micro)
# =============================================================================

module "rds" {
  source = "../../modules/rds"

  project_name = var.project_name
  environment  = var.environment

  vpc_id               = module.vpc.vpc_id
  db_subnet_group_name = module.vpc.db_subnet_group_name

  # Free Tier eligible!
  use_aurora     = false
  instance_class = "db.t3.micro"

  # Minimal storage
  allocated_storage     = 20
  max_allocated_storage = 0 # Disable autoscaling

  # Database settings
  database_name   = var.database_name
  master_username = var.database_username

  # Budget: Single-AZ, skip final snapshot
  multi_az            = false
  deletion_protection = false
  skip_final_snapshot = true

  # Allow from EC2 bastion
  allowed_security_groups = [module.bastion.security_group_id]
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

  # Single node - cheapest option
  node_type       = "cache.t3.micro"
  num_cache_nodes = 1

  # No snapshots for budget
  snapshot_retention_limit = 0

  # Allow from EC2 bastion
  allowed_security_groups = [module.bastion.security_group_id]
}

# =============================================================================
# Bastion/EC2 Host (runs services via Docker Compose)
# =============================================================================

module "bastion" {
  source = "../../modules/bastion"

  project_name = var.project_name
  environment  = var.environment

  vpc_id    = module.vpc.vpc_id
  subnet_id = module.vpc.public_subnet_ids[0]

  instance_type           = var.ec2_instance_type
  allowed_ssh_cidr_blocks = var.allowed_ssh_cidr_blocks
  ssh_public_key          = var.ssh_public_key

  # Use Elastic IP for consistent address
  create_eip = true
}

# =============================================================================
# Amplify (Frontend Hosting - Free Tier)
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
    ANGULAR_APP_API_URL = "http://${module.bastion.public_ip}:8080"
  }

  enable_auto_build = var.enable_amplify_auto_build
}

# =============================================================================
# SSM Parameters for Docker Compose Configuration
# =============================================================================

resource "aws_ssm_parameter" "ec2_public_ip" {
  name        = "/${var.project_name}/${var.environment}/ec2/public_ip"
  description = "EC2 instance public IP"
  type        = "String"
  value       = module.bastion.public_ip

  tags = { Module = "budget" }
}

resource "aws_ssm_parameter" "api_url" {
  name        = "/${var.project_name}/${var.environment}/api/url"
  description = "API URL for frontend"
  type        = "String"
  value       = "http://${module.bastion.public_ip}:8080"

  tags = { Module = "budget" }
}
