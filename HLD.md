┌─────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL LAYER                                     │
│  ┌──────────────┐                                                            │
│  │   Angular    │  User Interface (Port 4200)                               │
│  │   Frontend   │  - Login/Signup UI                                        │
│  └──────┬───────┘  - Dashboard & CRUD Operations                            │
│         │          - HTTP Client with JWT Bearer Token                      │
└─────────┼──────────────────────────────────────────────────────────────────┘
│
│ HTTP + JWT Token
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY LAYER (Port 8080)                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Spring Cloud Gateway                                               │    │
│  │  ┌──────────────────────────────────────────────────────────────┐  │    │
│  │  │  1. JWT Validation (Cognito JWKS)                            │  │    │
│  │  │  2. Extract tenant_id from cognito:groups                    │  │    │
│  │  │  3. Add Headers: X-Tenant-Id, X-User-Id, X-Authorities       │  │    │
│  │  │  4. Circuit Breaker & Retry Logic                            │  │    │
│  │  │  5. CORS Configuration                                        │  │    │
│  │  └──────────────────────────────────────────────────────────────┘  │    │
│  │                                                                     │    │
│  │  Routes:                                                            │    │
│  │  • /auth/**  → lb://auth-service                                   │    │
│  │  • /api/**   → lb://backend-service                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────┬───────────────────────────────────┬───────────────────────────────┘
│                                   │
│                                   │
┌─────────▼─────────────┐          ┌──────────▼────────────────────────────────┐
│  SERVICE REGISTRY     │          │     MICROSERVICES LAYER                   │
│  (Port 8761)          │          │                                           │
│  ┌─────────────────┐  │          │  ┌────────────────────────────────────┐  │
│  │ Eureka Server   │◄─┼──────────┼──┤  All services register here        │  │
│  │                 │  │          │  │  with heartbeat every 30s           │  │
│  │ - Service       │  │          │  └────────────────────────────────────┘  │
│  │   Discovery     │  │          │                                           │
│  │ - Health Checks │  │          │  ┌────────────────────────────────────┐  │
│  │ - Load Balancer │  │          │  │  Auth-Service (Port 8081)          │  │
│  └─────────────────┘  │          │  │  ┌──────────────────────────────┐  │  │
└───────────────────────┘          │  │  │ • OAuth2 Client (Cognito)    │  │  │
│  │  │ • Login/Signup/Logout        │  │  │
│  │  │ • JWT Token Management       │  │  │
│  │  │ • User Registration          │  │  │
│  │  │ • Tenant Provisioning Trigger│  │  │
│  │  └──────────────────────────────┘  │  │
│  └──────────┬─────────────────────────┘  │
│             │                             │
│  ┌──────────▼─────────────────────────┐  │
│  │  Backend-Service (Port 8082)       │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │ • Multi-Tenant Data Access   │  │  │
│  │  │ • Schema-per-Tenant          │  │  │
│  │  │ • CRUD APIs                  │  │  │
│  │  │ • Business Logic             │  │  │
│  │  │ • Admin Tenant Provisioning  │  │  │
│  │  └──────────┬───────────────────┘  │  │
│  └─────────────┼──────────────────────┘  │
└────────────────┼─────────────────────────┘
│
┌───────────────────────────────────────────────────┼─────────────────────────┐
│                         DATA LAYER                │                          │
│                                                   │                          │
│  ┌────────────────────────────────────────────────▼──────────────────────┐  │
│  │  PostgreSQL Database (Port 5432)                                      │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  Database: awsinfra                                             │  │  │
│  │  │  ┌──────────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  public schema (System Tables)                           │  │  │  │
│  │  │  │  • tenants (tenant registry)                             │  │  │  │
│  │  │  │  • tenant_audit_log                                      │  │  │  │
│  │  │  └──────────────────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  tenant_acme schema (Tenant ACME data)                   │  │  │  │
│  │  │  │  • entries                                               │  │  │  │
│  │  │  │  • notes                                                 │  │  │  │
│  │  │  │  • ... (all business tables)                            │  │  │  │
│  │  │  └──────────────────────────────────────────────────────────┘  │  │  │
│  │  │  ┌──────────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  tenant_xyz schema (Tenant XYZ data)                     │  │  │  │
│  │  │  │  • entries                                               │  │  │  │
│  │  │  │  • notes                                                 │  │  │  │
│  │  │  └──────────────────────────────────────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL SERVICES                                     │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  AWS Cognito                                                       │     │
│  │  • User Pool (Authentication)                                      │     │
│  │  • User Groups (tenant_acme, tenant_xyz)                          │     │
│  │  • JWKS Endpoint (JWT validation)                                 │     │
│  │  • OAuth2 Authorization Server                                    │     │
│  └────────────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Complete End-to-End Flows

### **Flow 1: New Tenant Signup** (One-time setup)
```
┌──────────┐    ┌─────────┐    ┌──────────┐    ┌─────────┐    ┌──────────┐
│ Angular  │    │ Gateway │    │   Auth   │    │ Cognito │    │ Backend  │
│ Frontend │    │ Service │    │ Service  │    │         │    │ Service  │
└────┬─────┘    └────┬────┘    └────┬─────┘    └────┬────┘    └────┬─────┘
│               │              │               │               │
│ 1. POST /auth/signup         │               │               │
│ {email, password,            │               │               │
│  companyName: "Acme Corp"}   │               │               │
├──────────────►│              │               │               │
│               │              │               │               │
│               │ 2. Route to auth-service     │               │
│               ├─────────────►│               │               │
│               │              │               │               │
│               │              │ 3. Create user in Cognito     │
│               │              ├──────────────►│               │
│               │              │               │               │
│               │              │ 4. User created (user-uuid)   │
│               │              │◄──────────────┤               │
│               │              │               │               │
│               │              │ 5. POST /api/admin/tenants   │
│               │              │ {tenantId: "acme",           │
│               │              │  tenantName: "Acme Corp"}    │
│               │              ├──────────────────────────────►│
│               │              │               │               │
│               │              │               │ 6. Create tenant record
│               │              │               │    in public.tenants
│               │              │               │               │
│               │              │               │ 7. CREATE SCHEMA
│               │              │               │    tenant_acme
│               │              │               │               │
│               │              │               │ 8. Run Flyway migrations
│               │              │               │    in tenant_acme schema
│               │              │               │    (creates entries table)
│               │              │               │               │
│               │              │ 9. Tenant provisioned         │
│               │              │◄──────────────────────────────┤
│               │              │               │               │
│               │              │ 10. Create Cognito group      │
│               │              │     "tenant_acme"             │
│               │              ├──────────────►│               │
│               │              │               │               │
│               │              │ 11. Add user to group         │
│               │              │     "tenant_acme"             │
│               │              ├──────────────►│               │
│               │              │               │               │
│               │ 12. Signup successful        │               │
│               │◄─────────────┤               │               │
│               │              │               │               │
│ 13. Success  │               │               │               │
│◄──────────────┤              │               │               │
│               │              │               │               │
```

---

### **Flow 2: User Login** (Existing tenant)
```
┌──────────┐    ┌─────────┐    ┌──────────┐    ┌─────────┐
│ Angular  │    │ Gateway │    │   Auth   │    │ Cognito │
│ Frontend │    │ Service │    │ Service  │    │         │
└────┬─────┘    └────┬────┘    └────┬─────┘    └────┬────┘
│               │              │               │
│ 1. POST /auth/login          │               │
│ {email, password}            │               │
├──────────────►│              │               │
│               │              │               │
│               │ 2. Route to auth-service     │
│               ├─────────────►│               │
│               │              │               │
│               │              │ 3. Authenticate user
│               │              ├──────────────►│
│               │              │               │
│               │              │ 4. Return JWT token
│               │              │    Claims:    │
│               │              │    - sub: user-uuid
│               │              │    - cognito:groups:
│               │              │      ["tenant_acme"]
│               │              │    - email    │
│               │              │◄──────────────┤
│               │              │               │
│               │ 5. Return JWT to gateway     │
│               │◄─────────────┤               │
│               │              │               │
│ 6. JWT Token │               │               │
│◄──────────────┤              │               │
│               │              │               │
│ 7. Store JWT in localStorage │               │
│               │              │               │
```

---

### **Flow 3: CRUD Operation** (Authenticated request)
```
┌──────────┐    ┌─────────┐    ┌──────────┐    ┌──────────┐
│ Angular  │    │ Gateway │    │ Backend  │    │PostgreSQL│
│ Frontend │    │ Service │    │ Service  │    │          │
└────┬─────┘    └────┬────┘    └────┬─────┘    └────┬─────┘
│               │              │               │
│ 1. POST /api/entries         │               │
│ Authorization: Bearer <JWT>  │               │
│ {key: "api_key",             │               │
│  value: "sk-123"}            │               │
├──────────────►│              │               │
│               │              │               │
│               │ 2. Validate JWT signature    │
│               │    using Cognito JWKS        │
│               │              │               │
│               │ 3. Extract from JWT:         │
│               │    - tenant_id: "acme"       │
│               │    - user_id: "user-uuid"    │
│               │    - email: "user@acme.com"  │
│               │              │               │
│               │ 4. Add headers & route       │
│               │    X-Tenant-Id: acme         │
│               │    X-User-Id: user-uuid      │
│               │    X-Email: user@acme.com    │
│               ├─────────────►│               │
│               │              │               │
│               │              │ 5. TenantContextFilter
│               │              │    reads headers
│               │              │    TenantContext.setTenantId("acme")
│               │              │               │
│               │              │ 6. Hibernate resolves
│               │              │    schema: tenant_acme
│               │              │               │
│               │              │ 7. INSERT INTO
│               │              │    tenant_acme.entries
│               │              │    (key, value, ...)
│               │              ├──────────────►│
│               │              │               │
│               │              │ 8. Row inserted
│               │              │◄──────────────┤
│               │              │               │
│               │ 9. EntryResponseDto          │
│               │◄─────────────┤               │
│               │              │               │
│ 10. Success  │               │               │
│◄──────────────┤              │               │
│               │              │               │
```

---

## 📂 Complete Backend-Service Structure (From Scratch)
```
backend-service/
├── pom.xml
├── Dockerfile
├── spotbugs-exclude.xml
└── src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── learning/
│   │           └── backendservice/
│   │               ├── BackendServiceApplication.java
│   │               │
│   │               ├── config/
│   │               │   ├── JpaAuditConfig.java
│   │               │   ├── MultiTenantDataSourceConfig.java
│   │               │   ├── OpenApiConfig.java
│   │               │   ├── SecurityConfig.java
│   │               │   └── TenantContextFilter.java
│   │               │
│   │               ├── controller/
│   │               │   ├── EntryController.java
│   │               │   └── TenantAdminController.java
│   │               │
│   │               ├── dto/
│   │               │   ├── EntryRequestDto.java
│   │               │   ├── EntryResponseDto.java
│   │               │   ├── ErrorResponse.java
│   │               │   ├── TenantRequestDto.java
│   │               │   └── TenantResponseDto.java
│   │               │
│   │               ├── entity/
│   │               │   ├── Entry.java
│   │               │   └── Tenant.java
│   │               │
│   │               ├── exception/
│   │               │   ├── GlobalExceptionHandler.java
│   │               │   ├── ResourceNotFoundException.java
│   │               │   └── TenantProvisioningException.java
│   │               │
│   │               ├── repository/
│   │               │   ├── EntryRepository.java
│   │               │   └── TenantRepository.java
│   │               │
│   │               ├── security/
│   │               │   └── TenantContext.java
│   │               │
│   │               └── service/
│   │                   ├── EntryService.java
│   │                   ├── EntryServiceImpl.java
│   │                   ├── TenantService.java
│   │                   └── TenantServiceImpl.java
│   │
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── db/
│           ├── migration/
│           │   └── V1__create_tenant_registry.sql
│           └── tenant-template/
│               └── V1__tenant_initial_schema.sql
│
└── test/
└── java/
└── com/
└── learning/
└── backendservice/
├── BackendServiceApplicationTests.java
├── controller/
│   └── EntryControllerTest.java
├── service/
│   └── EntryServiceTest.java
└── repository/
└── EntryRepositoryTest.java
