-- V1: Entries Schema with tenant_id
-- Single source of truth - used for both personal_shared and org tenant DBs
-- ============================================================================

-- ============================================================================
-- ENTRIES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS entries (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    entry_key VARCHAR(255) NOT NULL,
    entry_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    UNIQUE(tenant_id, entry_key)
);

-- Indexes for performance
CREATE INDEX idx_entries_tenant ON entries(tenant_id);
CREATE INDEX idx_entries_key ON entries(entry_key);
CREATE INDEX idx_entries_tenant_key ON entries(tenant_id, entry_key);
CREATE INDEX idx_entries_created_at ON entries(created_at DESC);

COMMENT ON TABLE entries IS 'Key-value entries with tenant isolation';
COMMENT ON COLUMN entries.tenant_id IS 'Tenant identifier for data isolation';
COMMENT ON COLUMN entries.entry_key IS 'Key unique within tenant';
