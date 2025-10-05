# AWS Infra Microservice

## Overview
Production-ready AWS-based Spring Boot microservice with Angular frontend. All infrastructure and resources are sized for AWS Free Tier.

## Tech Stack
- Backend: Java 21+, Spring Boot 3.x, PostgreSQL (RDS)
- Frontend: Angular 18+, TypeScript, SCSS
- Infra: Terraform, Helm, AWS EKS, AWS Secrets Manager
- CI/CD: GitHub Actions

## Setup Instructions
1. **Prerequisites**
   - Java 21+
   - Maven 3.9+
   - Docker
   - AWS CLI
   - kubectl
2. **Build Backend**
   ```sh
   ./mvnw clean install
   ```
3. **Run Locally**
   ```sh
   ./mvnw spring-boot:run
   ```
4. **Infrastructure**
   - See `infra/`, `terraform/`, and `helm/` for AWS setup and deployment.
5. **Frontend**
   - See `frontend/` for Angular app (to be created).

## Project Structure
- `src/main/java` - Backend source
- `src/main/resources` - Backend config
- `src/test/java` - Backend tests
- `infra/` - Infra as Code (Terraform)
- `helm/` - K8s manifests
- `terraform/` - AWS resources
- `scripts/` - Utility scripts
- `frontend/` - Angular app

## Build Tool Version
- Maven: 3.9+
- Java: 21+

## Documentation
- See `copilot-index.md` for architecture, flows, APIs, and test coverage.

## API Documentation (Swagger/OpenAPI)

- Interactive API docs are available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) when running locally.
- The OpenAPI v3 specification is available at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).
- Use the Swagger UI to explore, test, and understand all available endpoints, request/response schemas, and error models.
- The OpenAPI spec can be used for client code generation (e.g., OpenAPI Generator, Postman).
- **Security Note:** In production, restrict access to Swagger UI and OpenAPI spec endpoints to authorized users only.

 ## Authentication (Development Mode)

**Note:** Authentication is currently disabled for all API endpoints to facilitate development and testing. This is a temporary configuration. **TODO: Re-enable authentication and full security before production deployment.**

## License
MIT
