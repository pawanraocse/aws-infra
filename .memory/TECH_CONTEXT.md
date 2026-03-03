# TECH_CONTEXT
_Living document. Update when stack or conventions change._
_Last updated: 2026-03-03_

## Stack
- **Language:** Java 21, TypeScript (Angular)
- **Runtime:** JVM, Node.js
- **Framework:** Spring Boot 3.5.9, Angular 19, Spring Cloud Gateway
- **Database:** PostgreSQL (Database-per-tenant)
- **Testing:** JUnit, Testcontainers, Mockito
- **CI/CD:** GitHub Actions
- **Deployment:** ECS Fargate, ALB, Terraform managed

## Coding Conventions
- **Style guide:** Maven Checkstyle plug-in (`checkstyle.xml`)
- **Folder structure:** Decoupled multi-module Maven project

## Test Plan Requirements
- Unit tests required for: business logic and routing
- Minimum coverage threshold: Checkstyle rules apply, coverage tracked via JaCoCo

## SOLID Enforcement
- **S** — Each file/class has one reason to change
- **O** — New features via extension, not modification of core modules
- **L** — Subtypes must be substitutable (enforced via TypeScript interfaces)
- **I** — Interfaces are narrow and role-specific
- **D** — Inject dependencies; no hard imports of concrete implementations in business logic
