package com.learning.authservice.stats.service;

import com.learning.authservice.authorization.repository.UserRoleRepository;
import com.learning.authservice.invitation.domain.InvitationStatus;
import com.learning.authservice.invitation.repository.InvitationRepository;
import com.learning.authservice.stats.dto.UserStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for calculating user statistics.
 * Tenant isolation is handled via TenantDataSourceRouter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatsService {

        private final InvitationRepository invitationRepository;
        private final UserRoleRepository userRoleRepository;

        /**
         * Get comprehensive user statistics.
         *
         * @return User statistics DTO
         */
        @Transactional(readOnly = true)
        public UserStatsDTO getUserStats() {
                log.info("Calculating user statistics");

                // Count invitations by status
                long pendingInvitations = invitationRepository.countByStatus(InvitationStatus.PENDING);
                long acceptedInvitations = invitationRepository.countByStatus(InvitationStatus.ACCEPTED);
                long expiredInvitations = invitationRepository.countByStatus(InvitationStatus.EXPIRED);
                long revokedInvitations = invitationRepository.countByStatus(InvitationStatus.REVOKED);

                // Total users = accepted invitations (users who joined)
                long totalUsers = acceptedInvitations;

                // Calculate role distribution
                List<Object[]> roleCountsRaw = userRoleRepository.countUsersByRole();
                Map<String, Long> roleDistribution = roleCountsRaw.stream()
                                .collect(Collectors.toMap(
                                                row -> (String) row[0], // roleId
                                                row -> ((Number) row[1]).longValue() // count
                                ));

                // Extract specific role counts
                long adminCount = roleDistribution.getOrDefault("admin", 0L);
                long regularUserCount = roleDistribution.getOrDefault("user", 0L);

                UserStatsDTO stats = UserStatsDTO.builder()
                                .totalUsers(totalUsers)
                                .pendingInvitations(pendingInvitations)
                                .expiredInvitations(expiredInvitations)
                                .revokedInvitations(revokedInvitations)
                                .roleDistribution(roleDistribution)
                                .adminCount(adminCount)
                                .regularUserCount(regularUserCount)
                                .build();

                log.info("User stats: {} total users, {} pending invitations", totalUsers, pendingInvitations);

                return stats;
        }
}
