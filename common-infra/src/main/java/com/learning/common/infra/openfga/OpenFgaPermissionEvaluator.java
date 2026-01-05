package com.learning.common.infra.openfga;

import com.learning.common.infra.security.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenFGA-based permission evaluator for resource-level access control.
 * Only active when openfga.enabled=true.
 * 
 * This evaluator supplements the existing RBAC system:
 * - RBAC (@RequirePermission) handles org-level permissions
 * - OpenFGA handles resource-level permissions (per-file, per-folder)
 * 
 * The authorization flow is:
 * 1. Check RBAC (org role allows action?) → If yes, ALLOW
 * 2. Check OpenFGA (user has relation to resource?) → If yes, ALLOW
 * 3. Otherwise, DENY
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaPermissionEvaluator implements PermissionEvaluator {

    private final OpenFgaClientWrapper fgaClient;

    @Override
    public boolean hasPermission(String userId, String resource, String action) {
        // This method is called by AuthorizationAspect for @RequirePermission checks.
        // For resource-level checks, use hasResourcePermission() instead.
        //
        // Since this evaluator is for resource-level permissions, we return false here
        // to let the RBAC evaluator handle org-level permissions first.
        log.debug("OpenFGA evaluator called for org-level check: user={}, resource={}, action={}",
                userId, resource, action);
        return false; // Falls back to RBAC evaluator
    }

    /**
     * Check if user has permission on a SPECIFIC resource instance.
     * This is the main method for OpenFGA resource-level checks.
     * 
     * @param userId       User identifier
     * @param resourceType Type of resource (e.g., "folder", "document")
     * @param resourceId   Specific resource ID
     * @param action       Action to check (e.g., "view", "edit", "delete")
     * @return true if allowed by OpenFGA, false otherwise
     */
    public boolean hasResourcePermission(String userId, String resourceType, String resourceId, String action) {
        String relation = mapActionToRelation(action);
        return fgaClient.check(userId, relation, resourceType, resourceId);
    }

    /**
     * Map action name to OpenFGA relation.
     * 
     * Actions use verb format (view, edit, delete)
     * Relations use noun format (viewer, editor, owner)
     */
    private String mapActionToRelation(String action) {
        return switch (action.toLowerCase()) {
            case "view", "read" -> "can_view";
            case "edit", "update", "write" -> "can_edit";
            case "delete", "remove" -> "can_delete";
            case "share", "manage_access" -> "can_share";
            default -> "can_" + action.toLowerCase();
        };
    }
}
