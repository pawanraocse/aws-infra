package com.learning.authservice.invitation.repository;

import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Invitation entity.
 * Tenant isolation is handled via TenantDataSourceRouter.
 */
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    /**
     * Find invitation by email - returns multiple if duplicates exist.
     * 
     * @deprecated Use findByEmailAndStatus for checking pending invitations.
     */
    @Deprecated
    Optional<Invitation> findByEmail(String email);

    /**
     * Find pending invitation by email - preferred method.
     * Only returns the active pending invitation, avoiding duplicates.
     */
    Optional<Invitation> findByEmailAndStatus(String email, InvitationStatus status);

    List<Invitation> findByStatus(InvitationStatus status);

    long countByStatus(InvitationStatus status);

    List<Invitation> findAll();
}
