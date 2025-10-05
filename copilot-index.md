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

## Auth/Security Approach
- JWT for API auth
- IAM roles for AWS resources
- Secrets via AWS Secrets Manager

## Update Policy
- Update this file after any significant architectural, module, or integration change.

