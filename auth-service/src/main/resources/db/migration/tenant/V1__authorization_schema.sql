-- V1: Simplified Authorization Schema
-- Predefined Role Bundles + Resource ACLs
-- ============================================================================

-- ============================================================================
-- 1. ROLES TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    scope VARCHAR(32) NOT NULL CHECK (scope IN ('PLATFORM', 'TENANT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_roles_scope ON roles(scope);

COMMENT ON TABLE roles IS 'Predefined organization roles';
COMMENT ON COLUMN roles.scope IS 'PLATFORM for super-admin, TENANT for tenant roles';

-- ============================================================================
-- 2. USER_ROLES TABLE (User Role Assignments)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    assigned_by VARCHAR(255),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    UNIQUE(user_id, role_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

COMMENT ON TABLE user_roles IS 'Assigns roles to users';
COMMENT ON COLUMN user_roles.user_id IS 'Cognito user ID (sub claim)';

-- ============================================================================
-- 3. USERS TABLE (Tenant User Registry)
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) PRIMARY KEY,
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

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

COMMENT ON TABLE users IS 'Registry of all users in the tenant';

-- ============================================================================
-- 4. INVITATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

CREATE INDEX idx_invitations_email ON invitations(email);
CREATE INDEX idx_invitations_token ON invitations(token);
CREATE INDEX idx_invitations_status ON invitations(status);

-- ============================================================================
-- 5. GROUP_ROLE_MAPPINGS TABLE (SSO Group to Role Mapping)
-- ============================================================================
CREATE TABLE IF NOT EXISTS group_role_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_group_id VARCHAR(512) NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    priority INTEGER DEFAULT 0,
    auto_assign BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    UNIQUE(external_group_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_grm_role ON group_role_mappings(role_id);
CREATE INDEX idx_grm_group ON group_role_mappings(external_group_id);

COMMENT ON TABLE group_role_mappings IS 'Maps SSO groups to roles for auto-assignment';

-- ============================================================================
-- 6. ACL_ENTRIES TABLE (Resource-Level Permissions)
-- ============================================================================
-- Google Drive style sharing for folders/files
CREATE TABLE IF NOT EXISTS acl_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL,
    resource_type VARCHAR(64) NOT NULL,       -- FOLDER, FILE, PROJECT
    principal_type VARCHAR(32) NOT NULL,      -- USER, GROUP, PUBLIC
    principal_id VARCHAR(255),                -- User/Group ID or null for PUBLIC
    role_bundle VARCHAR(32) NOT NULL,         -- VIEWER, CONTRIBUTOR, EDITOR, MANAGER
    granted_by VARCHAR(255),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    UNIQUE(resource_id, principal_type, principal_id)
);

CREATE INDEX idx_acl_resource ON acl_entries(resource_id);
CREATE INDEX idx_acl_principal ON acl_entries(principal_id) WHERE principal_id IS NOT NULL;
CREATE INDEX idx_acl_resource_type ON acl_entries(resource_type);

COMMENT ON TABLE acl_entries IS 'Resource-level ACL for folder/file sharing';
COMMENT ON COLUMN acl_entries.role_bundle IS 'VIEWER=read, CONTRIBUTOR=read+upload, EDITOR=edit, MANAGER=full+share';

-- ============================================================================
-- 7. SEED DATA - PREDEFINED ROLES
-- ============================================================================
INSERT INTO roles (id, name, description, scope) VALUES
('super-admin', 'SUPER_ADMIN', 'Platform administrator with full system access', 'PLATFORM'),
('admin', 'ADMIN', 'Tenant administrator with full tenant access', 'TENANT'),
('editor', 'EDITOR', 'Can read, edit, delete, and share resources', 'TENANT'),
('viewer', 'VIEWER', 'Read-only access to resources', 'TENANT'),
('guest', 'GUEST', 'Limited access to shared resources only', 'TENANT')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 8. AUDIT TRIGGER
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_roles_updated_at
BEFORE UPDATE ON roles
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invitations_updated_at
BEFORE UPDATE ON invitations
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- SCHEMA COMPLETE - Simplified Permission Model
-- ============================================================================
-- Org Roles: admin (full access), editor, viewer, guest (predefined capabilities)
-- Resource ACLs: Fine-grained sharing via acl_entries table
-- Admin Access: Checked by role, not granular permissions
-- ============================================================================
