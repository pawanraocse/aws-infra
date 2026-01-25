
# System Architecture

**Version:** 7.2 (Extracted from HLD)
**Last Updated:** 2026-01-17

This document details the microservices architecture, component responsibilities, and request flows of the SaaS Foundation.

---

## 🏗️ High-Level Architecture

```mermaid
graph TB
    subgraph "Client Layer"
        WebApp[🌐 Angular Web App]
        User[👤 End Users]
    end
    
    subgraph "Entry Point"
        ALB[AWS ALB]
    end
    
    subgraph "Microservices"
        Gateway[🛡️ Gateway Service]
        Auth[🔐 Auth Service]
        Platform[⚙️ Platform Service]
        Backend[📦 Backend Service]
        Eureka[🔍 Eureka Server]
    end
    
    subgraph "AWS Services"
        Cognito[Cognito]
        RDS[RDS PostgreSQL]
        SSM[SSM Parameter Store]
    end
    
    User --> WebApp
    WebApp --> ALB
    ALB --> Gateway
    Gateway --> Auth
    Gateway --> Platform
    Gateway --> Backend
    Gateway -.- Eureka
```

---

## 📋 Service Roles & Responsibilities

### 🛡️ Gateway Service (Port 8080)
**Role:** Gatekeeper - Security boundary for ALL incoming requests.

- **Authentication:** Validates JWT tokens from Cognito. **Sole Validator**.
- **Tenant Context:** Extracts `X-Tenant-Id` from JWT/Headers.
- **Header Enrichment:** Injects `X-User-Id`, `X-Authorities` downstream.
- **Rate Limiting:** Redis-based limiting per tenant/IP.
- **Routing:** Dispatches requests to services via Eureka.

### 🔐 Auth Service (Port 8081)
**Role:** Identity, Permissions, and Signup Orchestration.

- **User Management:** Wraps Cognito actions, manages user profiles.
- **RBAC & PBAC:** Stores Roles (`admin`, `viewer`) and Permissions (`entry:read`).
- **Signup Pipeline:** Orchestrates tenant creation, DB provisioning, and email verification.
- **OpenFGA Writer:** If enabled, writes ReBAC tuples.

### ⚙️ Platform Service (Port 8083)
**Role:** Control plane for Tenant Lifecycle.

- **Tenant Registry:** Master list of tenants and their JDBC URLs.
- **Provisioning:** Creates new tenant databases and runs Flyway migrations.
- **API Keys:** Manages programmatic access keys.

### 💳 Payment Service (Port 8088)
**Role:** Dedicated Billing and Subscription Management.

- **Providers:** Abstraction over Stripe/Razorpay.
- **Webhooks:** Processes provider events.
- **Status Authority:** Updates Redis `billing:status:{tenant}` for real-time enforcement.

### 📦 Backend Service (Port 8082)
**Role:** Domain-specific business logic (MIMIC/TEMPLATE).

- **Business Logic:** Your actual SaaS application code (Orders, Projects, etc.).
- **Authorization:** Enforces `@RequirePermission` checks.
- **Isolation:** Connects to specific tenant DB based on `X-Tenant-Id`.

### 🔍 Eureka Server (Port 8761)
**Role:** Service Discovery.
- Services register here on startup (`http://eureka-server:8761/eureka`).
- Gateway queries Eureka to find service instances.

---

## 📡 API Request Flow (Production)

### 1. Simplified Flow
1. **User** sends request → **Gateway**
2. **Gateway** validates JWT & extracts Tenant ID.
3. **Gateway** injects headers: `X-Tenant-Id`, `X-User-Id`.
4. **Gateway** forwards to **Backend Service**.
5. **Backend** reads header → Connects to **Tenant Database**.
6. **Backend** executes logic & returns response.

### 2. Request Diagram
```mermaid
sequenceDiagram
    participant Browser
    participant Gateway
    participant Backend
    participant TenantDB

    Browser->>Gateway: GET /api/v1/entries (JWT)
    Note over Gateway: Validate JWT<br/>Extract Tenant
    Gateway->>Backend: Forward request<br/>+ X-Tenant-Id: t_acme
    Note over Backend: TenantContextFilter<br/>Select DB: t_acme
    Backend->>TenantDB: Query Data
    TenantDB-->>Backend: Results
    Backend-->>Gateway: 200 OK
    Gateway-->>Browser: 200 OK
```

### 3. Security Boundaries
> [!IMPORTANT]
> **Gateway-as-Gatekeeper Principle**
> - **External Traffic**: MUST go through Gateway.
> - **Gateway**: The ONLY service that validates JWTs.
> - **Internal Services**: Trust `X-Tenant-Id` and `X-User-Id` headers from Gateway.
> - **Network**: Internal services should not be exposed publicly.

---

## 🔍 Observability

The platform uses **OpenTelemetry** and **AWS X-Ray** for distributed tracing.

### Tracing Flow
1. **Gateway** receives request → Starts Trace ID.
2. **Services** propagate Trace ID (B3/W3C format).
3. **OTEL Collector** gathers spans.
4. **AWS X-Ray** visualizes the full request path.

### Configuration
```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0 # 100% in Dev
```
