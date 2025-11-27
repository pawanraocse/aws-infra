# Documentation Index

This directory contains all project documentation.

---

## 📖 Main Documentation

### [HLD.md](../HLD.md)
**High-Level Design** - Master architecture document
- System overview & template philosophy
- Service responsibilities & interactions
- Multi-tenancy model
- Security architecture
- Data architecture
- Technology stack
- How to use this template

### [STATUS.md](STATUS.md)
**Project Status** - Current completion state & roadmap
- Days 1-6 completed work
- Next priorities
- Testing status
- Running instructions

---

## 📋 Operational Docs

### [tenant-onboarding/PRODUCTION_READINESS.md](tenant-onboarding/PRODUCTION_READINESS.md)
Production deployment checklist:
- Infrastructure requirements
- Security hardening
- Monitoring & observability
- Backup & disaster recovery
- Performance optimization

---

## 📦 Archived Planning Docs

See `archive/` for pre-implementation planning documents:
- `IMPLEMENTATION_GUIDE.md` - Original 12-15 day plan
- `implementation_plan.md` - Detailed implementation steps
- `GAP_ANALYSIS.md` - Initial gap analysis
- `b2b_flow_steps.md` - B2B flow documentation
- `tenant_onboarding_guide.md` - Onboarding guide

**Note:** These were created during planning phase. Refer to HLD.md and STATUS.md for current reality.

---

## 🚀 Quick Links

- **Architecture**: [HLD.md](../HLD.md)
- **Current Status**: [STATUS.md](STATUS.md)
- **Production Checklist**: [tenant-onboarding/PRODUCTION_READINESS.md](tenant-onboarding/PRODUCTION_READINESS.md)
- **System Tests**: [../system-tests/](../system-tests/)
- **Terraform**: [../terraform/](../terraform/)

---

## 📝 Documentation Philosophy

1. **HLD.md** = Single source of truth for architecture
2. **STATUS.md** = What's done, what's next
3. **PRODUCTION_READINESS.md** = Deployment checklist
4. **Code** = Living documentation via tests & comments

Keep it simple. Avoid redundancy. Update as you build.
