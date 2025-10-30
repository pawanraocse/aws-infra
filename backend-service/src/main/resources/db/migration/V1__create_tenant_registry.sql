-- V1__create_tenant_registry.sql
-- Creates tenant registry in PUBLIC schema

-- Tenant registry table
CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
    );

-- Indexes
CREATE INDEX idx_tenants_tenant_id ON tenants(tenant_id);
CREATE INDEX idx_tenants_schema_name ON tenants(schema_name);
CREATE INDEX idx_tenants_status ON tenants(status);

-- Audit log table
CREATE TABLE IF NOT EXISTS tenant_audit_log (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    performed_by VARCHAR(255),
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details JSONB,
    FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
    );

CREATE INDEX idx_audit_tenant_id ON tenant_audit_log(tenant_id);
CREATE INDEX idx_audit_performed_at ON tenant_audit_log(performed_at DESC);

-- Comments
COMMENT ON TABLE tenants IS 'Registry of all tenants and their isolated schemas';
COMMENT ON TABLE tenant_audit_log IS 'Audit trail for tenant operations';
