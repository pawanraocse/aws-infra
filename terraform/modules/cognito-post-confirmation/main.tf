# Lambda PostConfirmation Trigger for Cognito
# Sets custom attributes after email verification

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
  source_dir  = "${path.root}/lambdas/cognito-post-confirmation"
  output_path = "${path.module}/lambda_function.zip"
  excludes    = ["test_handler.py", "requirements-test.txt", "README.md", "__pycache__"]
}

# Lambda function
resource "aws_lambda_function" "post_confirmation" {
  function_name    = "${var.project_name}-${var.environment}-cognito-post-confirmation"
  filename         = data.archive_file.lambda_zip.output_path
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256

  handler = "index.lambda_handler"
  runtime = "python3.11"
  timeout = 10

  role = aws_iam_role.lambda_exec.arn

  environment {
    variables = {
      USER_POOL_ID = var.user_pool_id
      ENVIRONMENT  = var.environment
    }
  }

  tags = {
    Name        = "Cognito PostConfirmation Handler"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# IAM role for Lambda execution
resource "aws_iam_role" "lambda_exec" {
  name = "${var.project_name}-${var.environment}-lambda-cognito-post-confirm-role"

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
    Name        = "Lambda Cognito PostConfirmation Role"
    Environment = var.environment
  }
}

# IAM policy for Lambda to update Cognito users
resource "aws_iam_role_policy" "lambda_cognito_policy" {
  name = "${var.project_name}-${var.environment}-lambda-cognito-policy"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cognito-idp:AdminUpdateUserAttributes",
          "cognito-idp:AdminGetUser"
        ]
        Resource = var.user_pool_arn
      },
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
  function_name = aws_lambda_function.post_confirmation.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.user_pool_arn
}

# CloudWatch Log Group for Lambda
resource "aws_cloudwatch_log_group" "lambda_logs" {
  name              = "/aws/lambda/${aws_lambda_function.post_confirmation.function_name}"
  retention_in_days = 14

  tags = {
    Name        = "Lambda PostConfirmation Logs"
    Environment = var.environment
  }
}
