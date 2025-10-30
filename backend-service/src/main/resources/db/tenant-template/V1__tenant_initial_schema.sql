-- V1__tenant_initial_schema.sql
-- Template schema structure for each tenant
-- This will be executed in tenant_xxx schemas

-- Entries table (no tenant_id needed - schema isolation!)
CREATE TABLE entries (
                         id BIGSERIAL PRIMARY KEY,
                         meta_key VARCHAR(255) NOT NULL UNIQUE,
                         meta_value TEXT NOT NULL,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         created_by VARCHAR(255),
                         updated_at TIMESTAMP,
                         updated_by VARCHAR(255)
);

-- Indexes
CREATE INDEX idx_entries_key ON entries(meta_key);
CREATE INDEX idx_entries_created_at ON entries(created_at DESC);

-- Comments
COMMENT ON TABLE entries IS 'Key-value entries for this tenant';
COMMENT ON COLUMN entries.meta_key IS 'Unique key within this tenant';
COMMENT ON COLUMN entries.meta_value IS 'Value associated with the key';
