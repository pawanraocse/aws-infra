# AWS-Infra Project - Unified Index & Source of Truth

**Last Updated:** 2025-10-30
**Version:** 2.3.0
**Status:** ✅ Production-Ready Multi-Tenant Microservices Architecture

---

## 📋 Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Project Structure](#project-structure)
5. [Microservices](#microservices)
6. [Infrastructure (Terraform)](#infrastructure-terraform)
7. [Authentication & Security](#authentication--security)
8. [Database & Multi-Tenancy](#database--multi-tenancy)
9. [Configuration Management](#configuration-management)
10. [Build & Deployment](#build--deployment)
11. [API Documentation](#api-documentation)
12. [Testing Strategy](#testing-strategy)
13. [Monitoring & Observability](#monitoring--observability)
14. [Development Workflow](#development-workflow)
15. [Known Issues & TODOs](#known-issues--todos)

---

## 🎯 Project Overview

### Purpose
Production-ready AWS-based Spring Boot microservices platform with Angular frontend, implementing:
- **Multi-tenant architecture** with schema-per-tenant isolation
- **AWS Cognito authentication** with Modern UI v2
- **Service discovery** via Netflix Eureka
- **API Gateway** with JWT validation and tenant context propagation
- **Infrastructure as Code** using Terraform
- **Containerized deployment** with Docker Compose

### Business Domain
Enterprise SaaS platform supporting multiple tenants with complete data isolation, centralized authentication, and scalable microservices architecture.

### Key Features
- ✅ Multi-tenant data isolation (schema-per-tenant)
- ✅ AWS Cognito OAuth2/OIDC authentication
- ✅ JWT-based authorization with tenant context
- ✅ Service discovery and load balancing
- ✅ API Gateway with circuit breaker
- ✅ Distributed tracing and metrics
- ✅ Automated infrastructure provisioning
- ✅ SSM Parameter Store for secrets
- ✅ PostgreSQL with Flyway migrations
- ✅ OpenAPI/Swagger documentation

---

## 🏗️ Architecture

### High-Level Architecture Diagram
```
[Angular Frontend] --(JWT)--> [API Gateway] --(JWT validated, headers added)--> [Auth Service | Backend Service] --(tenant context)--> [PostgreSQL]
      |                                                                                                   |
      |                                                                                                   |
      +-------------------[Eureka Service Registry]-------------------+                                   |
      |                                                                                                   |
      +-------------------[AWS Cognito, SSM Parameter Store]----------+                                   |
```

### Request Flow Sequence

**1. User Login Flow:**
```
User → Frontend → Gateway (8080) → Auth-Service (8081) → Cognito
                                                    ↓
User ← Frontend ← Gateway ← Auth-Service ← JWT Token ← Cognito
```

**2. API Request Flow:**
```
User (JWT) → Frontend → Gateway → Validate JWT → Extract tenant_id
                                   ↓
                              Add Headers (X-Tenant-Id, X-User-Id)
                                   ↓
                              Backend-Service → TenantContextFilter
                                   ↓
                              Set schema: tenant_acme
                                   ↓
                              PostgreSQL (tenant_acme.entries)
```

---

## 💻 Technology Stack

### Backend
| Component         | Technology         | Version   | Purpose                                 |
|-------------------|-------------------|-----------|-----------------------------------------|
| Language          | Java              | 21 LTS    | Modern Java features, records, pattern matching |
| Framework         | Spring Boot       | 3.5.6     | Microservices framework                  |
| Cloud             | Spring Cloud      | 2025.0.0  | Service discovery, gateway, config       |
| Build Tool        | Maven             | 3.9+      | Dependency management, multi-module builds |
| Database          | PostgreSQL        | 16-alpine | Relational database with JSONB support   |
| Migration         | Flyway            | Latest    | Database version control                 |
| ORM               | Hibernate/JPA     | Latest    | Object-relational mapping                |
| Auth              | AWS Cognito       | -         | OAuth2/OIDC authentication               |
| Service Discovery | Netflix Eureka    | Latest    | Service registry                         |
| API Gateway       | Spring Cloud Gateway | Latest | Reactive gateway with filters            |
| Tracing           | Micrometer + Brave| Latest    | Distributed tracing                      |
| Metrics           | Prometheus        | Latest    | Metrics collection                       |
| Documentation     | SpringDoc OpenAPI | 2.8.13    | API documentation                        |

### Frontend
| Component   | Technology   | Version   | Purpose                |
|-------------|-------------|-----------|------------------------|
| Framework   | Angular     | 20.3.0    | SPA framework          |
| Language    | TypeScript  | 5.9.2     | Type-safe JavaScript   |
| Build       | Angular CLI | 20.3.7    | Build and dev server   |
| Testing     | Jasmine + Karma | Latest | Unit testing           |
| SSR         | Angular SSR | 20.3.7    | Server-side rendering  |

### Infrastructure
| Component   | Technology   | Version   | Purpose                |
|-------------|-------------|-----------|------------------------|
| Container   | Docker       | Latest    | Containerization       |
| IaC         | Terraform    | >=1.9.0   | Infrastructure as Code |
| Cloud       | AWS          | -         | Cloud provider         |
| Secrets     | AWS SSM      | -         | Parameter Store        |
| Registry    | AWS ECR      | Planned   | Container Registry     |
| Orchestration | AWS ECS Fargate | Planned | Container Orchestration |
| CI/CD       | GitHub Actions | Planned | Continuous Integration |
| Monitoring  | Prometheus, Grafana | Planned | Monitoring           |
| Logging     | CloudWatch, Fluent Bit | Planned | Logging           |
| Tracing     | AWS X-Ray, OpenTelemetry | Planned | Tracing         |

---

## 📂 Project Structure

- **/auth-service/**: Handles authentication, JWT, and user info
- **/backend-service/**: Business logic, multi-tenant data, CRUD APIs
- **/gateway-service/**: API Gateway, JWT validation, routing
- **/eureka-server/**: Service registry
- **/frontend/**: Angular SPA
- **/terraform/**: Infrastructure as Code
- **/scripts/**: Utility scripts

---

## 🧩 Microservices

### Eureka Server (Port 8761)
- Service discovery and registration
- Health monitoring
- Load balancing support
- Endpoint: `http://localhost:8761`

### Auth Service (Port 8081)
- OAuth2/OIDC authentication with AWS Cognito
- Session management (JSESSIONID)
- JWT token extraction
- User info endpoint
- Logout functionality
- Endpoints:
  - `GET /auth/login` - Redirect to Cognito Hosted UI
  - `GET /auth/oauth2/callback` - OAuth2 callback handler
  - `GET /auth/tokens` - Extract JWT from session
  - `GET /auth/user-info` - Get user information
  - `POST /auth/logout` - Logout and clear session

### Gateway Service (Port 8080)
- API Gateway with JWT validation and routing
- Circuit breaker, header propagation, CORS
- Endpoints:
  - `/auth/**` → lb://auth-service
  - `/api/**` → lb://backend-service

### Backend Service (Port 8082)
- Multi-tenant business logic
- Schema-per-tenant data isolation
- CRUD APIs

---

## 🛡️ Authentication & Security
- OAuth2/OIDC with AWS Cognito
- JWT-based authentication
- Tenant context propagation via headers
- Secure secrets in AWS SSM Parameter Store
- Spring Security for all endpoints

---

## 🗄️ Database & Multi-Tenancy
- PostgreSQL 16-alpine
- Schema-per-tenant isolation
- Flyway for migrations
- Hibernate multi-tenancy

---

## ⚙️ Configuration Management
- All secrets and config in AWS SSM Parameter Store
- Environment-specific YAML files
- Terraform manages SSM parameters

---

## 🚀 Build & Deployment
- Maven multi-module build
- Docker Compose for local dev
- Terraform for AWS infra
- GitHub Actions for CI/CD (planned)

---

## 📑 API Documentation
- SpringDoc OpenAPI 2.8.13
- Swagger UI at `/swagger-ui.html` for each service

---

## 🧪 Testing Strategy
- JUnit 5, Mockito, AssertJ for unit tests
- Testcontainers for integration tests
- Cypress/Playwright for frontend e2e

---

## 📈 Monitoring & Observability
- Prometheus, Grafana (planned)
- Distributed tracing (planned)
- Centralized logging (planned)

---

## 🛠️ Development Workflow
- Feature branches, semantic commits
- PR review checklist:
  - End-to-end flow complete
  - Dependency injection and test coverage
  - Security, logging, validation integrated
  - Scalable and reusable
  - Names clearly communicate intent

---

## 📝 Known Issues & TODOs
- [ ] ECS/ECR deployment automation
- [ ] Full CI/CD pipeline
- [ ] Advanced monitoring and tracing
- [ ] Tenant onboarding automation

---

## Naming Conventions
- Controllers: `*Controller.java`
- Services: `*.service.ts`
- Tests: `*.spec.ts`, `*Test.java`
- Config: `application-<env>.yml`

---

## Test Coverage Map
- Auth Service: 80%+ unit/integration
- Backend Service: 85%+ unit/integration
- Gateway: 70%+ integration
- Frontend: 80%+ unit/e2e

---

## Auth/Security Approach
- All external APIs secured with JWT
- User/tenant context propagated via headers
- SSM Parameter Store for secrets
- No JPA entities exposed externally

---

## Update Policy
- This file is the single source of truth
- Update after every significant architectural or flow change
- Remove all duplicates (e.g., co-pilot-index.md)

