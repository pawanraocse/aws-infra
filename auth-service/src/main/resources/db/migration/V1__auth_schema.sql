-- V1: Authorization Schema with tenant_id
-- Single source of truth - used for both personal_shared and org tenant DBs
-- ============================================================================

-- ============================================================================
-- 1. ROLES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    scope VARCHAR(32) NOT NULL CHECK (scope IN ('PLATFORM', 'TENANT')),
    access_level VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_roles_tenant ON roles(tenant_id);
CREATE INDEX idx_roles_scope ON roles(scope);

COMMENT ON TABLE roles IS 'Predefined organization roles';
COMMENT ON COLUMN roles.tenant_id IS 'Tenant identifier for data isolation';

-- ============================================================================
-- 2. USER_ROLES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    assigned_by VARCHAR(255),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    UNIQUE(tenant_id, user_id, role_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_tenant ON user_roles(tenant_id);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

-- ============================================================================
-- 3. USERS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    avatar_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED')),
    source VARCHAR(32) NOT NULL DEFAULT 'COGNITO' CHECK (source IN ('COGNITO', 'SAML', 'OIDC', 'MANUAL', 'INVITATION')),
    first_login_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- ============================================================================
-- 4. INVITATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    invited_by VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_invitations_tenant ON invitations(tenant_id);
CREATE INDEX idx_invitations_email ON invitations(email);
CREATE INDEX idx_invitations_token ON invitations(token);

-- ============================================================================
-- 5. GROUP_ROLE_MAPPINGS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS group_role_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    external_group_id VARCHAR(512) NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    priority INTEGER DEFAULT 0,
    auto_assign BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    UNIQUE(tenant_id, external_group_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_grm_tenant ON group_role_mappings(tenant_id);
CREATE INDEX idx_grm_role ON group_role_mappings(role_id);

-- ============================================================================
-- 6. ACL_ENTRIES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS acl_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    principal_type VARCHAR(32) NOT NULL,
    principal_id VARCHAR(255),
    role_bundle VARCHAR(32) NOT NULL,
    granted_by VARCHAR(255),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    UNIQUE(tenant_id, resource_id, principal_type, principal_id)
);

CREATE INDEX idx_acl_tenant ON acl_entries(tenant_id);
CREATE INDEX idx_acl_resource ON acl_entries(resource_id);

-- ============================================================================
-- 7. PERMISSIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, resource, action)
);

CREATE INDEX idx_permissions_tenant ON permissions(tenant_id);
CREATE INDEX idx_permissions_resource ON permissions(resource);

-- ============================================================================
-- 8. ROLE_PERMISSIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS role_permissions (
    tenant_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_tenant ON role_permissions(tenant_id);
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);

-- ============================================================================
-- 9. SSO CONFIGURATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS sso_configurations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL UNIQUE,
    sso_enabled BOOLEAN DEFAULT FALSE,
    idp_type VARCHAR(50),
    provider_name VARCHAR(255),
    saml_metadata_url VARCHAR(1024),
    saml_metadata_xml TEXT,
    saml_entity_id VARCHAR(255),
    saml_sso_url VARCHAR(1024),
    saml_certificate TEXT,
    oidc_issuer VARCHAR(1024),
    oidc_client_id VARCHAR(255),
    oidc_client_secret VARCHAR(255),
    oidc_scopes VARCHAR(255) DEFAULT 'openid email profile',
    attribute_mappings JSONB,
    jit_provisioning_enabled BOOLEAN DEFAULT FALSE,
    default_role VARCHAR(100),
    cognito_provider_name VARCHAR(255),
    last_tested_at TIMESTAMPTZ,
    test_status VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sso_configurations_tenant_id ON sso_configurations(tenant_id);

-- ============================================================================
-- 10. AUDIT TRIGGER
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_roles_updated_at
BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invitations_updated_at
BEFORE UPDATE ON invitations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- NOTE: Seed data (roles, permissions) is inserted at provisioning time
-- with the specific tenant_id. See TenantMigrationService.
-- ============================================================================
