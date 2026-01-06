# RDS Module
# PostgreSQL or Aurora PostgreSQL database with Secrets Manager integration
# Designed for production workloads with configurable engine choice

locals {
  # Common tags for all resources
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "rds"
  }

  # Database identifier
  db_identifier = "${var.project_name}-${var.environment}"

  # Engine configuration
  is_aurora = var.use_aurora

  # Final connection info
  db_endpoint = local.is_aurora ? aws_rds_cluster.aurora[0].endpoint : aws_db_instance.postgres[0].address
  db_port     = local.is_aurora ? aws_rds_cluster.aurora[0].port : aws_db_instance.postgres[0].port
}

# =============================================================================
# Security Group
# =============================================================================

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-rds-sg"
  description = "Security group for RDS database"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from application"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = var.allowed_security_groups
    cidr_blocks     = var.allowed_cidr_blocks
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-rds-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

# =============================================================================
# Secrets Manager - Database Credentials
# =============================================================================

resource "random_password" "master" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.project_name}/${var.environment}/rds-credentials"
  description = "RDS database credentials for ${var.project_name}-${var.environment}"

  recovery_window_in_days = var.environment == "prod" || var.environment == "production" ? 30 : 0

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.master_username
    password = random_password.master.result
    host     = local.db_endpoint
    port     = 5432
    database = var.database_name
    engine   = local.is_aurora ? "aurora-postgresql" : "postgres"
  })
}

# =============================================================================
# RDS PostgreSQL Instance (Standard)
# =============================================================================

resource "aws_db_instance" "postgres" {
  count = local.is_aurora ? 0 : 1

  identifier = local.db_identifier

  # Engine
  engine               = "postgres"
  engine_version       = var.postgres_version
  instance_class       = var.instance_class
  parameter_group_name = aws_db_parameter_group.postgres[0].name

  # Storage
  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = var.storage_type
  storage_encrypted     = true
  kms_key_id            = var.kms_key_id

  # Database
  db_name  = var.database_name
  username = var.master_username
  password = random_password.master.result
  port     = 5432

  # Network
  db_subnet_group_name   = var.db_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = var.multi_az

  # Backup
  backup_retention_period = var.backup_retention_period
  backup_window           = var.backup_window
  maintenance_window      = var.maintenance_window

  # Monitoring
  performance_insights_enabled          = var.enable_performance_insights
  performance_insights_retention_period = var.enable_performance_insights ? 7 : null
  enabled_cloudwatch_logs_exports       = var.cloudwatch_logs_exports

  # Deletion protection
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${local.db_identifier}-final-snapshot"

  # Apply changes immediately in non-prod
  apply_immediately = var.environment != "prod" && var.environment != "production"

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-postgres"
  })

  lifecycle {
    ignore_changes = [password]
  }
}

resource "aws_db_parameter_group" "postgres" {
  count = local.is_aurora ? 0 : 1

  name        = "${var.project_name}-${var.environment}-postgres-params"
  family      = "postgres${split(".", var.postgres_version)[0]}"
  description = "PostgreSQL parameter group for ${var.project_name}-${var.environment}"

  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # Log queries > 1 second
  }

  tags = local.common_tags
}

# =============================================================================
# Aurora PostgreSQL Cluster
# =============================================================================

resource "aws_rds_cluster" "aurora" {
  count = local.is_aurora ? 1 : 0

  cluster_identifier = "${local.db_identifier}-aurora"

  # Engine
  engine         = "aurora-postgresql"
  engine_mode    = var.aurora_serverless ? "provisioned" : "provisioned"
  engine_version = var.aurora_postgres_version

  # Database
  database_name   = var.database_name
  master_username = var.master_username
  master_password = random_password.master.result
  port            = 5432

  # Network
  db_subnet_group_name   = var.db_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # Storage
  storage_encrypted = true
  kms_key_id        = var.kms_key_id

  # Backup
  backup_retention_period = var.backup_retention_period
  preferred_backup_window = var.backup_window

  # Serverless v2 scaling (if enabled)
  dynamic "serverlessv2_scaling_configuration" {
    for_each = var.aurora_serverless ? [1] : []
    content {
      min_capacity = var.aurora_min_capacity
      max_capacity = var.aurora_max_capacity
    }
  }

  # Deletion protection
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${local.db_identifier}-aurora-final-snapshot"

  # Apply changes immediately in non-prod
  apply_immediately = var.environment != "prod" && var.environment != "production"

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-aurora-cluster"
  })

  lifecycle {
    ignore_changes = [master_password]
  }
}

resource "aws_rds_cluster_instance" "aurora" {
  count = local.is_aurora ? var.aurora_instance_count : 0

  identifier         = "${local.db_identifier}-aurora-${count.index + 1}"
  cluster_identifier = aws_rds_cluster.aurora[0].id

  # Engine
  engine         = aws_rds_cluster.aurora[0].engine
  engine_version = aws_rds_cluster.aurora[0].engine_version

  # Instance
  instance_class = var.aurora_serverless ? "db.serverless" : var.aurora_instance_class

  # Monitoring
  performance_insights_enabled          = var.enable_performance_insights
  performance_insights_retention_period = var.enable_performance_insights ? 7 : null

  # Network
  publicly_accessible = false

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-aurora-${count.index + 1}"
  })
}

# =============================================================================
# SSM Parameters for Service Discovery
# =============================================================================

resource "aws_ssm_parameter" "db_endpoint" {
  name        = "/${var.project_name}/${var.environment}/rds/endpoint"
  description = "RDS database endpoint"
  type        = "String"
  value       = local.db_endpoint

  tags = local.common_tags
}

resource "aws_ssm_parameter" "db_port" {
  name        = "/${var.project_name}/${var.environment}/rds/port"
  description = "RDS database port"
  type        = "String"
  value       = "5432"

  tags = local.common_tags
}

resource "aws_ssm_parameter" "db_name" {
  name        = "/${var.project_name}/${var.environment}/rds/database"
  description = "RDS database name"
  type        = "String"
  value       = var.database_name

  tags = local.common_tags
}

resource "aws_ssm_parameter" "db_username" {
  name        = "/${var.project_name}/${var.environment}/rds/username"
  description = "RDS database username"
  type        = "String"
  value       = var.master_username

  tags = local.common_tags
}

# Password stored in Secrets Manager, but reference path in SSM
resource "aws_ssm_parameter" "db_secret_arn" {
  name        = "/${var.project_name}/${var.environment}/rds/secret_arn"
  description = "ARN of the Secrets Manager secret containing DB credentials"
  type        = "String"
  value       = aws_secretsmanager_secret.db_credentials.arn

  tags = local.common_tags
}
