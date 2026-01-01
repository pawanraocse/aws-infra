package com.learning.authservice.signup.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object that carries state through the signup pipeline.
 * 
 * Contains:
 * - User input data (email, password, tenantType, etc.)
 * - Generated/resolved data (tenantId, cognitoUserId)
 * - Tracking of completed actions for rollback support
 * - Metadata for custom action-specific data
 */
public class SignupContext {

    // ========== Input Data ==========
    private String email;
    private String password;
    private String name;
    private SignupType signupType;

    // Organization-specific
    private String companyName;
    private String tier;

    // SSO-specific
    private String cognitoUserId;
    private List<String> ssoGroups;

    // ========== Generated/Resolved Data ==========
    private String tenantId;
    private String assignedRole;

    // ========== State Tracking ==========
    private final List<String> completedActionNames = new ArrayList<>();
    private final Map<String, Object> metadata = new HashMap<>();
    private boolean emailSent = false;

    /**
     * Signup types supported by the pipeline.
     */
    public enum SignupType {
        PERSONAL, // B2C personal signup
        ORGANIZATION, // B2B organization signup
        SSO_GOOGLE, // Google social login (B2C)
        SSO_SAML, // SAML SSO (B2B)
        SSO_OIDC // OIDC SSO (B2B)
    }

    // ========== Builder Pattern ==========
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SignupContext ctx = new SignupContext();

        public Builder email(String email) {
            ctx.email = email;
            return this;
        }

        public Builder password(String password) {
            ctx.password = password;
            return this;
        }

        public Builder name(String name) {
            ctx.name = name;
            return this;
        }

        public Builder signupType(SignupType type) {
            ctx.signupType = type;
            return this;
        }

        public Builder companyName(String companyName) {
            ctx.companyName = companyName;
            return this;
        }

        public Builder tier(String tier) {
            ctx.tier = tier;
            return this;
        }

        public Builder tenantId(String tenantId) {
            ctx.tenantId = tenantId;
            return this;
        }

        public Builder cognitoUserId(String cognitoUserId) {
            ctx.cognitoUserId = cognitoUserId;
            return this;
        }

        public Builder ssoGroups(List<String> groups) {
            ctx.ssoGroups = groups;
            return this;
        }

        public SignupContext build() {
            return ctx;
        }
    }

    // ========== State Management ==========

    public void markActionCompleted(String actionName) {
        completedActionNames.add(actionName);
    }

    public boolean isActionCompleted(String actionName) {
        return completedActionNames.contains(actionName);
    }

    public List<String> getCompletedActionNames() {
        return new ArrayList<>(completedActionNames);
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        return (T) metadata.get(key);
    }

    public boolean isSsoSignup() {
        return signupType == SignupType.SSO_GOOGLE ||
                signupType == SignupType.SSO_SAML ||
                signupType == SignupType.SSO_OIDC;
    }

    public boolean isOrganizationSignup() {
        return signupType == SignupType.ORGANIZATION;
    }

    // ========== Getters and Setters ==========

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SignupType getSignupType() {
        return signupType;
    }

    public void setSignupType(SignupType signupType) {
        this.signupType = signupType;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCognitoUserId() {
        return cognitoUserId;
    }

    public void setCognitoUserId(String cognitoUserId) {
        this.cognitoUserId = cognitoUserId;
    }

    public List<String> getSsoGroups() {
        return ssoGroups;
    }

    public void setSsoGroups(List<String> ssoGroups) {
        this.ssoGroups = ssoGroups;
    }

    public String getAssignedRole() {
        return assignedRole;
    }

    public void setAssignedRole(String assignedRole) {
        this.assignedRole = assignedRole;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }
}
