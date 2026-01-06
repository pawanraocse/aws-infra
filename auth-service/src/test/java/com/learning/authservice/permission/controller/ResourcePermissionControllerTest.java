package com.learning.authservice.permission.controller;

import com.learning.authservice.permission.dto.ResourceAccessResponse;
import com.learning.authservice.permission.dto.RevokePermissionRequest;
import com.learning.authservice.permission.dto.SharePermissionRequest;
import com.learning.authservice.user.repository.TenantUserRepository;
import com.learning.common.infra.openfga.OpenFgaReader;
import com.learning.common.infra.openfga.OpenFgaTupleService;
import com.learning.common.infra.ratelimit.ApiRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration-style unit tests for ResourcePermissionController.
 * Tests the complete flow including security validations.
 */
@ExtendWith(MockitoExtension.class)
class ResourcePermissionControllerTest {

    @Mock
    private OpenFgaTupleService tupleService;

    @Mock
    private TenantUserRepository userRepository;

    @Mock
    private ApiRateLimiter rateLimiter;

    @Mock
    private OpenFgaReader fgaReader;

    private ResourcePermissionController controller;

    private static final String USER_ID = "user-123";
    private static final String TENANT_ID = "tenant-456";
    private static final String TARGET_USER_ID = "user-789";
    private static final String RESOURCE_TYPE = "folder";
    private static final String RESOURCE_ID = "folder-001";

    @BeforeEach
    void setUp() {
        controller = new ResourcePermissionController(tupleService, userRepository, rateLimiter, fgaReader);
    }

    // ========================================================================
    // Share Access Tests
    // ========================================================================

    @Test
    @DisplayName("Share access should succeed when user exists and not rate limited")
    void shareAccess_Success() {
        // Given
        SharePermissionRequest request = new SharePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(true);

        // When
        ResponseEntity<Map<String, String>> response = controller.shareAccess(USER_ID, TENANT_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "success");
        verify(tupleService).grantAccess(TARGET_USER_ID, "editor", RESOURCE_TYPE, RESOURCE_ID);
    }

    @Test
    @DisplayName("Share access should fail when rate limited")
    void shareAccess_RateLimited() {
        // Given
        SharePermissionRequest request = new SharePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> controller.shareAccess(USER_ID, TENANT_ID, request))
                .hasMessageContaining("Rate limit exceeded");

        verify(tupleService, never()).grantAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Share access should fail when target user does not exist")
    void shareAccess_UserNotFound() {
        // Given
        SharePermissionRequest request = new SharePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> controller.shareAccess(USER_ID, TENANT_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Target user does not exist");

        verify(tupleService, never()).grantAccess(any(), any(), any(), any());
    }

    // ========================================================================
    // Revoke Access Tests
    // ========================================================================

    @Test
    @DisplayName("Revoke access should succeed when user is owner")
    void revokeAccess_AsOwner_Success() {
        // Given
        RevokePermissionRequest request = new RevokePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(fgaReader.check(USER_ID, "owner", RESOURCE_TYPE, RESOURCE_ID)).thenReturn(true);

        // When
        ResponseEntity<Map<String, String>> response = controller.revokeAccess(USER_ID, TENANT_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "success");
        verify(tupleService).revokeAccess(TARGET_USER_ID, "editor", RESOURCE_TYPE, RESOURCE_ID);
    }

    @Test
    @DisplayName("Revoke access should succeed when user has can_share permission")
    void revokeAccess_WithSharePermission_Success() {
        // Given
        RevokePermissionRequest request = new RevokePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(fgaReader.check(USER_ID, "owner", RESOURCE_TYPE, RESOURCE_ID)).thenReturn(false);
        when(fgaReader.check(USER_ID, "can_share", RESOURCE_TYPE, RESOURCE_ID)).thenReturn(true);

        // When
        ResponseEntity<Map<String, String>> response = controller.revokeAccess(USER_ID, TENANT_ID, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tupleService).revokeAccess(TARGET_USER_ID, "editor", RESOURCE_TYPE, RESOURCE_ID);
    }

    @Test
    @DisplayName("Revoke access should fail when user is not owner")
    void revokeAccess_NotOwner_Forbidden() {
        // Given
        RevokePermissionRequest request = new RevokePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(fgaReader.check(USER_ID, "owner", RESOURCE_TYPE, RESOURCE_ID)).thenReturn(false);
        when(fgaReader.check(USER_ID, "can_share", RESOURCE_TYPE, RESOURCE_ID)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> controller.revokeAccess(USER_ID, TENANT_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be the owner");

        verify(tupleService, never()).revokeAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Revoke access should fail when rate limited")
    void revokeAccess_RateLimited() {
        // Given
        RevokePermissionRequest request = new RevokePermissionRequest(
                TARGET_USER_ID, RESOURCE_TYPE, RESOURCE_ID, "editor");

        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> controller.revokeAccess(USER_ID, TENANT_ID, request))
                .hasMessageContaining("Rate limit exceeded");

        verify(tupleService, never()).revokeAccess(any(), any(), any(), any());
    }

    // ========================================================================
    // List Access Tests
    // ========================================================================

    @Test
    @DisplayName("List access should return grants for resource")
    void listAccess_Success() {
        // Given
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(tupleService.listTuples(RESOURCE_TYPE, RESOURCE_ID))
                .thenReturn(List.of(
                        new OpenFgaTupleService.TupleInfo("user-1", "owner"),
                        new OpenFgaTupleService.TupleInfo("user-2", "editor")));

        // When
        ResponseEntity<ResourceAccessResponse> response = controller.listAccess(
                USER_ID, TENANT_ID, RESOURCE_TYPE, RESOURCE_ID);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().grants()).hasSize(2);
    }

    @Test
    @DisplayName("List access should fail when rate limited")
    void listAccess_RateLimited() {
        // Given
        when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> controller.listAccess(USER_ID, TENANT_ID, RESOURCE_TYPE, RESOURCE_ID))
                .hasMessageContaining("Rate limit exceeded");
    }
}
