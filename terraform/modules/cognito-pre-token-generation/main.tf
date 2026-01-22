# Lambda PreTokenGeneration Trigger for Cognito
# Injects selected tenant ID into JWT during authentication

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }
}

# Package Lambda function code
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.root}/lambdas/cognito-pre-token-generation"
  output_path = "${path.module}/lambda_function.zip"
  excludes    = ["test_handler.py", "requirements-test.txt", "README.md", "__pycache__"]
}

# Lambda function
resource "aws_lambda_function" "pre_token_generation" {
  function_name    = "${var.project_name}-${var.environment}-cognito-pre-token-gen"
  filename         = data.archive_file.lambda_zip.output_path
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256

  handler = "handler.lambda_handler"
  runtime = "python3.11"
  timeout = 10 # Increased for group sync

  role = aws_iam_role.lambda_exec.arn

  environment {
    variables = {
      ENVIRONMENT          = var.environment
      PLATFORM_SERVICE_URL = var.platform_service_url
      AUTH_SERVICE_URL     = var.auth_service_url
      ENABLE_GROUP_SYNC    = var.enable_group_sync ? "true" : "false"
    }
  }

  # VPC configuration (optional - enables private access to EC2)
  dynamic "vpc_config" {
    for_each = var.enable_vpc_mode ? [1] : []
    content {
      subnet_ids         = var.subnet_ids
      security_group_ids = [aws_security_group.lambda[0].id]
    }
  }

  tags = {
    Name        = "Cognito PreTokenGeneration Handler"
    Environment = var.environment
    ManagedBy   = "Terraform"
    Purpose     = "Multi-tenant login: inject selected tenantId and IdP groups into JWT"
  }
}

# IAM role for Lambda execution
resource "aws_iam_role" "lambda_exec" {
  name = "${var.project_name}-${var.environment}-lambda-pre-token-gen-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })

  tags = {
    Name        = "Lambda Cognito PreTokenGeneration Role"
    Environment = var.environment
  }
}

# IAM policy for Lambda - only needs logging (no Cognito writes)
resource "aws_iam_role_policy" "lambda_policy" {
  name = "${var.project_name}-${var.environment}-lambda-pre-token-gen-policy"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${var.aws_account_id}:*"
      }
    ]
  })
}

# Grant Cognito permission to invoke Lambda
resource "aws_lambda_permission" "cognito_invoke" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.pre_token_generation.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.user_pool_arn
}

# CloudWatch Log Group for Lambda
resource "aws_cloudwatch_log_group" "lambda_logs" {
  name              = "/aws/lambda/${aws_lambda_function.pre_token_generation.function_name}"
  retention_in_days = 14

  tags = {
    Name        = "Lambda PreTokenGeneration Logs"
    Environment = var.environment
  }
}

# =============================================================================
# VPC Resources (only created when vpc_id is provided)
# =============================================================================

# Security group for Lambda in VPC
resource "aws_security_group" "lambda" {
  count = var.enable_vpc_mode ? 1 : 0

  name        = "${var.project_name}-${var.environment}-lambda-pre-token-sg"
  description = "Security group for PreTokenGeneration Lambda (VPC mode)"
  vpc_id      = var.vpc_id

  # Allow all outbound (Lambda needs to call EC2 services)
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "${var.environment}-lambda-pre-token-sg"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# IAM policy attachment for VPC access
resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  count = var.enable_vpc_mode ? 1 : 0

  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}
