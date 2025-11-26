
# ============================================================================
# Lambda Function for Pre-Token Generation Trigger
# ============================================================================

# IAM Role for Lambda Execution
resource "aws_iam_role" "lambda_pre_token" {
  name = "${var.project_name}-${var.environment}-lambda-pre-token-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.project_name}-${var.environment}-lambda-pre-token-role"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Attach AWS managed policy for CloudWatch Logs (free tier)
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_pre_token.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Archive Lambda function code
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/pre-token-generation"
  output_path = "${path.module}/lambda/pre-token-generation.zip"
}

# Lambda Function
resource "aws_lambda_function" "pre_token_generation" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = "${var.project_name}-${var.environment}-pre-token-generation"
  role             = aws_iam_role.lambda_pre_token.arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  runtime          = "nodejs22.x"
  timeout          = 3
  memory_size      = 128

  # No environment variables needed - reads from event

  tags = {
    Name        = "${var.project_name}-${var.environment}-pre-token-generation"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_logs
  ]
}

# Lambda Permission for Cognito to Invoke
resource "aws_lambda_permission" "cognito_invoke" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.pre_token_generation.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.main.arn
}

# Update Lambda trigger to V2_0 for access token customization
# NOTE: Terraform doesn't support PreTokenGenerationConfig.LambdaVersion parameter
# so we use null_resource with AWS CLI as a workaround
resource "null_resource" "update_lambda_trigger_version" {
  # This resource runs AWS CLI to update the Lambda trigger version to V2_0
  # which enables access token customization (not just ID token)
  # IMPORTANT: Must include auto-verified-attributes to prevent AWS from clearing it

  provisioner "local-exec" {
    command = <<-EOT
      aws cognito-idp update-user-pool \
        --user-pool-id ${aws_cognito_user_pool.main.id} \
        --auto-verified-attributes email \
        --lambda-config "PreTokenGenerationConfig={LambdaVersion=V2_0,LambdaArn=${aws_lambda_function.pre_token_generation.arn}}" \
        --profile ${var.aws_profile} \
        --region ${var.aws_region}
    EOT
  }

  depends_on = [
    aws_cognito_user_pool.main,
    aws_lambda_function.pre_token_generation,
    aws_lambda_permission.cognito_invoke
  ]

  # Trigger re-run if any of these values change
  triggers = {
    user_pool_id = aws_cognito_user_pool.main.id
    lambda_arn   = aws_lambda_function.pre_token_generation.arn
  }
}

# ============================================================================
# Lambda Function for Post-Confirmation Trigger
# ============================================================================

# Archive post-confirmation Lambda code
data "archive_file" "post_confirmation_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/post-confirmation"
  output_path = "${path.module}/lambda/post-confirmation.zip"
}

# Lambda Function for Post-Confirmation
resource "aws_lambda_function" "post_confirmation" {
  filename         = data.archive_file.post_confirmation_zip.output_path
  function_name    = "${var.project_name}-${var.environment}-post-confirmation"
  role             = aws_iam_role.lambda_pre_token.arn # Reuse same role
  handler          = "index.handler"
  source_code_hash = data.archive_file.post_confirmation_zip.output_base64sha256
  runtime          = "nodejs22.x"
  timeout          = 10 # Longer timeout for API call
  memory_size      = 128

  environment {
    variables = {
      PLATFORM_SERVICE_URL = var.platform_service_url
    }
  }

  tags = {
    Name        = "${var.project_name}-${var.environment}-post-confirmation"
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_logs
  ]
}

# Lambda Permission for Cognito to Invoke Post-Confirmation
resource "aws_lambda_permission" "cognito_post_confirmation" {
  statement_id  = "AllowCognitoPostConfirmation"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.post_confirmation.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.main.arn
}
