# ECR Module

ECR repositories for Docker images with lifecycle policies.

## Usage

```hcl
module "ecr" {
  source = "../../modules/ecr"

  project_name = "saas-factory"
  environment  = "production"

  # Optional: Override default services
  services = [
    "gateway-service",
    "auth-service",
    "platform-service",
    "backend-service",
    "eureka-server"
  ]

  # Keep last 30 images
  max_image_count = 30
  
  # Scan for vulnerabilities
  scan_on_push = true
}
```

## Push Images

```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com

# Build and push
docker build -t saas-factory/gateway-service .
docker tag saas-factory/gateway-service:latest ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/saas-factory/gateway-service:latest
docker push ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/saas-factory/gateway-service:latest
```

## Outputs

| Name | Description |
|------|-------------|
| `repository_urls` | Map of service name to ECR URL |
| `repository_arns` | Map of service name to ARN |
