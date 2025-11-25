# Tenant Onboarding Documentation

This directory contains all planning and implementation documentation for the multi-tenant onboarding system.

## Documents

### 1. [implementation_plan.md](./implementation_plan.md) - **START HERE**
Complete implementation guide for unified B2B and B2C tenant onboarding.

**Contents:**
- Architecture overview (unified approach)
- Database schema changes
- API endpoints specification
- B2C personal signup flow
- B2B organization signup flow
- Common tenant creation script
- Testing plan
- Implementation checklist

### 2. [b2b_flow_steps.md](./b2b_flow_steps.md)
Detailed step-by-step breakdown of the B2B enterprise onboarding flow.

**Contents:**
- 7-step B2B onboarding process
- Who does what (actors and responsibilities)
- Timeline and duration
- FAQ

### 3. [tenant_onboarding_guide.md](./tenant_onboarding_guide.md)
Comprehensive guide covering both B2B and B2C models with detailed comparisons.

**Contents:**
- Hybrid B2B + B2C architecture
- Tenant types (PERSONAL vs ORGANIZATION)
- User-to-tenant relationships
- Security rules
- Database schema
- Pricing tiers

---

## Quick Reference

### B2C Flow (Self-Service)
```
User fills form → POST /api/signup/personal → 
Create tenant (auto) → Create user → Email verification → Login
```

### B2B Flow (Admin-Controlled)
```
Company fills form → POST /api/signup/organization → 
Optional approval → Create tenant → Create admin user → Send credentials → Admin invites team
```

### Common Backend
Both flows call the same Platform Service API:
```bash
POST /platform/api/tenants
{
  "id": "<tenant-id>",
  "tenantType": "PERSONAL" | "ORGANIZATION",
  ...
}
```

---

## Implementation Status

- [ ] Phase 1: Database schema updates
- [ ] Phase 2: Signup API endpoints
- [ ] Phase 3: B2C personal signup flow
- [ ] Phase 4: B2B organization signup flow
- [ ] Phase 5: Testing and validation

---

## Related Documentation

- [HLD.md](../HLD.md) - High-level design
- [terraform/README.md](../terraform/README.md) - Infrastructure setup
- [scripts/README.md](../scripts/README.md) - Utility scripts

---

Last Updated: 2025-11-25
