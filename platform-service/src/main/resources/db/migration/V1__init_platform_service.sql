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

-- =====================================================
-- USER-TENANT MEMBERSHIPS
-- =====================================================
-- Tracks which users (by email) belong to which tenants.
-- Enables multi-tenant login: one user can access multiple workspaces.
--
-- Rules:
--   - PERSONAL tenant: Max 1 per email (enforced at application level)
--   - ORGANIZATION tenant: Unlimited per email
--   - A user can have both personal + organization memberships
-- =====================================================

CREATE TABLE IF NOT EXISTS user_tenant_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- User identification (email is the primary identifier)
    user_email VARCHAR(255) NOT NULL,
    cognito_user_id VARCHAR(255),  -- Set after first login, used for faster lookups
    
    -- Tenant relationship
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    
    -- Role hint for display in tenant selector (actual permissions in tenant DB)
    role_hint VARCHAR(50) NOT NULL DEFAULT 'member',
    -- Valid values: owner, admin, member, guest
    
    -- Ownership: true if this user created/owns the tenant
    is_owner BOOLEAN NOT NULL DEFAULT false,
    
    -- Default workspace: only ONE per user (enforced by unique partial index)
    is_default BOOLEAN NOT NULL DEFAULT false,
    
    -- Activity tracking for sorting (most recently used first)
    last_accessed_at TIMESTAMPTZ,
    
    -- Membership lifecycle
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    invited_by VARCHAR(255),  -- Email of inviter (null for owners/self-signup)
    
    -- Soft delete support (for invitation revocation / removal)
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    -- Valid values: ACTIVE, REMOVED, SUSPENDED
    
    -- Unique constraint: one membership per user-tenant pair
    CONSTRAINT uk_user_tenant UNIQUE(user_email, tenant_id)
);

-- Performance indexes for common queries
CREATE INDEX idx_utm_email ON user_tenant_memberships(LOWER(user_email)) 
    WHERE status = 'ACTIVE';
CREATE INDEX idx_utm_tenant ON user_tenant_memberships(tenant_id) 
    WHERE status = 'ACTIVE';
CREATE INDEX idx_utm_cognito ON user_tenant_memberships(cognito_user_id) 
    WHERE cognito_user_id IS NOT NULL;
CREATE INDEX idx_utm_owner ON user_tenant_memberships(tenant_id, is_owner) 
    WHERE is_owner = true;
CREATE INDEX idx_utm_last_accessed ON user_tenant_memberships(user_email, last_accessed_at DESC NULLS LAST)
    WHERE status = 'ACTIVE';

-- Ensure only one default workspace per user (partial unique index)
CREATE UNIQUE INDEX idx_utm_single_default 
    ON user_tenant_memberships(LOWER(user_email)) 
    WHERE is_default = true AND status = 'ACTIVE';

-- Comments for documentation
COMMENT ON TABLE user_tenant_memberships IS 
    'Maps users (by email) to their tenant memberships. Enables multi-tenant login flow.';
COMMENT ON COLUMN user_tenant_memberships.role_hint IS 
    'Display hint for tenant selector UI. Actual permissions are managed in tenant database.';
COMMENT ON COLUMN user_tenant_memberships.is_owner IS 
    'True if user created/owns this tenant. Personal tenant owner or organization founder.';
COMMENT ON COLUMN user_tenant_memberships.is_default IS 
    'Users default workspace. Auto-selected during login if only one tenant.';
COMMENT ON COLUMN user_tenant_memberships.status IS 
    'Membership status: ACTIVE, REMOVED (left/kicked), SUSPENDED (temporarily blocked).';

-- =====================================================
-- DELETED ACCOUNTS AUDIT TABLE
-- =====================================================
-- Tracks deleted accounts for re-registration detection and audit.
-- When a tenant is deleted, we keep a record here for:
--   - Re-registration tracking (detect returning users)
--   - Audit compliance (GDPR deletion records)
-- =====================================================

CREATE TABLE IF NOT EXISTS deleted_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    original_tenant_id VARCHAR(64) NOT NULL,
    tenant_type VARCHAR(20) NOT NULL,
    tenant_name VARCHAR(255),
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_by VARCHAR(255)
);

-- Index for fast lookup during re-registration
CREATE INDEX idx_deleted_accounts_email ON deleted_accounts(email);
CREATE INDEX idx_deleted_accounts_tenant_id ON deleted_accounts(original_tenant_id);

COMMENT ON TABLE deleted_accounts IS 'Audit trail for deleted accounts, used for re-registration tracking';
