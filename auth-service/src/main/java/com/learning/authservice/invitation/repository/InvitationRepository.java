package com.learning.authservice.invitation.repository;

import com.learning.authservice.invitation.domain.Invitation;
import com.learning.authservice.invitation.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    List<Invitation> findByTenantId(String tenantId);

    Optional<Invitation> findByTenantIdAndEmail(String tenantId, String email);

    List<Invitation> findByTenantIdAndStatus(String tenantId, InvitationStatus status);
}
