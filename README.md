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

## License
MIT

