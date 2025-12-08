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

    Optional<Invitation> findByEmail(String email);

    List<Invitation> findByStatus(InvitationStatus status);

    long countByStatus(InvitationStatus status);

    List<Invitation> findAll();
}
