# AWS-Infra Project - Technical Index & Status

**Last Updated:** 2025-11-24
**Version:** 3.0.0
**Status:** ✅ Authentication Flow Functional | 🚧 Platform Service Hardening | 🚧 Frontend Development

---

## 1. Project Overview
Production-ready AWS-based Spring Boot microservices platform with Angular frontend.
*   **Repo:** `AWS-Infra`
*   **Architecture:** Microservices (Gateway, Auth, Backend, Platform, Eureka).
*   **Infrastructure:** AWS (EKS/ECS, RDS, Cognito, SSM) via Terraform.

---

## 2. Current Implementation State (from CURRENT_STATUS.md)

### ✅ What's Working
*   **Authentication:** End-to-end OAuth2 flow with Cognito Hosted UI.
    *   Frontend redirects to Cognito -> Auth Service exchanges code -> Session created.
    *   Gateway validates JWTs and enforces tenant context.
*   **Microservices:**
    *   **Gateway (8080):** Routing, JWT Validation, Header Enrichment.
    *   **Auth (8081):** OIDC/OAuth2, Session Management.
    *   **Backend (8082):** Multi-tenant Entry CRUD (Schema-per-tenant).
    *   **Platform (8083):** Tenant Provisioning (Basic).
    *   **Eureka (8761):** Service Discovery.
*   **Frontend:** Angular 18+ app structure, Login flow, JWT storage.

### 🚧 In Progress / Known Issues
*   **Platform Service:** Database-per-tenant mode is partially implemented but needs migration split.
*   **Internal Tokens:** Planned but not yet implemented (currently trusting headers).
*   **Policy Engine:** Planned.

---

## 3. Developer Guide

### 3.1 Setup & Run
1.  **Prerequisites:** Java 21, Maven 3.9+, Docker, Node.js 20+.
2.  **Build:** `./mvnw clean install`
3.  **Run Locally:** `docker-compose up -d`
4.  **Access:**
    *   Frontend: `http://localhost:4200`
    *   Gateway: `http://localhost:8080`
    *   Eureka: `http://localhost:8761`

### 3.2 Key Commands
*   **Backend Build:** `./mvnw clean package -DskipTests`
*   **Frontend Run:** `ng serve` (in `frontend/` dir)
*   **Test API:** `curl -H "Authorization: Bearer <JWT>" http://localhost:8080/api/v1/entries`

---

## 4. Codebase Index

### 4.1 Service Modules
*   `/gateway-service`: **Security Boundary**. See `JwtAuthenticationGatewayFilterFactory.java`.
*   `/auth-service`: **Identity**. See `AuthController.java`, `CognitoConfig.java`.
*   `/backend-service`: **Domain**. See `EntryController.java`, `TenantContextFilter.java`.
*   `/platform-service`: **Control Plane**. See `TenantController.java`, `ProvisioningService.java`.
*   `/common-dto`: Shared data transfer objects.

### 4.2 Infrastructure
*   `/terraform`: AWS Infrastructure as Code.
*   `/helm`: Kubernetes charts.
*   `/infra`: Docker Compose and local config.

### 4.3 Documentation
*   `HLD.md`: **Primary Design Document**. Architecture, Security, Multi-tenancy.
*   `next_task.md`: **Backlog**. Active and pending tasks.

---

## 5. Configuration & Secrets
*   **Local:** `application.yml` and `docker-compose.yml`.
*   **AWS:** SSM Parameter Store (`/cloud-infra/dev/...`).
*   **Secrets:** Never commit secrets. Use env vars or SSM.

---

## 6. Testing Strategy
*   **Unit:** JUnit 5, Mockito.
*   **Integration:** Testcontainers (Postgres).
*   **E2E:** Cypress/Playwright (Planned).

---

**End of Index**
