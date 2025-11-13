-- V1: Initial schema for platform-service
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_mode VARCHAR(32) NOT NULL,
    jdbc_url TEXT,
    db_user_secret_ref TEXT,
    sla_tier VARCHAR(32) NOT NULL,
    last_migration_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenant_migration_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    CONSTRAINT fk_tenant_mh FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE TABLE IF NOT EXISTS policy_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64),
    version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    json_blob JSONB NOT NULL,
    CONSTRAINT fk_tenant_pd FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE TABLE IF NOT EXISTS internal_token_signature_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    current_key_id VARCHAR(64) NOT NULL,
    next_key_id VARCHAR(64),
    rotated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS tenant_quota (
    tenant_id VARCHAR(64) PRIMARY KEY,
    api_call_limit INTEGER,
    storage_limit_gb INTEGER,
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    usage_api_calls INTEGER DEFAULT 0,
    usage_storage_gb INTEGER DEFAULT 0,
    CONSTRAINT fk_tenant_quota FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
