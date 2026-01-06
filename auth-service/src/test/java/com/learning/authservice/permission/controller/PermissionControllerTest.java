package com.learning.authservice.permission.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for PermissionController.
 * Tests the permission management API endpoints.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionControllerTest {

        private MockMvc mockMvc;

        @Mock
        private OpenFgaTupleService tupleService;

        @Mock
        private TenantUserRepository userRepository;

        @Mock
        private ApiRateLimiter rateLimiter;

        @Mock
        private OpenFgaReader fgaReader;

        private ObjectMapper objectMapper;

        private static final String USER_ID = "user-123";
        private static final String TENANT_ID = "tenant-1";

        @BeforeEach
        void setUp() {
                // Configure mocks to allow all operations by default
                when(rateLimiter.tryAcquire(anyString(), anyString())).thenReturn(true);
                when(userRepository.existsById(anyString())).thenReturn(true);
                when(fgaReader.check(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

                ResourcePermissionController controller = new ResourcePermissionController(
                                tupleService, userRepository, rateLimiter, fgaReader);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
                objectMapper = new ObjectMapper();
        }

        @Test
        @DisplayName("POST /share - should grant access and return success")
        void shareAccess_shouldGrantAccess() throws Exception {
                SharePermissionRequest request = new SharePermissionRequest(
                                "target-user", "folder", "folder-123", "editor");

                mockMvc.perform(post("/api/v1/resource-permissions/share")
                                .header("X-User-Id", USER_ID)
                                .header("X-Tenant-Id", TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("success"))
                                .andExpect(jsonPath("$.message").exists());

                verify(tupleService).grantAccess("target-user", "editor", "folder", "folder-123");
        }

        @Test
        @DisplayName("POST /share - should return 400 for invalid request")
        void shareAccess_invalidRequest_shouldReturn400() throws Exception {
                SharePermissionRequest request = new SharePermissionRequest(
                                "", "folder", "folder-123", "editor" // Empty targetUserId
                );

                mockMvc.perform(post("/api/v1/resource-permissions/share")
                                .header("X-User-Id", USER_ID)
                                .header("X-Tenant-Id", TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(tupleService);
        }

        @Test
        @DisplayName("DELETE /revoke - should revoke access and return success")
        void revokeAccess_shouldRevokeAccess() throws Exception {
                RevokePermissionRequest request = new RevokePermissionRequest(
                                "target-user", "folder", "folder-123", "editor");

                mockMvc.perform(delete("/api/v1/resource-permissions/revoke")
                                .header("X-User-Id", USER_ID)
                                .header("X-Tenant-Id", TENANT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("success"))
                                .andExpect(jsonPath("$.message").exists());

                verify(tupleService).revokeAccess("target-user", "editor", "folder", "folder-123");
        }

        @Test
        @DisplayName("GET /{resourceType}/{resourceId} - should return access grants")
        void listAccess_shouldReturnGrants() throws Exception {
                when(tupleService.listTuples("folder", "folder-123"))
                                .thenReturn(List.of(
                                                new OpenFgaTupleService.TupleInfo("user-1", "owner"),
                                                new OpenFgaTupleService.TupleInfo("user-2", "editor")));

                mockMvc.perform(get("/api/v1/resource-permissions/folder/folder-123")
                                .header("X-User-Id", USER_ID)
                                .header("X-Tenant-Id", TENANT_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.resourceType").value("folder"))
                                .andExpect(jsonPath("$.resourceId").value("folder-123"))
                                .andExpect(jsonPath("$.grants").isArray())
                                .andExpect(jsonPath("$.grants.length()").value(2))
                                .andExpect(jsonPath("$.grants[0].userId").value("user-1"))
                                .andExpect(jsonPath("$.grants[0].relation").value("owner"));
        }

        @Test
        @DisplayName("GET /{resourceType}/{resourceId} - should return empty list when no grants")
        void listAccess_noGrants_shouldReturnEmptyList() throws Exception {
                when(tupleService.listTuples("document", "doc-456"))
                                .thenReturn(List.of());

                mockMvc.perform(get("/api/v1/resource-permissions/document/doc-456")
                                .header("X-User-Id", USER_ID)
                                .header("X-Tenant-Id", TENANT_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.resourceType").value("document"))
                                .andExpect(jsonPath("$.resourceId").value("doc-456"))
                                .andExpect(jsonPath("$.grants").isArray())
                                .andExpect(jsonPath("$.grants.length()").value(0));
        }
}
