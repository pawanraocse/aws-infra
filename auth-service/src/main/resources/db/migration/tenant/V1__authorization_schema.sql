-- V2: Authorization Schema for PBAC (Permission-Based Access Control)
-- Creates tables: roles, permissions, role_permissions, user_roles

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

COMMENT ON TABLE roles IS 'Defines user roles for authorization';
COMMENT ON COLUMN roles.scope IS 'PLATFORM for super-admin, TENANT for tenant-level roles';

-- ============================================================================
-- 2. PERMISSIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(64) PRIMARY KEY,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(resource, action)
);

CREATE INDEX idx_permissions_resource ON permissions(resource);

COMMENT ON TABLE permissions IS 'Defines granular permissions (resource:action format)';
COMMENT ON COLUMN permissions.resource IS 'Resource type (e.g., entry, user, tenant, billing)';
COMMENT ON COLUMN permissions.action IS 'Action type (e.g., read, create, update, delete, manage)';

-- ============================================================================
-- 3. ROLE_PERMISSIONS TABLE (Many-to-Many)
-- ============================================================================
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id VARCHAR(64) NOT NULL,
    permission_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS 'Maps roles to their granted permissions';

-- ============================================================================
-- 4. USER_ROLES TABLE (User Role Assignments per Tenant)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    assigned_by VARCHAR(255),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    UNIQUE(user_id, tenant_id, role_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_lookup ON user_roles(user_id, tenant_id);
CREATE INDEX idx_user_roles_tenant ON user_roles(tenant_id);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);

COMMENT ON TABLE user_roles IS 'Assigns roles to users within specific tenants';
COMMENT ON COLUMN user_roles.user_id IS 'Cognito user ID (sub claim from JWT)';
COMMENT ON COLUMN user_roles.tenant_id IS 'Tenant ID for tenant-scoped roles';
COMMENT ON COLUMN user_roles.assigned_by IS 'User ID who assigned this role';
COMMENT ON COLUMN user_roles.expires_at IS 'Optional expiration timestamp for temporary role assignments';

-- ============================================================================
-- 5. SEED DATA - ROLES
-- ============================================================================
INSERT INTO roles (id, name, description, scope) VALUES
('super-admin', 'SUPER_ADMIN', 'Platform super administrator with full system access', 'PLATFORM'),
('tenant-admin', 'TENANT_ADMIN', 'Tenant administrator with full control over tenant resources and users', 'TENANT'),
('tenant-user', 'TENANT_USER', 'Standard tenant user with CRUD access to resources', 'TENANT'),
('tenant-guest', 'TENANT_GUEST', 'Read-only guest user with limited access', 'TENANT')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 6. SEED DATA - PERMISSIONS
-- ============================================================================
-- Entry permissions
INSERT INTO permissions (id, resource, action, description) VALUES
('entry-read', 'entry', 'read', 'View entries'),
('entry-create', 'entry', 'create', 'Create new entries'),
('entry-update', 'entry', 'update', 'Update existing entries'),
('entry-delete', 'entry', 'delete', 'Delete entries')
ON CONFLICT (resource, action) DO NOTHING;

-- User management permissions
INSERT INTO permissions (id, resource, action, description) VALUES
('user-read', 'user', 'read', 'View user information'),
('user-create', 'user', 'create', 'Create new users'),
('user-update', 'user', 'update', 'Update user information'),
('user-delete', 'user', 'delete', 'Delete users'),
('user-manage', 'user', 'manage', 'Full user management (create, update, delete, assign roles)')
ON CONFLICT (resource, action) DO NOTHING;

-- Tenant management permissions
INSERT INTO permissions (id, resource, action, description) VALUES
('tenant-read', 'tenant', 'read', 'View tenant settings and information'),
('tenant-update', 'tenant', 'update', 'Update tenant settings'),
('tenant-manage', 'tenant', 'manage', 'Full tenant management including settings and configuration')
ON CONFLICT (resource, action) DO NOTHING;

-- Billing permissions
INSERT INTO permissions (id, resource, action, description) VALUES
('billing-read', 'billing', 'read', 'View billing information and invoices'),
('billing-manage', 'billing', 'manage', 'Manage billing, subscriptions, and payment methods')
ON CONFLICT (resource, action) DO NOTHING;

-- ============================================================================
-- 7. SEED DATA - ROLE PERMISSIONS MAPPING
-- ============================================================================
-- SUPER_ADMIN gets all permissions (handled in code with wildcard)

-- TENANT_ADMIN permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES
('tenant-admin', 'entry-read'),
('tenant-admin', 'entry-create'),
('tenant-admin', 'entry-update'),
('tenant-admin', 'entry-delete'),
('tenant-admin', 'user-read'),
('tenant-admin', 'user-create'),
('tenant-admin', 'user-update'),
('tenant-admin', 'user-delete'),
('tenant-admin', 'user-manage'),
('tenant-admin', 'tenant-read'),
('tenant-admin', 'tenant-update'),
('tenant-admin', 'tenant-manage'),
('tenant-admin', 'billing-read'),
('tenant-admin', 'billing-manage')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TENANT_USER permissions (standard CRUD + read own user info)
INSERT INTO role_permissions (role_id, permission_id) VALUES
('tenant-user', 'entry-read'),
('tenant-user', 'entry-create'),
('tenant-user', 'entry-update'),
('tenant-user', 'entry-delete'),
('tenant-user', 'user-read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TENANT_GUEST permissions (read-only)
INSERT INTO role_permissions (role_id, permission_id) VALUES
('tenant-guest', 'entry-read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 8. AUDIT TRIGGER (Optional - for tracking role/permission changes)
-- ============================================================================
-- Create updated_at trigger for roles table
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

-- ============================================================================
-- 9. INVITATIONS TABLE (User Invitation System)
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
CREATE INDEX idx_invitations_status ON invitations(status);

COMMENT ON TABLE invitations IS 'Stores invitation tokens for inviting new users to tenants';
COMMENT ON COLUMN invitations.tenant_id IS 'Tenant ID for which the invitation is created';
COMMENT ON COLUMN invitations.email IS 'Email address of the invited user';
COMMENT ON COLUMN invitations.role_id IS 'Role to be assigned to the user upon acceptance';
COMMENT ON COLUMN invitations.token IS 'Secure random token used in the invitation link';
COMMENT ON COLUMN invitations.status IS 'Current status of the invitation';
COMMENT ON COLUMN invitations.invited_by IS 'User ID who created the invitation';
COMMENT ON COLUMN invitations.expires_at IS 'Expiration timestamp for the invitation';

-- Trigger to update updated_at column for invitations
CREATE TRIGGER update_invitations_updated_at
BEFORE UPDATE ON invitations
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Add invite permission
INSERT INTO permissions (id, resource, action, description) VALUES
('user-invite', 'user', 'invite', 'Invite new users to the tenant')
ON CONFLICT (resource, action) DO NOTHING;

-- Grant invite permission to tenant-admin role
INSERT INTO role_permissions (role_id, permission_id) VALUES
('tenant-admin', 'user-invite')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
