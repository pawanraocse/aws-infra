package com.learning.authservice.user.service;

import com.learning.authservice.user.domain.TenantUser;
import com.learning.authservice.user.dto.TenantUserDto;
import com.learning.authservice.user.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing tenant users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserService {

    private final TenantUserRepository userRepository;

    /**
     * Get all users in the tenant.
     */
    @Transactional(readOnly = true)
    public List<TenantUserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get all active users.
     */
    @Transactional(readOnly = true)
    public List<TenantUserDto> getActiveUsers() {
        return userRepository.findAllActiveUsers().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Search users by name or email.
     */
    @Transactional(readOnly = true)
    public List<TenantUserDto> searchUsers(@NonNull String query) {
        return userRepository.searchUsers(query).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get a user by ID.
     */
    @Transactional(readOnly = true)
    public Optional<TenantUserDto> getUserById(@NonNull String userId) {
        return userRepository.findById(userId).map(this::toDto);
    }

    /**
     * Register or update user on login.
     * Called when user logs in via Cognito or SSO.
     */
    @Transactional
    public TenantUserDto upsertOnLogin(@NonNull String userId, @NonNull String email,
            String name, String source) {
        log.info("Upserting user on login: userId={}, email={}", userId, email);

        Optional<TenantUser> existing = userRepository.findById(userId);
        TenantUser user;

        if (existing.isPresent()) {
            // Update existing user
            user = existing.get();
            user.setLastLoginAt(Instant.now());
            user.setStatus("ACTIVE");
            if (name != null) {
                user.setName(name);
            }
        } else {
            // Create new user
            user = TenantUser.builder()
                    .userId(userId)
                    .email(email)
                    .name(name)
                    .source(source != null ? source : "COGNITO")
                    .status("ACTIVE")
                    .firstLoginAt(Instant.now())
                    .lastLoginAt(Instant.now())
                    .build();
        }

        TenantUser saved = userRepository.save(user);
        return toDto(saved);
    }

    /**
     * Create user from invitation (status=INVITED until they login).
     */
    @Transactional
    public TenantUserDto createFromInvitation(@NonNull String email, String name) {
        log.info("Creating user from invitation: email={}", email);

        // Check if user already exists
        Optional<TenantUser> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        TenantUser user = TenantUser.builder()
                .userId("pending-" + email.hashCode()) // Temporary ID until login
                .email(email)
                .name(name)
                .source("INVITATION")
                .status("INVITED")
                .build();

        TenantUser saved = userRepository.save(user);
        return toDto(saved);
    }

    /**
     * Disable a user.
     */
    @Transactional
    public void disableUser(@NonNull String userId) {
        log.info("Disabling user: {}", userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus("DISABLED");
            userRepository.save(user);
        });
    }

    /**
     * Get user count by status.
     */
    @Transactional(readOnly = true)
    public long countByStatus(@NonNull String status) {
        return userRepository.countByStatus(status);
    }

    private TenantUserDto toDto(TenantUser user) {
        return TenantUserDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .source(user.getSource())
                .firstLoginAt(user.getFirstLoginAt())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
