-- V1: Initial schema for tenant database
-- This schema is applied to each tenant's dedicated database

-- Entries table (no tenant_id column needed - DB isolation)
CREATE TABLE IF NOT EXISTS entries (
    id BIGSERIAL PRIMARY KEY,
    entry_key VARCHAR(255) NOT NULL UNIQUE,
    entry_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255)
);

-- Indexes for performance
CREATE INDEX idx_entries_key ON entries(entry_key);
CREATE INDEX idx_entries_created_at ON entries(created_at DESC);
CREATE INDEX idx_entries_created_by ON entries(created_by);

-- Comments for documentation
COMMENT ON TABLE entries IS 'Key-value entries specific to this tenant';
COMMENT ON COLUMN entries.entry_key IS 'Unique key within this tenant';
COMMENT ON COLUMN entries.entry_value IS 'Value associated with the key';
