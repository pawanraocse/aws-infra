-- V2__create_entries_table.sql
-- Creates the entries table in the public schema (for default tenant)

CREATE TABLE IF NOT EXISTS entries (
    id BIGSERIAL PRIMARY KEY,
    entry_key VARCHAR(255) NOT NULL UNIQUE,
    entry_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_entries_key ON entries(entry_key);
