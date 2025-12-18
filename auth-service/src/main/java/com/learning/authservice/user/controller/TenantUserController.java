package com.learning.authservice.user.controller;

import com.learning.authservice.user.dto.TenantUserDto;
import com.learning.authservice.user.service.TenantUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for tenant user management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class TenantUserController {

    private final TenantUserService userService;

    /**
     * Get all users in the tenant.
     */
    @GetMapping
    public ResponseEntity<List<TenantUserDto>> getAllUsers(
            @RequestParam(required = false) String status) {

        List<TenantUserDto> users;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            users = userService.getActiveUsers();
        } else {
            users = userService.getAllUsers();
        }
        return ResponseEntity.ok(users);
    }

    /**
     * Search users by name or email.
     */
    @GetMapping("/search")
    public ResponseEntity<List<TenantUserDto>> searchUsers(
            @RequestParam String q) {

        List<TenantUserDto> users = userService.searchUsers(q);
        return ResponseEntity.ok(users);
    }

    /**
     * Get a specific user by ID.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<TenantUserDto> getUserById(@PathVariable String userId) {
        return userService.getUserById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get user statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getUserStats() {
        Map<String, Long> stats = Map.of(
                "total", userService.countByStatus("ACTIVE") +
                        userService.countByStatus("INVITED") +
                        userService.countByStatus("DISABLED"),
                "active", userService.countByStatus("ACTIVE"),
                "invited", userService.countByStatus("INVITED"),
                "disabled", userService.countByStatus("DISABLED"));
        return ResponseEntity.ok(stats);
    }

    /**
     * Disable a user.
     */
    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disableUser(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String currentUserId) {

        log.info("User {} disabling user {}", currentUserId, userId);
        userService.disableUser(userId);
        return ResponseEntity.noContent().build();
    }
}
