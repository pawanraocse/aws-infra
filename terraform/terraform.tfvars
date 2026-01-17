# terraform.tfvars - loads variables from the global .env for consistency

# AWS Configuration
aws_region   = "us-east-1"
project_name = "cloud-infra"
environment  = "dev"

# OAuth Configuration
callback_urls = [
  "http://localhost:8081/auth/login/oauth2/code/cognito",
  "http://localhost:3000/callback",
  "http://localhost:4200/auth/callback"
]

logout_urls = [
  "http://localhost:8081/auth/logged-out",
  "http://localhost:3000",
  "http://localhost:4200"
]

# Token Validity (adjust based on security requirements)
access_token_validity      = 60 # minutes
id_token_validity          = 60 # minutes
refresh_token_validity     = 30 # days
enable_google_social_login = true
