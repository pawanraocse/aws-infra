package com.learning.authservice.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a user in the tenant's user registry.
 * This entity tracks all users who have accessed the tenant via:
 * - Cognito login
 * - SSO/SAML login
 * - Invitation
 * - Manual creation by admin
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUser {

    @Id
    @Column(name = "user_id", length = 255)
    private String userId; // Cognito sub or IdP user ID

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INVITED, DISABLED

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String source = "COGNITO"; // COGNITO, SAML, OIDC, MANUAL, INVITATION

    @Column(name = "first_login_at")
    private Instant firstLoginAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
