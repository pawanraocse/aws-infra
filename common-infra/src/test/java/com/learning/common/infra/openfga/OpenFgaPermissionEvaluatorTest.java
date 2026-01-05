package com.learning.common.infra.openfga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenFgaPermissionEvaluator.
 * Tests the permission evaluation logic without requiring a running OpenFGA
 * server.
 */
@ExtendWith(MockitoExtension.class)
class OpenFgaPermissionEvaluatorTest {

    @Mock
    private OpenFgaClientWrapper fgaClient;

    private OpenFgaPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new OpenFgaPermissionEvaluator(fgaClient);
    }

    @Test
    void hasPermission_orgLevel_shouldReturnFalse() {
        // Org-level checks should fall back to RBAC evaluator
        boolean result = evaluator.hasPermission("user-123", "entry", "read");

        assertFalse(result, "Org-level checks should return false to defer to RBAC");
        verifyNoInteractions(fgaClient);
    }

    @Test
    void hasResourcePermission_viewAction_shouldCheckCanView() {
        when(fgaClient.check("user-123", "can_view", "folder", "folder-456"))
                .thenReturn(true);

        boolean result = evaluator.hasResourcePermission("user-123", "folder", "folder-456", "view");

        assertTrue(result);
        verify(fgaClient).check("user-123", "can_view", "folder", "folder-456");
    }

    @Test
    void hasResourcePermission_editAction_shouldCheckCanEdit() {
        when(fgaClient.check("user-123", "can_edit", "document", "doc-789"))
                .thenReturn(true);

        boolean result = evaluator.hasResourcePermission("user-123", "document", "doc-789", "edit");

        assertTrue(result);
        verify(fgaClient).check("user-123", "can_edit", "document", "doc-789");
    }

    @Test
    void hasResourcePermission_deleteAction_shouldCheckCanDelete() {
        when(fgaClient.check("user-123", "can_delete", "folder", "folder-456"))
                .thenReturn(false);

        boolean result = evaluator.hasResourcePermission("user-123", "folder", "folder-456", "delete");

        assertFalse(result);
        verify(fgaClient).check("user-123", "can_delete", "folder", "folder-456");
    }

    @Test
    void hasResourcePermission_readAction_shouldMapToCanView() {
        when(fgaClient.check("user-123", "can_view", "project", "proj-123"))
                .thenReturn(true);

        boolean result = evaluator.hasResourcePermission("user-123", "project", "proj-123", "read");

        assertTrue(result);
        verify(fgaClient).check("user-123", "can_view", "project", "proj-123");
    }

    @Test
    void hasResourcePermission_updateAction_shouldMapToCanEdit() {
        when(fgaClient.check("user-123", "can_edit", "document", "doc-123"))
                .thenReturn(true);

        boolean result = evaluator.hasResourcePermission("user-123", "document", "doc-123", "update");

        assertTrue(result);
        verify(fgaClient).check("user-123", "can_edit", "document", "doc-123");
    }

    @Test
    void hasResourcePermission_shareAction_shouldCheckCanShare() {
        when(fgaClient.check("user-123", "can_share", "folder", "folder-456"))
                .thenReturn(true);

        boolean result = evaluator.hasResourcePermission("user-123", "folder", "folder-456", "share");

        assertTrue(result);
        verify(fgaClient).check("user-123", "can_share", "folder", "folder-456");
    }

    @Test
    void hasResourcePermission_customAction_shouldPrefixWithCan() {
        when(fgaClient.check("user-123", "can_export", "report", "report-123"))
                .thenReturn(true);

        boolean result = evaluator.hasResourcePermission("user-123", "report", "report-123", "export");

        assertTrue(result);
        verify(fgaClient).check("user-123", "can_export", "report", "report-123");
    }
}
