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
  function_name    = "${var.environment}-cognito-pre-token-generation"
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

  tags = {
    Name        = "Cognito PreTokenGeneration Handler"
    Environment = var.environment
    ManagedBy   = "Terraform"
    Purpose     = "Multi-tenant login: inject selected tenantId and IdP groups into JWT"
  }
}

# IAM role for Lambda execution
resource "aws_iam_role" "lambda_exec" {
  name = "${var.environment}-lambda-cognito-pre-token-gen-role"

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
  name = "${var.environment}-lambda-pre-token-gen-policy"
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
