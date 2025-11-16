-- V1: Initial schema for platform-service
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_mode VARCHAR(32) NOT NULL,
    jdbc_url TEXT,
    db_user_secret_ref TEXT,
    db_user_password_enc TEXT,
    sla_tier VARCHAR(32) NOT NULL,
    last_migration_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
