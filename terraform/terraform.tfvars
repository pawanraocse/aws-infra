# terraform.tfvars - loads variables from the global .env for consistency

# AWS Configuration
aws_region   = "us-east-1"
project_name = "clone-app"
environment  = "dev"

# OAuth Configuration
callback_urls = [
  "http://localhost:8080/login/oauth2/code/cognito",
  "http://localhost:3000/callback"
]

logout_urls = [
  "http://localhost:8080/logged-out",
  "http://localhost:3000"
]

# Token Validity (adjust based on security requirements)
access_token_validity  = 60 # minutes
id_token_validity      = 60 # minutes
refresh_token_validity = 30 # days
