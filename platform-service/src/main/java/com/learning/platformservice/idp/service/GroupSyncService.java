package com.learning.platformservice.idp.service;

import com.learning.platformservice.idp.entity.IdpGroup;
import com.learning.platformservice.tenant.entity.IdpType;

import java.util.List;

/**
 * Service for syncing groups from external Identity Providers.
 */
public interface GroupSyncService {

    /**
     * Sync groups from SSO login claims.
     * Creates new groups if they don't exist, updates last_synced_at if they do.
     *
     * @param tenantId    Tenant ID
     * @param groupClaims List of group identifiers from IdP (SAML assertion or OIDC
     *                    token)
     * @param idpType     Type of Identity Provider
     * @return List of synced IdpGroup entities
     */
    List<IdpGroup> syncGroupsFromClaims(String tenantId, List<String> groupClaims, IdpType idpType);

    /**
     * Get all groups for a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of IdpGroup entities
     */
    List<IdpGroup> getGroupsForTenant(String tenantId);

    /**
     * Get a specific group by external ID.
     *
     * @param tenantId        Tenant ID
     * @param externalGroupId External group identifier
     * @return IdpGroup if found
     */
    IdpGroup getGroupByExternalId(String tenantId, String externalGroupId);

    /**
     * Delete all groups for a tenant (used when resetting SSO config).
     *
     * @param tenantId Tenant ID
     */
    void deleteAllGroupsForTenant(String tenantId);
}
