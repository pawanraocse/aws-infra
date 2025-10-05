# copilot-index.md

## Overview
Production-ready AWS-based Spring Boot microservice with Angular frontend. All infrastructure and resources are sized for AWS Free Tier.

## Tech Stack
- Backend: Java 21+, Spring Boot 3.x, PostgreSQL (RDS)
- Frontend: Angular 18+, TypeScript, SCSS
- Infra: Terraform, Helm, AWS EKS (t3.micro managed node group), AWS Secrets Manager
- CI/CD: GitHub Actions

## Entry Points
- Backend: /src/main/java/com/learning/awsinfra/AwsInfraApplication.java
- Frontend: /frontend (to be created)
- Infra: /infra, /helm, /terraform

## Modules/Folders
- src/main/java: Backend source
- src/main/resources: Backend config
- src/test/java: Backend tests
- infra/: Infra as Code (Terraform)
- helm/: K8s manifests
- terraform/: AWS resources
- scripts/: Utility scripts
- frontend/: Angular app (to be created)

## Key Relationships & End-to-End Flows
- UI (Angular) → API (Spring Boot) → Service → Repository → PostgreSQL (RDS)
- Auth via JWT, secrets via AWS Secrets Manager
- CI/CD pipeline: GitHub Actions → Build/Test/Deploy

## Critical APIs & Integration Points
- REST endpoints documented via OpenAPI/Swagger
- DB access via JPA
- Secrets via AWS Secrets Manager
- Logging via SLF4J + Logback

## Naming Conventions
- Controller: *Controller.java
- Service: *Service.java
- Repository: *Repository.java
- DTO: *Dto.java
- Angular: *.component.ts, *.service.ts, *.spec.ts

## Test Coverage Map
- Backend: JUnit 5, Mockito, AssertJ, Testcontainers
- Frontend: Jest/Karma+Jasmine, Cypress/Playwright
- Integration: API, DB, E2E

## API Filtering & Specification Pattern
- GET /api/entries supports query params: type, minAmount, createdAfter
- Dynamic filtering implemented via JPA Specification pattern (EntrySpecification)
- Controller parses query params, service applies Specification for DB-level filtering
- Integration tests validate all filter combinations

## DB Schema & Versioning
- Schema managed via Flyway migrations (see src/main/resources/db/migration)
- Initial migration creates entries and entry_metadata tables
- All schema changes are versioned, repeatable, and CI/CD compatible

## Security & Logging
- Structured logging at every layer (userId, requestId, operation)
- API endpoints require userId/requestId for traceability
- Error responses standardized via ErrorResponse DTO
- **Note:** Authentication is currently disabled for all API endpoints to facilitate development and testing. This is a temporary configuration. **TODO: Re-enable authentication and full security before production deployment.**

## Update Policy
- Update this file after any significant architectural, module, or integration change.

## API Contract & Swagger/OpenAPI Integration

- **springdoc-openapi** dependency present in pom.xml.
- All REST controllers annotated with @Operation, @ApiResponse, @Parameter, and schema references for request/response/error models.
- OpenAPI v3 spec auto-generated and available at `/v3/api-docs`.
- Interactive Swagger UI available at `/swagger-ui.html` (see README for usage).
- All endpoints (Entry CRUD, Hello) documented and match implementation.
- Error responses use structured ErrorResponse DTO, referenced in OpenAPI annotations.
- GET endpoints support pagination, filtering, and sorting via query params (see EntryController, EntryService).
- Automated integration test (`OpenApiSpecIntegrationTest`) verifies OpenAPI spec endpoint, content type, and required fields.
- No undocumented endpoints; all are covered by OpenAPI annotations.
- DTOs validated and referenced in OpenAPI schemas.
- Edge cases (missing error models, undocumented endpoints) are handled.

**Status:** Fully implemented and validated as of 2025-10-05

## DTO Design & Validation

- All external API communication uses immutable DTOs (Java records).
- EntryRequestDto enforces validation: metadata must contain required keys (type: invoice|payment, amount > 0).
- Custom @ValidMetadata annotation and MetadataValidator ensure business rules.
- Controllers use @Valid and @Validated for DTO validation.
- Error responses use structured ErrorResponse DTO.
- OpenAPI schema documents all validation constraints.
- Dedicated unit tests (EntryRequestDtoTest) verify validation for all edge cases.
- No JPA entities are exposed externally; mapping is handled in service layer.

**Status:** Fully implemented and validated as of 2025-10-05

## Key Entity & API Refactor (Key-Value Model)

- Entry entity refactored: now a simple key-value table with auto-generated Long id, key, and value columns.
- Flyway migration script updated: creates single entries table (id BIGSERIAL PK, meta_key, meta_value).
- DTOs (EntryRequestDto, EntryResponseDto) now use key and value fields (no metadata map or timestamps).
- Repository, service, and controller layers refactored for Long id and key/value CRUD operations.
- Controller endpoints simplified: create, get all (paginated), get by id, update, delete.
- All business logic, validation, and OpenAPI documentation updated for new model.
- All related unit and integration tests refactored and passing for key-value flow.

**Status:** Fully implemented and validated as of 2025-10-05
