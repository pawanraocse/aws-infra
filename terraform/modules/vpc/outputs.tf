# VPC Module - Outputs
# All values exported for use by other modules

# =============================================================================
# VPC Outputs
# =============================================================================

output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = aws_vpc.main.cidr_block
}

output "vpc_arn" {
  description = "ARN of the VPC"
  value       = aws_vpc.main.arn
}

# =============================================================================
# Subnet Outputs
# =============================================================================

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "public_subnet_cidrs" {
  description = "List of public subnet CIDR blocks"
  value       = aws_subnet.public[*].cidr_block
}

output "private_subnet_ids" {
  description = "List of private subnet IDs (for ECS tasks)"
  value       = aws_subnet.private[*].id
}

output "private_subnet_cidrs" {
  description = "List of private subnet CIDR blocks"
  value       = aws_subnet.private[*].cidr_block
}

output "database_subnet_ids" {
  description = "List of database subnet IDs (for RDS, ElastiCache)"
  value       = aws_subnet.database[*].id
}

output "database_subnet_cidrs" {
  description = "List of database subnet CIDR blocks"
  value       = aws_subnet.database[*].cidr_block
}

# =============================================================================
# Subnet Groups
# =============================================================================

output "db_subnet_group_name" {
  description = "Name of the RDS database subnet group"
  value       = aws_db_subnet_group.database.name
}

output "db_subnet_group_arn" {
  description = "ARN of the RDS database subnet group"
  value       = aws_db_subnet_group.database.arn
}

output "elasticache_subnet_group_name" {
  description = "Name of the ElastiCache subnet group"
  value       = aws_elasticache_subnet_group.cache.name
}

# =============================================================================
# Gateway Outputs
# =============================================================================

output "internet_gateway_id" {
  description = "ID of the Internet Gateway"
  value       = aws_internet_gateway.main.id
}

output "nat_gateway_ids" {
  description = "List of NAT Gateway IDs"
  value       = aws_nat_gateway.main[*].id
}

output "nat_gateway_public_ips" {
  description = "List of NAT Gateway public IPs"
  value       = aws_eip.nat[*].public_ip
}

# =============================================================================
# Route Table Outputs
# =============================================================================

output "public_route_table_id" {
  description = "ID of the public route table"
  value       = aws_route_table.public.id
}

output "private_route_table_ids" {
  description = "List of private route table IDs"
  value       = aws_route_table.private[*].id
}

output "database_route_table_id" {
  description = "ID of the database (isolated) route table"
  value       = aws_route_table.database.id
}

# =============================================================================
# Availability Zone Outputs
# =============================================================================

output "availability_zones" {
  description = "List of availability zones used"
  value       = var.availability_zones
}

output "availability_zone_count" {
  description = "Number of availability zones"
  value       = length(var.availability_zones)
}
