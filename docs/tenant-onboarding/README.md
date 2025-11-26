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
