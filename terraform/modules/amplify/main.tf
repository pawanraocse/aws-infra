# Amplify Module
# AWS Amplify for Angular frontend hosting with CI/CD

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "amplify"
  }

  app_name = "${var.project_name}-${var.environment}"
}

# =============================================================================
# Amplify App
# =============================================================================

resource "aws_amplify_app" "frontend" {
  name       = local.app_name
  repository = var.repository_url

  # GitHub access token for repository access
  access_token = var.github_access_token

  # Build settings
  build_spec = var.build_spec != null ? var.build_spec : <<-EOT
    version: 1
    frontend:
      phases:
        preBuild:
          commands:
            - npm ci
        build:
          commands:
            - npm run build
      artifacts:
        baseDirectory: dist/${var.app_name}
        files:
          - '**/*'
      cache:
        paths:
          - node_modules/**/*
  EOT

  # Environment variables
  environment_variables = merge(
    {
      AMPLIFY_DIFF_DEPLOY = "false"
    },
    var.app_root != null && var.app_root != "" ? { AMPLIFY_MONOREPO_APP_ROOT = var.app_root } : {},
    var.environment_variables
  )

  # Custom rules (SPA routing)
  custom_rule {
    source = "/<*>"
    status = "404"
    target = "/index.html"
  }

  custom_rule {
    source = "</^[^.]+$|\\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json)$)([^.]+$)/>"
    status = "200"
    target = "/index.html"
  }

  # Platform settings
  platform = "WEB"

  tags = merge(local.common_tags, {
    Name = local.app_name
  })
}

# =============================================================================
# Branch Configuration
# =============================================================================

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.frontend.id
  branch_name = var.branch_name

  # Auto-build on push
  enable_auto_build = var.enable_auto_build

  # Framework detection
  framework = "Angular - SSR"

  # Branch-specific environment variables
  environment_variables = var.branch_environment_variables

  # Enable preview for PRs
  enable_pull_request_preview = var.enable_pr_preview

  tags = local.common_tags
}

# =============================================================================
# Domain Association (optional)
# =============================================================================

resource "aws_amplify_domain_association" "main" {
  count = var.domain_name != null ? 1 : 0

  app_id      = aws_amplify_app.frontend.id
  domain_name = var.domain_name

  sub_domain {
    branch_name = aws_amplify_branch.main.branch_name
    prefix      = var.subdomain_prefix
  }

  # Wait for certificate validation
  wait_for_verification = var.wait_for_verification
}

# =============================================================================
# SSM Parameters
# =============================================================================

resource "aws_ssm_parameter" "amplify_url" {
  name        = "/${var.project_name}/${var.environment}/amplify/url"
  description = "Amplify app URL"
  type        = "String"
  value       = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.frontend.default_domain}"

  tags = local.common_tags
}

resource "aws_ssm_parameter" "amplify_app_id" {
  name        = "/${var.project_name}/${var.environment}/amplify/app_id"
  description = "Amplify app ID"
  type        = "String"
  value       = aws_amplify_app.frontend.id

  tags = local.common_tags
}
