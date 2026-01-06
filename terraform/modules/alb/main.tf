# ALB Module
# Application Load Balancer with HTTPS and path-based routing

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "alb"
  }

  alb_name = "${var.project_name}-${var.environment}"
}

# =============================================================================
# Security Group
# =============================================================================

resource "aws_security_group" "alb" {
  name        = "${local.alb_name}-alb-sg"
  description = "Security group for ALB"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.alb_name}-alb-sg"
  })
}

# =============================================================================
# Application Load Balancer
# =============================================================================

resource "aws_lb" "main" {
  name               = local.alb_name
  internal           = var.internal
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.subnet_ids

  enable_deletion_protection = var.deletion_protection

  access_logs {
    bucket  = var.access_logs_bucket
    prefix  = var.access_logs_prefix
    enabled = var.access_logs_bucket != null
  }

  tags = merge(local.common_tags, {
    Name = local.alb_name
  })
}

# =============================================================================
# HTTPS Listener (with ACM certificate)
# =============================================================================

resource "aws_lb_listener" "https" {
  count = var.certificate_arn != null ? 1 : 0

  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = var.ssl_policy
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.default.arn
  }

  tags = local.common_tags
}

# =============================================================================
# HTTP Listener (redirect to HTTPS or forward)
# =============================================================================

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = var.certificate_arn != null ? "redirect" : "forward"

    dynamic "redirect" {
      for_each = var.certificate_arn != null ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }

    target_group_arn = var.certificate_arn == null ? aws_lb_target_group.default.arn : null
  }

  tags = local.common_tags
}

# =============================================================================
# Default Target Group (for health checks/fallback)
# =============================================================================

resource "aws_lb_target_group" "default" {
  name        = "${local.alb_name}-default"
  port        = 80
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200-299"
    path                = var.default_health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = merge(local.common_tags, {
    Name = "${local.alb_name}-default"
  })
}

# =============================================================================
# Service Target Groups
# =============================================================================

resource "aws_lb_target_group" "services" {
  for_each = var.target_groups

  name        = "${local.alb_name}-${each.key}"
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = each.value.health_check_matcher
    path                = each.value.health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = merge(local.common_tags, {
    Name    = "${local.alb_name}-${each.key}"
    Service = each.key
  })
}

# =============================================================================
# Listener Rules (path-based routing)
# =============================================================================

resource "aws_lb_listener_rule" "services" {
  for_each = var.target_groups

  listener_arn = var.certificate_arn != null ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
  priority     = each.value.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.services[each.key].arn
  }

  condition {
    path_pattern {
      values = each.value.path_patterns
    }
  }

  tags = local.common_tags
}

# =============================================================================
# SSM Parameters
# =============================================================================

resource "aws_ssm_parameter" "alb_dns" {
  name        = "/${var.project_name}/${var.environment}/alb/dns_name"
  description = "ALB DNS name"
  type        = "String"
  value       = aws_lb.main.dns_name

  tags = local.common_tags
}

resource "aws_ssm_parameter" "alb_zone_id" {
  name        = "/${var.project_name}/${var.environment}/alb/zone_id"
  description = "ALB hosted zone ID (for Route53)"
  type        = "String"
  value       = aws_lb.main.zone_id

  tags = local.common_tags
}
