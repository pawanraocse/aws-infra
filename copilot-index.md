# copilot-index.md

## Overview
This project provisions and manages a multi-tenant authentication service using AWS Cognito, with both native and federated (Google) authentication, automated via Terraform.

## Tech Stack
- AWS Cognito (User Pool, User Groups, User Pool Clients, Identity Providers)
- Terraform (infrastructure as code)
- Shell scripting (automation)
- (Planned) Spring Boot (Java 21+), Angular 18+, JUnit 5, Mockito, Testcontainers

## Entry Points
- `terraform/deploy.sh`: Automates provisioning of all AWS Cognito resources.
- `terraform/auth-cognito.tf`: Main Terraform configuration for Cognito.

## Modules/Folders
- `terraform/`: Infrastructure as code for AWS resources.
- `auth-service/`: (Planned) Spring Boot microservice for authentication.
- `backend-service/`: (Planned) Backend business logic and APIs.
- `frontend/`: (Planned) Angular frontend.

## Key Relationships & End-to-End Flows
- User registration and authentication via Cognito (native or Google).
- User pool clients for both native and federated flows.
- User groups for tenant isolation.
- All resources managed and versioned via Terraform.

## Critical APIs & Integration Points
- Cognito User Pool endpoints (native and federated clients).
- Google OAuth integration via Cognito.

## Naming Conventions
- Terraform: snake_case for resources, variables.
- Java: *Controller.java, *Service.java, *Repository.java, *Dto.java, *Spec.java, *Test.java
- Angular: *.service.ts, *.component.ts, *.spec.ts

## Test Coverage Map
- (Planned) Unit and integration tests for backend and frontend.
- (Planned) E2E tests for authentication flows.

## Auth/Security Approach
- AWS Cognito for authentication and user management.
- Google federated login via Cognito.
- Secrets managed via Terraform variables (recommend using env vars or secrets manager for production).

## Update Policy
- Update this index after any significant infrastructure, API, or flow change.
- Log all major actions in `action-auth-service.md`.

# Secret Management Policy

All sensitive configuration (OAuth client secrets, DB passwords, API keys, etc.) must be managed using AWS SSM Parameter Store.

- Never commit secrets to version control.
- Provide secrets to Terraform and applications via environment variables, SSM Parameter Store, or a gitignored .tfvars file for local/dev only.
- Example: To store a secret in SSM Parameter Store:

  aws ssm put-parameter --name "/auth-service/google_client_secret" --value "<secret>" --type "SecureString"

- Example: To retrieve a secret for local use:

  aws ssm get-parameter --name "/auth-service/google_client_secret" --with-decryption --query Parameter.Value --output text

- Document this policy in README.md and action-auth-service.md.

## AWS SSM Parameter Store Setup

### Storing Secrets
- Store secrets using AWS CLI:
  aws ssm put-parameter --name "/auth-service/google_client_id" --value "YOUR_GOOGLE_CLIENT_ID" --type "String"
  aws ssm put-parameter --name "/auth-service/google_client_secret" --value "YOUR_GOOGLE_CLIENT_SECRET" --type "SecureString"

### Retrieving Secrets
- For local dev, retrieve secrets:
  aws ssm get-parameter --name "/auth-service/google_client_secret" --with-decryption --query Parameter.Value --output text
- Use scripts/export-ssm-secrets.sh to export all required secrets as env vars.

### Terraform Integration Example
```
data "aws_ssm_parameter" "google_client_id" {
  name = "/auth-service/google_client_id"
}
data "aws_ssm_parameter" "google_client_secret" {
  name = "/auth-service/google_client_secret"
  with_decryption = true
}
```
- Reference in resources as: data.aws_ssm_parameter.google_client_id.value

### Security
- Never commit secrets to version control.
- Use SSM Parameter Store for all environments.
- See README.md for more details.
