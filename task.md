# task.md – Production-Ready Implementation Tasks

This file breaks the PRD into independent, detailed tasks for robust, scalable, and secure AWS-based Spring Boot microservice development. Each task is self-contained and includes all necessary details for implementation, edge cases, and clarifying questions.

---

## 1. Repository & Project Bootstrap
- Create the prescribed directory structure:
  - src/main/java, src/main/resources, src/test/java, infra/, helm/, terraform/, scripts/
- Add .gitignore with standard Java, Maven/Gradle, IDE, and OS exclusions.
- Create README.md with project overview, setup instructions, and tech stack.
- Create copilot-index.md with architecture, modules, entry points, flows, APIs, naming conventions, and test coverage.
- Initialize Maven/Gradle project:
  - Set Java 21 as source/target.
  - Add dependencies: Spring Boot Starter Web, JPA, Flyway, Security, Actuator, Micrometer, OpenTelemetry, Testcontainers, Mockito, JUnit 5, springdoc-openapi.
- Verify build with `mvn clean install` or `./gradlew build`.
- Make a semantic initial commit (e.g., `feat: initial project structure and build setup`).
- Document any build tool version requirements in README.
- Validate that all folders/files exist and are tracked.
- Run static analysis (e.g., Checkstyle, SpotBugs) and ensure no critical issues.
- DoD:
  - All folders/files present and tracked in VCS.
  - Build passes with no errors.
  - Static analysis/linting passes.
  - Initial documentation (README, copilot-index.md) is present.

## 2. API Contract & Swagger/OpenAPI Integration
- Add springdoc-openapi dependency to Maven/Gradle.
- Create OpenAPI v3 spec for all planned endpoints:
  - Define paths, HTTP methods, request/response schemas, status codes, error models.
  - Include examples for each endpoint.
- Annotate controllers and DTOs with @Operation, @ApiResponse, @Parameter, etc.
- Configure Swagger UI endpoint at `/swagger-ui.html` and OpenAPI spec at `/v3/api-docs`.
- Document how to access and use Swagger UI in README.
- Validate that all endpoints are documented and match implementation.
- Add automated test to verify OpenAPI spec is generated and accessible.
- Edge cases:
  - Ensure undocumented endpoints are flagged.
  - Validate DTOs against OpenAPI schemas.
  - Handle missing or incomplete error models.
- Defaults:
  - PATCH endpoints: not required unless business need arises.
  - File upload/download: not required unless specified.
  - GET endpoints: support pagination, filtering, sorting using query params.
- DoD:
  - All endpoints documented in OpenAPI v3.
  - Swagger UI and /v3/api-docs accessible.
  - Automated test verifies spec generation.
  - API contract matches implementation.

## 3. DTO Design & Validation
- Define DTO classes for each API operation:
  - Use Java 21 records or immutable classes.
  - Annotate fields with validation constraints (@NotNull, @Size, @Pattern, etc.).
  - Document each field with Javadoc and OpenAPI annotations.
- Implement mapping logic between DTOs and domain entities (use MapStruct or manual mapping).
- Ensure DTOs are not coupled to JPA entities; use separate packages for DTOs and entities.
- Add unit tests for DTO validation logic.
- Document DTO structure and validation rules in copilot-index.md.
- Edge cases:
  - Handle nested DTOs and complex validation (e.g., custom validators).
  - Test large payloads and boundary values.
- Defaults:
  - Multi-tenancy: not required unless specified.
  - Audit logging: not required for DTO changes unless compliance required.
  - GDPR/compliance: not required unless specified.
- DoD:
  - All DTOs are immutable and validated.
  - Mapping logic is covered by unit tests.
  - DTOs documented in OpenAPI and copilot-index.md.

## 4. Domain Model & Database Migration
- Design Entry entity:
  - Fields: UUID PK, JSONB metadata, created/updated timestamps.
  - Use Java 21 features (e.g., records for value objects).
- Create Flyway migration scripts:
  - Create table with appropriate columns and types.
  - Add indexes for performance (e.g., on UUID, timestamps).
  - Enable uuid-ossp/gen_random_uuid extension for UUID generation.
- Implement EntryRepository using Spring Data JPA:
  - Use Specification pattern for dynamic queries.
  - Add custom query methods as needed.
- Add integration tests using Testcontainers to validate migrations and repository logic.
- Document DB schema and migration process in copilot-index.md.
- Edge cases:
  - Handle invalid UUIDs and large metadata objects.
  - Test migration failures and rollback scenarios.
- Defaults:
  - Soft deletes: not required, use hard deletes unless business need arises.
- DoD:
  - Migrations run successfully and are idempotent.
  - DB schema matches documented model.
  - Repository logic covered by integration tests.

## 5. API Implementation & Exception Handling
- Implement EntryController:
  - Define endpoints for create, read, update, delete operations.
  - Use DTOs for all input/output.
  - Annotate with @Validated for request validation.
- Implement EntryService:
  - Encapsulate business logic, mark boundaries with @Transactional.
  - Wrap low-level exceptions into domain-specific exceptions.
- Implement EntryRepository:
  - Abstract persistence logic, use Specification for queries.
- Implement global exception handler:
  - Use @ControllerAdvice to handle domain-specific and generic exceptions.
  - Return structured error responses with status, code, message, and details.
- Add unit and integration tests for all endpoints and error scenarios.
- Document error codes/messages in copilot-index.md.
- Edge cases:
  - Handle invalid input, duplicate entries, DB errors, missing records.
- Defaults:
  - Use standard HTTP error codes/messages unless business-specific errors are required.
- DoD:
  - All endpoints covered by tests (positive/negative).
  - Exception handling is robust and documented.
  - Error responses match API contract.

## 6. Authentication & Authorization
- Integrate Spring Security with Cognito OIDC JWT validation:
  - Configure issuer, audience, JWKS URI in application.yml for both prod and local profiles.
  - Use spring-boot-starter-oauth2-resource-server for JWT validation.
- Implement JwtAuthenticationConverter:
  - Extract roles from Cognito claims (cognito:groups or custom claims).
  - Map claims to application roles and authorities.
- Enforce method security:
  - Use @PreAuthorize annotations on service/controller methods for role-based access control.
  - Document required roles for each endpoint in copilot-index.md.
- Handle authentication failures:
  - Return structured 401/403 error responses with details.
  - Log failed authentication attempts with userId/requestId.
- Document local Cognito setup:
  - Provide step-by-step guide for creating dev pool, app client, test users/groups.
  - Document process for obtaining JWT tokens (AWS CLI, Hosted UI, scripts).
  - Add troubleshooting guide for common local auth issues (token expiry, invalid claims).
- Add integration tests for JWT validation, role mapping, and access control.
- Edge cases:
  - Token expiry, invalid claims, revoked tokens, clock skew, missing roles.
  - Test with users in/out of required groups, malformed JWTs.
- Clarify:
  - Should custom claims be supported in addition to standard Cognito claims?
  - Is multi-tenancy required for role mapping?

## 7. Logging & Monitoring
- Configure Logback for structured JSON logs:
  - Include timestamp, log level, logger, traceId, userId, requestId, operation, error details.
  - Use MDC to propagate correlation IDs across threads and async calls.
- Integrate with centralized logging (e.g., AWS CloudWatch, Fluent Bit):
  - Set up log shipping from containers to CloudWatch.
  - Document log retention and access policies.
- Expose Prometheus metrics via /actuator/prometheus:
  - Add custom metrics for business operations (e.g., entry creation rate).
  - Document all available metrics in copilot-index.md.
- Integrate OpenTelemetry for distributed tracing:
  - Export traces to AWS X-Ray.
  - Instrument key service boundaries and external calls.
- Set up alerting and dashboards:
  - Configure Prometheus Alertmanager and CloudWatch Alarms for SLOs.
  - Create Grafana dashboards for key metrics and traces.
- Add tests to verify logging, metrics, and tracing are emitted as expected.
- Edge cases:
  - Log loss, trace propagation failures, metric gaps, alert noise, dashboard access issues.
- Clarify:
  - What are the SLOs and alert thresholds for key operations?
  - Is audit logging required for sensitive actions?
  - What is the required log retention period?

## 8. Testing: Unit, Integration, Contract, E2E
- Write unit tests for all business logic, controllers, services, and utilities:
  - Use JUnit 5, Mockito, AssertJ for assertions and mocking.
  - Cover positive and negative scenarios, including edge cases.
- Implement integration tests:
  - Use Testcontainers to spin up Postgres and other dependencies.
  - Test DB migrations, repository logic, and service integration.
- Add contract tests:
  - Use Pact or Spring Cloud Contract to validate API contract against OpenAPI spec.
  - Test error responses and schema compliance.
- Implement E2E tests in CI pipeline:
  - Simulate real user flows, including authentication and authorization.
  - Test with real Cognito tokens and edge cases (expired, invalid, missing claims).
- Measure and report test coverage:
  - Ensure minimum required coverage (e.g., 80%+ for critical modules).
  - Document coverage in copilot-index.md.
- Edge cases:
  - Invalid input, auth failures, DB downtime, migration errors, contract mismatches.
- Clarify:
  - What is the minimum required coverage for each layer?
  - Should negative test cases be mandatory for all endpoints?

## 9. Dockerization
- Create multi-stage Dockerfile:
  - Stage 1: Build app with JDK, run tests, package jar.
  - Stage 2: Use JRE/distroless image, copy jar, set non-root user, expose port 8080.
  - Parameterize Java version via build args.
- Optimize image size and security:
  - Remove build artifacts, minimize layers, scan for vulnerabilities.
  - Use Docker healthcheck for readiness.
- Document build and run instructions in README.
- Add automated build/test in CI pipeline.
- Edge cases:
  - Build failures, runtime errors, port conflicts, missing dependencies.
  - Test image with different base OS versions.

## 10. Helm & Kubernetes Deployment
- Scaffold Helm chart with templates for deployment, service, ingress, HPA, configmap, secret, values.yaml.
- Set resource requests/limits, readiness/liveness probes, replica counts, IRSA annotations.
- Configure ingress for AWS ALB, TLS termination, ACM certificate, Route53 DNS.
- Load environment variables and secrets from ConfigMap/Secret (never hardcode sensitive values).
- Document Helm values and deployment process in copilot-index.md.
- Add tests for Helm chart rendering and K8s manifests.
- Edge cases:
  - Probe failures, scaling events, secret sync issues, misconfigured ingress.
  - Test with different values.yaml configurations.
- Clarify:
  - Should API versioning be supported in ingress paths?
  - What health/readiness endpoints are required?
  - Is rate limiting needed at ingress or app level?

## 11. AWS Infrastructure as Code
- Create Terraform modules for each AWS resource:
  - VPC: multi-AZ, public/private subnets, NAT gateways, route tables.
  - EKS: cluster, node groups, OIDC provider, security groups, IAM roles.
  - RDS: Postgres instance, multi-AZ, subnet group, security group, parameter group.
  - ECR: repository for Docker images, lifecycle policies.
  - IAM: roles/policies for EKS, IRSA, CI/CD, least privilege.
  - ACM: TLS certificate for ALB ingress.
  - Route53: DNS records for app endpoints.
- Define outputs and dependencies between modules (e.g., VPC → EKS → RDS).
- Parameterize region, instance types, scaling policies, and tags.
- Document module usage and variables in README/copilot-index.md.
- Add automated tests for Terraform plan/apply (e.g., with terratest).
- Edge cases:
  - Subnet misconfigurations, IAM privilege errors, resource limits, region mismatches.
  - Test with different region/instance type combinations.
- Clarify:
  - Which AWS regions should be supported?
  - Any compliance/audit requirements (e.g., encryption, log retention)?
  - Should blue/green or canary deployments be supported?

## 12. CI/CD Pipeline
- Implement GitHub Actions workflows for CI and CD:
  - ci.yml: checkout, build, test, Docker image build/push, artifact upload.
  - cd.yml: deploy to EKS via Helm, run Flyway migrations, post-deploy smoke tests.
- Configure AWS OIDC authentication for secure access.
- Manage secrets via GitHub/AWS Secrets Manager (never hardcode in workflow).
- Implement rollback strategy for failed deployments (e.g., Helm rollback, DB migration rollback).
- Document pipeline steps, environment variables, and secret management in README/copilot-index.md.
- Add status badges and notifications for pipeline runs.
- Edge cases:
  - Failed builds, deployment rollbacks, secret rotation, migration failures, artifact loss.
  - Test pipeline with simulated failures and recovery.
- Clarify:
  - Should migrations run before or after app deployment?
  - How should secrets be rotated and managed in CI/CD?
  - What is the rollback strategy for failed deployments?

## 13. Secrets Management & IRSA
- Store DB and sensitive credentials in AWS Secrets Manager.
- Annotate K8s service account with IRSA role for secrets access.
- Integrate aws-secrets-manager-csi-driver or external-secrets for syncing secrets to pods.
- Document secret creation, rotation, and sync process in README/copilot-index.md.
- Add tests for secret retrieval and rotation scenarios.
- Edge cases:
  - Secret sync failures, permission issues, secret rotation, missing secrets.
  - Test with rotated/expired secrets and permission changes.

## 14. Observability & Monitoring
- Configure Fluent Bit/CloudWatch agent for log shipping from containers.
- Set up Prometheus/Grafana for metrics and dashboards:
  - Create custom dashboards for business and system metrics.
  - Document dashboard access and usage.
- Integrate OpenTelemetry/AWS X-Ray for distributed tracing.
- Configure Prometheus Alertmanager and CloudWatch Alarms for SLOs and alerting.
- Document observability setup and troubleshooting in copilot-index.md.
- Add tests for log/metric/trace emission and alert triggers.
- Edge cases:
  - Log loss, metric gaps, alert noise, dashboard access issues, trace propagation failures.
  - Test with simulated outages and high load.
- Clarify:
  - What log retention and metrics granularity are required?
  - What dashboards and alerting rules are needed for production?

## 15. Security Hardening
- Enforce TLS for all endpoints via ALB ingress and ACM certs.
- Restrict RDS/public access, minimize security group rules.
- Apply least privilege to IAM roles and K8s RBAC.
- Enable image scanning on ECR and use Dependabot for dependency updates.
- Document security controls, policies, and audit requirements in copilot-index.md.
- Add automated security tests/scans in CI pipeline.
- Edge cases:
  - Misconfigurations, privilege escalation, dependency vulnerabilities, unscanned images.
  - Test with simulated attacks and misconfigurations.

## 16. Local Development & Tooling
- Provide docker-compose.yml for local Postgres and app.
- Add scripts/run-local.sh to build/run jar and set env vars.
- Integrate with actual Cognito dev pool for local authentication:
  - Document setup, token retrieval, and troubleshooting for local Cognito.
- Use Testcontainers for integration tests; optionally LocalStack for AWS emulation.
- Document local dev workflow, environment variables, and troubleshooting in README/copilot-index.md.
- Add tests for local dev scenarios (DB failures, expired tokens, env var overrides).
- Edge cases:
  - Local DB failures, expired tokens, env var overrides, port conflicts.
  - Test with missing/invalid local configuration.
- Clarify:
  - Should scripts for Cognito token generation be provided?
  - Is LocalStack required for any AWS service emulation?

## 17. Documentation & Deliverables
- Write and maintain README.md with quick start, build, deploy, and test instructions.
- Maintain copilot-index.md with architecture, modules, flows, APIs, naming, and test coverage.
- Provide onboarding guide, troubleshooting steps, and API usage examples (curl/Postman).
- Validate file tree and deliverables per requirements.md.
- Add automated checks for documentation completeness in CI pipeline.
- Edge cases:
  - Missing/outdated docs, onboarding gaps, incomplete API examples.
  - Test onboarding with new developer feedback.
- Clarify:
  - What troubleshooting guide and API usage examples are required?

## 18. Acceptance Criteria & Final Validation
- Validate all functional and non-functional requirements against requirements.md and copilot-index.md.
- Run full test suite (unit, integration, contract, E2E) and verify deployment in all environments.
- Test edge case handling (API contract mismatches, scaling failures, security gaps, log/metric loss).
- Document validation results and final checklist in copilot-index.md.
- Add automated acceptance checks in CI pipeline.
- Edge cases:
  - API contract mismatches, scaling failures, security gaps, log/metric loss, incomplete validation.
  - Test with simulated failures and recovery scenarios.
- Clarify:
  - Any additional acceptance criteria or validation steps required for production sign-off?

---

> Please answer the clarifying questions in each task to ensure all requirements and edge cases are covered. If you have additional requirements, specify them now.
