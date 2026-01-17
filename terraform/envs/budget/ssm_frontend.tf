resource "aws_ssm_parameter" "frontend_url" {
  name        = "/${var.project_name}/${var.environment}/frontend/url"
  description = "Frontend application URL"
  type        = "String"
  value       = module.amplify.branch_url

  tags = { Module = "budget" }
}
