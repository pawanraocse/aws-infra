package com.learning.platformservice.membership.entity;

/**
 * Role hint for display purposes in the tenant selector.
 * Actual permissions are managed in the tenant database.
 */
public enum MembershipRoleHint {
    /**
     * Owner of the tenant (created it or is the primary admin).
     */
    OWNER("owner"),

    /**
     * Administrator with full control within the tenant.
     */
    ADMIN("admin"),

    /**
     * Regular member with standard access.
     */
    MEMBER("member"),

    /**
     * Guest with limited/read-only access.
     */
    GUEST("guest");

    private final String value;

    MembershipRoleHint(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MembershipRoleHint fromValue(String value) {
        if (value == null) {
            return MEMBER;
        }
        for (MembershipRoleHint hint : values()) {
            if (hint.value.equalsIgnoreCase(value)) {
                return hint;
            }
        }
        return MEMBER;
    }
}
