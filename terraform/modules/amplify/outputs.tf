# Amplify Module - Outputs

output "app_id" {
  description = "Amplify app ID"
  value       = aws_amplify_app.frontend.id
}

output "app_arn" {
  description = "Amplify app ARN"
  value       = aws_amplify_app.frontend.arn
}

output "default_domain" {
  description = "Default Amplify domain"
  value       = aws_amplify_app.frontend.default_domain
}

output "branch_url" {
  description = "Branch-specific URL"
  value       = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.frontend.default_domain}"
}

output "custom_domain" {
  description = "Custom domain URL (if configured)"
  value       = var.domain_name != null ? "https://${var.subdomain_prefix}${var.subdomain_prefix != "" ? "." : ""}${var.domain_name}" : null
}

output "ssm_url_path" {
  description = "SSM parameter path for Amplify URL"
  value       = aws_ssm_parameter.amplify_url.name
}
