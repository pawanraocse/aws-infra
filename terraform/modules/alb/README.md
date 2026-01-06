# ALB Module

Application Load Balancer with HTTPS and path-based routing for microservices.

## Usage

```hcl
module "alb" {
  source = "../../modules/alb"

  project_name = "saas-factory"
  environment  = "production"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.public_subnet_ids

  # HTTPS with ACM certificate
  certificate_arn = aws_acm_certificate.main.arn

  # Path-based routing
  target_groups = {
    gateway = {
      port                 = 8080
      health_check_path    = "/actuator/health"
      health_check_matcher = "200"
      priority             = 100
      path_patterns        = ["/api/*"]
    }
    auth = {
      port                 = 8081
      health_check_path    = "/actuator/health"
      health_check_matcher = "200"
      priority             = 200
      path_patterns        = ["/auth/*"]
    }
  }
}
```

## Outputs

| Name | Description |
|------|-------------|
| `alb_dns_name` | ALB DNS name |
| `security_group_id` | ALB security group |
| `target_group_arns` | Map of service to target group ARN |
