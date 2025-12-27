# 🏭 SaaS Factory Template

**Build any SaaS in days, not months.** A production-ready multi-tenant foundation with Auth, Billing, Organizations, and RBAC built-in.

## What You Get

| Feature | Status | Description |
|---------|--------|-------------|
| **Multi-Tenant Auth** | ✅ | Cognito-powered signup, login, SSO-ready |
| **Database-per-Tenant** | ✅ | Complete data isolation with dynamic routing |
| **Role-Based Access** | ✅ | Admin/Editor/Viewer roles with permissions |
| **Stripe Billing** | ✅ | Subscriptions, tiers, customer portal |
| **Organization Management** | ✅ | Invite users, manage teams |
| **API Gateway** | ✅ | Security, routing, tenant context injection |

## Use Cases

This template is perfect for building:
- 🖼️ **ImageKit** - Media management SaaS
- 👥 **CRM** - Customer relationship management
- 📊 **Analytics Platform** - Multi-tenant dashboards
- 📁 **DAM** - Digital asset management
- 👔 **HR/Employee Management** - Workforce tools

## Quick Start

```bash
# 1. Deploy AWS infrastructure (Cognito, Lambda)
cd terraform && terraform init && terraform apply

# 2. Start all services
docker-compose up -d

# 3. Create system admin
./scripts/bootstrap-system-admin.sh admin@example.com "Password123!"

# 4. Access the app
open http://localhost:4200
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (Angular)                      │
├─────────────────────────────────────────────────────────────┤
│                    Gateway (Port 8080)                       │
│              JWT Validation • Routing • Headers              │
├──────────┬──────────┬────────────┬──────────────────────────┤
│  Auth    │ Platform │  Backend   │      Your Service        │
│  :8081   │  :8083   │   :8082    │        :808X             │
├──────────┴──────────┴────────────┴──────────────────────────┤
│              PostgreSQL (per-tenant databases)               │
└─────────────────────────────────────────────────────────────┘
```

## Adding Your Service

1. **Copy the backend-service template**
   ```bash
   cp -r backend-service/ my-service/
   ```

2. **Update configuration**
   ```yaml
   # application.yml
   spring.application.name: my-service
   server.port: 8084
   ```

3. **Add your domain logic**
   - Replace `Entry` entity with your domain (Order, Product, Task)
   - Use `@RequirePermission` for authorization
   - Multi-tenant routing is automatic via `X-Tenant-Id` header

4. **Register in docker-compose.yml**

See [HLD.md - Adding Your Service](HLD.md#-adding-your-own-service) for details.

## Project Structure

```
├── frontend/          # Angular app
├── gateway-service/   # API gateway (security, routing)
├── auth-service/      # Authentication & authorization
├── platform-service/  # Tenants, orgs, billing
├── backend-service/   # ← REPLACE THIS with your domain
├── common-infra/      # Shared multi-tenant infrastructure
├── terraform/         # AWS resources (Cognito, Lambda)
└── docker-compose.yml # Local development
```

## Documentation

| Document | Purpose |
|----------|---------|
| [HLD.md](HLD.md) | Architecture, design decisions, how-to guides |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Feature status, future plans |
| [docs/STRIPE_BILLING.md](docs/STRIPE_BILLING.md) | Billing integration guide |
| [terraform/README.md](terraform/README.md) | AWS infrastructure setup |

## Configuration

Create a `.env` file for local development:

```bash
# Stripe (optional - for billing)
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_STARTER=price_...
STRIPE_PRICE_PRO=price_...
```

## Tech Stack

- **Backend:** Java 21, Spring Boot 3.3, Spring Cloud Gateway
- **Frontend:** Angular 19, PrimeNG
- **Database:** PostgreSQL (database-per-tenant)
- **Auth:** AWS Cognito + Lambda
- **Billing:** Stripe
- **Infrastructure:** Docker, Terraform

## Philosophy

> *"Focus on the 20% that earns money. We handle the 80% that doesn't."*

This template provides all the boring-but-essential infrastructure so you can focus on your unique business logic.

---

**License:** MIT  
**Version:** 1.0.0
