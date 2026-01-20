
# High-Level Design: SaaS Foundation & Project Template

**Version:** 8.0 (Documentation Refactor)
**Last Updated:** 2026-01-17

**Core Mission:** A production-ready, ultra-decoupled foundation for launching multi-tenant SaaS applications in days, not months.

---

## 📚 Documentation Map

The documentation has been split into focused guides for better readability.

| Guide | Description |
|-------|-------------|
| 🚀 **[Quick Start](docs/QUICK_START.md)** | **Start Here!** Prerequisites, deployment, and how to build your service. |
| 🏗️ **[Architecture](docs/ARCHITECTURE.md)** | Service roles, request flows, and technology stack. |
| 🏢 **[Multi-Tenancy](docs/MULTI_TENANCY.md)** | Database-per-tenant isolation, routing, and tiers. |
| 🔐 **[Authentication](docs/AUTHENTICATION.md)** | JWT flows, Signup pipelines, and Login logic. |
| 🛡️ **[Authorization](docs/AUTHORIZATION.md)** | RBAC (Roles), ACLs, and Group Mapping. |
| 📝 **[OpenFGA](docs/OPENFGA.md)** | Fine-grained relationship-based access control (ReBAC). |
| 💳 **[Billing](docs/BILLING.md)** | Stripe integration, subscriptions, and webhooks. |

---

## 🎯 What Is This Project?

This is a **SaaS Factory**. It provides the "Boring 80%" of a SaaS application so you can focus on your unique business value.

### Key Philosophy
1.  **Strict Decoupling:** Infrastructure (Auth, Gateway, Platform) is separate from Business Logic.
2.  **Database-per-Tenant:** Maximum isolation and security.
3.  **Infrastructure as Code:** 100% Terraform-managed.
4.  **Developer Experience:** Local development mirrors production (Docker Compose).

---

## 🚀 Quick Links
- **[Status Tracking](docs/STATUS.md)**
- **[Deployment Guide](docs/QUICK_START.md#%F0%9F%9B%A0-deployment--configuration-flow)**

---

*For historical context or detailed diagrams previously in this file, refer to the specific sub-documents linked above.*
