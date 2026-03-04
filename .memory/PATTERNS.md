# PATTERNS
_Established code patterns. Agents must follow these when implementing similar features._
_Last updated: 2026-03-03_

---

## Backend Patterns

### REST Controller Pattern
- Controller → Service (interface) → ServiceImpl → Repository
- Controllers handle only HTTP concerns (request mapping, validation, response codes)
- Business logic lives exclusively in Service layer
- Example: `AuthController` → `CreditService` → `CreditServiceImpl` → `CreditTransactionRepository`

### Pipeline Pattern (Auth Service — Signup)
- Define actions via `SignupAction` interface
- Execute via `SignupPipeline` orchestrator
- To add new signup steps: implement `SignupAction`, register in pipeline
- **Do NOT** modify existing actions for new requirements (Open/Closed)

### Strategy Pattern (Backend Service — Export)
- Define strategies via `ExportStrategy` interface
- Example: `Rule37ExcelExportStrategy`
- To add new export formats: implement `ExportStrategy`, register via DI
- **Do NOT** add if/else branches to existing export code

### Multi-Tenant Data Access
- Database-per-tenant isolation strategy
- Every entity MUST include `tenant_id` column
- `TenantFilter` (common-infra) auto-applies tenant scoping
- `TenantAuditingListener` auto-fills `tenant_id` on persist
- Never bypass tenant filtering without explicit DECISION log

### Credit System (Auth Service)
- `CreditService.consume()` is idempotent via `reference_id` + `reference_type`
- Always call `CreditService.validate()` before `consume()`
- Inter-service credit consumption: use `CreditClient` (WebClient-based)

### Platform Service Patterns
- Tenant & Organization management lives in `platform-service`
- Stripe subscriptions handled by `payment-service`
- OpenFGA for fine-grained authorization

---

## Frontend Patterns

### Feature Module Structure
```
src/app/features/<domain>/
├── components/     # domain-specific components
├── services/       # domain-specific API services
├── models/         # TypeScript interfaces/types
└── <domain>.routes.ts
```

### API Service Pattern
- Extend `BaseApiService` for standard CRUD
- Use `HttpClientService` (with auth interceptor) — never use raw `HttpClient`

### Auth Guard Pattern
- Route protection: `authGuard` (logged in), `guestGuard` (logged out), `adminGuard` (super-admin)
- Backend enforcement: `@RequirePermission` annotation + `AuthorizationAspect`

---

## Infrastructure Patterns

### Terraform Module Pattern
- Each AWS resource type gets its own module folder (kebab-case)
- Shared variables in `variables.tf`, outputs in `outputs.tf`
- Environment-specific overrides in `terraform/envs/<env>/`

### Database Migration Pattern
- Flyway with per-service history tables (`flyway_schema_history_auth`, etc.)
- Naming: `V<N>__<Description>.sql`
- Never modify existing migrations — always create new ones

### Docker Compose Pattern
- `docker-compose.base.yml` — shared infra (DB, Redis, etc.)
- `docker-compose.yml` — full local dev stack
- `docker-compose.budget.yml` — budget AWS deployment
