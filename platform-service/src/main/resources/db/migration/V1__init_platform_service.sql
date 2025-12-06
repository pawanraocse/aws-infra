-- V1: Enhanced schema for multi-tenant platform with B2B/B2C and SSO support
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    -- Core identity
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    
    -- Storage configuration
    storage_mode VARCHAR(32) NOT NULL,
    jdbc_url TEXT,
    db_user_secret_ref TEXT,
    db_user_password_enc TEXT,
    
    -- Tenant type and limits
    tenant_type VARCHAR(32) NOT NULL DEFAULT 'PERSONAL', -- PERSONAL or ORGANIZATION
    owner_email VARCHAR(255),
    max_users INTEGER NOT NULL DEFAULT 1,
    
    -- SLA and versioning
    sla_tier VARCHAR(32) NOT NULL,
    last_migration_version VARCHAR(64),
    
    -- SSO/IDP configuration (for B2B)
    sso_enabled BOOLEAN DEFAULT FALSE,
    idp_type VARCHAR(64),
    idp_metadata_url TEXT,
    idp_entity_id VARCHAR(512),
    idp_config_json JSONB,
    
    -- Security & Compliance
    encryption_key_id VARCHAR(255),
    data_residency VARCHAR(64),
    
    -- Performance & Scalability
    db_shard VARCHAR(64) DEFAULT 'shard-1',
    read_replica_url TEXT,
    connection_pool_min INTEGER DEFAULT 2,
    connection_pool_max INTEGER DEFAULT 10,
    
    -- Lifecycle Management
    trial_ends_at TIMESTAMPTZ,
    subscription_status VARCHAR(32) DEFAULT 'TRIAL',
    archived_at TIMESTAMPTZ,
    archived_to_s3 BOOLEAN DEFAULT FALSE,
    
    -- Organization Profile (for company customization)
    company_name VARCHAR(255),
    industry VARCHAR(100),
    company_size VARCHAR(20),
    website VARCHAR(500),
    logo_url VARCHAR(1000),
    
    -- Timestamps  
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN tenant.company_name IS 'Official company name (editable by tenant admins)';
COMMENT ON COLUMN tenant.industry IS 'Industry sector (Technology, Healthcare, Finance, etc.)';
COMMENT ON COLUMN tenant.company_size IS 'Employee count range (1-10, 11-50, 51-200, 201-500, 501-1000, 1001+)';
COMMENT ON COLUMN tenant.website IS 'Company website URL';
COMMENT ON COLUMN tenant.logo_url IS 'URL to company logo image';


-- Indexes for performance
CREATE INDEX idx_tenant_type ON tenant(tenant_type);
CREATE INDEX idx_tenant_owner ON tenant(owner_email);
CREATE INDEX idx_tenant_status ON tenant(status);
CREATE INDEX idx_tenant_sso ON tenant(sso_enabled) WHERE sso_enabled = TRUE;
CREATE INDEX idx_tenant_shard ON tenant(db_shard);
CREATE INDEX idx_tenant_subscription ON tenant(subscription_status);
CREATE INDEX idx_tenant_trial ON tenant(trial_ends_at) WHERE subscription_status = 'TRIAL';

-- Audit log for compliance (GDPR, HIPAA, SOC2)
CREATE TABLE IF NOT EXISTS tenant_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    request_id VARCHAR(64),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant ON tenant_audit_log(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_action ON tenant_audit_log(action);
CREATE INDEX idx_audit_user ON tenant_audit_log(user_id, timestamp DESC);
CREATE INDEX idx_audit_timestamp ON tenant_audit_log(timestamp DESC);

-- Usage metrics for cost allocation
CREATE TABLE IF NOT EXISTS tenant_usage_metrics (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    metric_date DATE NOT NULL,
    api_calls BIGINT DEFAULT 0,
    storage_mb BIGINT DEFAULT 0,
    data_transfer_mb BIGINT DEFAULT 0,
    compute_hours DECIMAL(10,2) DEFAULT 0,
    UNIQUE(tenant_id, metric_date)
);

CREATE INDEX idx_usage_tenant_date ON tenant_usage_metrics(tenant_id, metric_date DESC);
CREATE INDEX idx_usage_date ON tenant_usage_metrics(metric_date DESC);
