package com.learning.authservice.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for TenantUser responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUserDto {
    private String userId;
    private String email;
    private String name;
    private String avatarUrl;
    private String status;
    private String source;
    private Instant firstLoginAt;
    private Instant lastLoginAt;
    private Instant createdAt;
}
