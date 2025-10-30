# Action Plan: Implementing Multi-Tenant Auth Service with AWS Cognito

## Overview
This document outlines the step-by-step actions required to design, implement, and deliver a production-ready, multi-tenant authentication service (auth-service) using AWS Cognito, as per the updated requirements. The plan ensures support for both federated SSO and username/password login, strict tenant context enforcement, and robust security and auditability. The auth-service is a dedicated microservice responsible for authentication and authorization across all enterprise applications, leveraging AWS Cognito's shared user pool and Cognito groups for multi-tenancy.

---

## 1. Project Setup
- [x] Create a new Spring Boot project (Java 21+, Spring Boot 3.x) named `auth-service` for multi-tenant authentication and authorization.
- [x] Set up Maven/Gradle with required dependencies (see requirements).
- [x] Initialize Git repository and configure code quality tools (Checkstyle, SpotBugs, JaCoCo).

## 2. AWS Cognito Configuration
- [x] Create a shared Cognito User Pool for all tenants.
- [x] For each tenant, create a Cognito group (e.g., `tenant-abc`, `tenant-xyz`).
- [x] Assign users to their respective tenant group during onboarding.
- [x] Configure Cognito App Clients for:
    - Native username/password login
    - Federated SSO (SAML, OIDC, social IdPs)
    - **Allowed callback URLs:**
        - `http://localhost:8081/api/v1/auth/callback` (local dev)
        - `https://your-frontend-domain.com/auth/callback` (production)
    - **Allowed sign-out URLs:**
        - `http://localhost:8081/logout` (local dev)
        - `https://your-frontend-domain.com/logout` (production)
- [x] Ensure the `cognito:groups` claim is present in JWTs and used for tenant context enforcement in the backend.
- [x] Document onboarding flows for both SSO and native login tenants, including group assignment.

## 3. Spring Security & OAuth2 Integration

### 3.1 Dependencies & Configuration
- Add Spring Security, OAuth2 Resource Server, and JWT dependencies to `pom.xml`.
- Configure `application.properties` for Cognito issuer URI, JWK set URI, and audience.

### 3.2 Security Configuration
- Create `SecurityConfig` class:
  - Enable OAuth2 Resource Server with JWT.
  - Configure JWT decoder to validate tokens from Cognito.
  - Map Cognito claims (`sub` → `userId`, `cognito:groups` → tenant/roles).
  - Set up stateless session management.
  - Restrict endpoints by roles/authorities as needed.

### 3.3 JWT Claim Extraction & Context Propagation
- Implement a custom `JwtAuthenticationConverter` to extract `userId` and tenant group from JWT.
- Store tenant context in a request-scoped bean or `SecurityContext` for downstream access.

### 3.4 Tenant Context Enforcement
- Implement a filter or interceptor:
  - On each request, extract tenant group from `SecurityContext`.
  - Validate that the tenant group is present and matches required context.
  - Reject requests with missing/invalid tenant context.

### 3.5 RBAC Enforcement
- Map Cognito groups to Spring Security roles/authorities.
- Use `@PreAuthorize` or method security for fine-grained access control.

### 3.6 Structured Logging
- Implement a logging filter/interceptor to log `userId`, tenant group, `requestId`, and operation for each request.

### 3.7 Exception Handling
- Implement a global exception handler to wrap security/auth errors into domain-specific exceptions and return appropriate responses.

### 3.8 Testing
- Unit test: `SecurityConfig`, `JwtAuthenticationConverter`, tenant context filter.
- Integration test: API endpoints with valid/invalid JWTs, tenant context enforcement.

**Artifacts to be created/modified:**
- `pom.xml` (dependencies)
- `src/main/resources/application.properties` (Cognito config)
- `src/main/java/com/learning/authservice/config/SecurityConfig.java`
- `src/main/java/com/learning/authservice/security/JwtAuthenticationConverter.java`
- `src/main/java/com/learning/authservice/security/TenantContextFilter.java`
- `src/main/java/com/learning/authservice/logging/RequestLoggingFilter.java`
- `src/main/java/com/learning/authservice/exception/GlobalExceptionHandler.java`
- Corresponding test classes under `src/test/java`

## 4. API Design & Implementation
- [ ] Define and implement authentication endpoints:
    - `/api/v1/auth/authorize` (initiate OAuth2 flow)
    - `/api/v1/auth/callback` (handle OAuth2 callback)
    - `/api/v1/auth/login` (redirect to Cognito Hosted UI)
    - `/api/v1/auth/logout` (token revocation)
- [ ] Ensure all endpoints validate and propagate tenant context using the group claim.
- [ ] Use DTOs for all external communication (never expose entities).
- [ ] Add structured logging (tenant group, userId, requestId, operation).

## 5. Service & Repository Layer
- [ ] Implement service layer for authentication/session logic, enforcing tenant boundaries using group membership.
- [ ] Implement repository layer for any local persistence (e.g., audit logs, sessions), enforcing tenant boundaries.

## 6. Security, Validation, and Auditing
- [ ] Add input validation for all endpoints.
- [ ] Implement structured, secure logging (SLF4J + Logback).
- [ ] Ensure all logs and audit events include tenant group, userId, requestId, and operation.
- [ ] Handle and wrap exceptions into domain-specific errors.

## 7. Testing
- [ ] Write unit tests for controllers, services, and utilities (JUnit 5, Mockito, AssertJ).
- [ ] Write integration tests for API and Cognito flows (Testcontainers, MockWebServer).
- [ ] Add contract tests for API endpoints.

## 8. Infrastructure & Deployment
- [ ] Create Dockerfile (multi-stage, non-root user).
- [ ] Write Helm chart for Kubernetes deployment (resources, probes, env vars, secrets).
- [ ] Add Terraform modules for Cognito, IAM, and supporting AWS resources.
- [ ] Configure GitHub Actions for CI/CD (build, test, deploy).

## 9. Documentation & Deliverables
- [ ] Update README with setup, configuration, and usage instructions.
- [ ] Document onboarding for new tenants (SSO and native login), including group assignment.
- [ ] Ensure copilot-index.md and requirements.md are up to date.

---

# Auth Service Action Log

## 2025-10-23: Cognito Infrastructure Provisioned via Terraform

- **Provisioned AWS Cognito User Pool**: Multi-tenant pool with email as username and auto-verified attribute.
- **User Groups**: Created `tenant-abc` and `tenant-xyz` groups for multi-tenancy.
- **User Pool Clients**:
  - `native-client`: For standard username/password authentication.
  - `federated-client`: Supports both Cognito and Google as identity providers.
- **Google Identity Provider**: Configured using Terraform with variables for client ID and secret. Used `depends_on` to ensure correct resource creation order.
- **Automation**: All resources provisioned using `deploy.sh` script, which sets the AWS profile and runs Terraform commands.
- **Security**: Google client secret is managed via Terraform variables (recommend using environment variables or a secrets manager for production).
- **Cleanup**: Resources can be destroyed with `terraform destroy` or a destroy script.

## Next Steps
- Integrate Cognito client IDs into backend and frontend services.
- Test both native and Google authentication flows.
- Update documentation and index as needed.

---

## Next Steps
- Review and approve this action plan.
- Execute each step, updating this file with progress and notes.
- Use this as the single source of truth for auth-service implementation.

---

# Secrets Management

- All secrets (OAuth client secrets, DB passwords, etc.) must be managed via AWS SSM Parameter Store. Never commit secrets to version control. See copilot-index.md for usage instructions.
