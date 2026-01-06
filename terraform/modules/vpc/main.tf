# VPC Module
# AWS VPC with public, private, and database subnets across multiple AZs
# Designed for production workloads with proper network isolation

locals {
  # Common tags for all resources
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "vpc"
  }

  # Calculate subnet CIDR blocks
  # /16 VPC split into /20 subnets (4096 IPs each)
  # Public:   10.0.0.0/20,  10.0.16.0/20,  10.0.32.0/20
  # Private:  10.0.48.0/20, 10.0.64.0/20,  10.0.80.0/20
  # Database: 10.0.96.0/20, 10.0.112.0/20, 10.0.128.0/20
  public_subnet_cidrs   = [for i, az in var.availability_zones : cidrsubnet(var.vpc_cidr, 4, i)]
  private_subnet_cidrs  = [for i, az in var.availability_zones : cidrsubnet(var.vpc_cidr, 4, i + length(var.availability_zones))]
  database_subnet_cidrs = [for i, az in var.availability_zones : cidrsubnet(var.vpc_cidr, 4, i + length(var.availability_zones) * 2)]
}

# =============================================================================
# VPC
# =============================================================================

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-vpc"
  })
}

# =============================================================================
# Internet Gateway (for public subnets)
# =============================================================================

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-igw"
  })
}

# =============================================================================
# Public Subnets
# =============================================================================

resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-public-${var.availability_zones[count.index]}"
    Tier = "Public"
  })
}

# Public Route Table
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-public-rt"
  })
}

# Associate public subnets with public route table
resource "aws_route_table_association" "public" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# =============================================================================
# NAT Gateway (for private subnet internet access)
# =============================================================================

# Elastic IP for NAT Gateway
resource "aws_eip" "nat" {
  count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : length(var.availability_zones)) : 0

  domain = "vpc"

  tags = merge(local.common_tags, {
    Name = var.single_nat_gateway ? "${var.project_name}-${var.environment}-nat-eip" : "${var.project_name}-${var.environment}-nat-eip-${var.availability_zones[count.index]}"
  })

  depends_on = [aws_internet_gateway.main]
}

# NAT Gateway
resource "aws_nat_gateway" "main" {
  count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : length(var.availability_zones)) : 0

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(local.common_tags, {
    Name = var.single_nat_gateway ? "${var.project_name}-${var.environment}-nat" : "${var.project_name}-${var.environment}-nat-${var.availability_zones[count.index]}"
  })

  depends_on = [aws_internet_gateway.main]
}

# =============================================================================
# Private Subnets (for ECS tasks, internal services)
# =============================================================================

resource "aws_subnet" "private" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.private_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-private-${var.availability_zones[count.index]}"
    Tier = "Private"
  })
}

# Private Route Tables
resource "aws_route_table" "private" {
  count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : length(var.availability_zones)) : 1

  vpc_id = aws_vpc.main.id

  # Route to NAT Gateway if enabled
  dynamic "route" {
    for_each = var.enable_nat_gateway ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = var.single_nat_gateway ? aws_nat_gateway.main[0].id : aws_nat_gateway.main[count.index].id
    }
  }

  tags = merge(local.common_tags, {
    Name = var.single_nat_gateway ? "${var.project_name}-${var.environment}-private-rt" : "${var.project_name}-${var.environment}-private-rt-${count.index + 1}"
  })
}

# Associate private subnets with private route table
resource "aws_route_table_association" "private" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = var.single_nat_gateway || !var.enable_nat_gateway ? aws_route_table.private[0].id : aws_route_table.private[count.index].id
}

# =============================================================================
# Database Subnets (isolated - no internet access)
# =============================================================================

resource "aws_subnet" "database" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.database_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-database-${var.availability_zones[count.index]}"
    Tier = "Database"
  })
}

# Database Route Table (no internet route - isolated)
resource "aws_route_table" "database" {
  vpc_id = aws_vpc.main.id

  # No routes - completely isolated
  # Only VPC internal traffic allowed

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-database-rt"
  })
}

# Associate database subnets with database route table
resource "aws_route_table_association" "database" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}

# Database Subnet Group (for RDS)
resource "aws_db_subnet_group" "database" {
  name        = "${var.project_name}-${var.environment}-db-subnet-group"
  description = "Database subnet group for ${var.project_name}-${var.environment}"
  subnet_ids  = aws_subnet.database[*].id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-db-subnet-group"
  })
}

# ElastiCache Subnet Group
resource "aws_elasticache_subnet_group" "cache" {
  name        = "${var.project_name}-${var.environment}-cache-subnet-group"
  description = "ElastiCache subnet group for ${var.project_name}-${var.environment}"
  subnet_ids  = aws_subnet.database[*].id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-cache-subnet-group"
  })
}

# =============================================================================
# VPC Flow Logs (optional - for debugging and security)
# =============================================================================

resource "aws_flow_log" "main" {
  count = var.enable_flow_logs ? 1 : 0

  vpc_id                   = aws_vpc.main.id
  traffic_type             = "ALL"
  log_destination_type     = "cloud-watch-logs"
  log_destination          = aws_cloudwatch_log_group.flow_logs[0].arn
  iam_role_arn             = aws_iam_role.flow_logs[0].arn
  max_aggregation_interval = 60

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-flow-logs"
  })
}

resource "aws_cloudwatch_log_group" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name              = "/aws/vpc/${var.project_name}-${var.environment}-flow-logs"
  retention_in_days = var.flow_logs_retention_days

  tags = local.common_tags
}

resource "aws_iam_role" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name = "${var.project_name}-${var.environment}-flow-logs-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "vpc-flow-logs.amazonaws.com"
        }
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name = "${var.project_name}-${var.environment}-flow-logs-policy"
  role = aws_iam_role.flow_logs[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogGroups",
          "logs:DescribeLogStreams"
        ]
        Effect   = "Allow"
        Resource = "*"
      }
    ]
  })
}
