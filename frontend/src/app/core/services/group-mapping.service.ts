import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {BaseApiService} from '../../core/base-api.service';

/**
 * Group-Role Mapping DTO
 */
export interface GroupRoleMapping {
    id: string;
    externalGroupId: string;
    groupName: string;
    roleId: string;
    roleName: string;
    priority: number;
    autoAssign: boolean;
    createdBy: string;
}

/**
 * Request to create a new group-role mapping
 */
export interface CreateMappingRequest {
    externalGroupId: string;
    groupName: string;
    roleId: string;
    priority?: number;
}

/**
 * Request to update an existing mapping
 */
export interface UpdateMappingRequest {
    roleId: string;
    priority?: number;
}

/**
 * IdP Group (from platform service)
 */
export interface IdpGroup {
    id: string;
    externalGroupId: string;
    groupName: string;
    idpType: string;
    memberCount?: number;
    lastSyncedAt?: string;
}

/**
 * Service for managing group-to-role mappings.
 *
 * Allows tenant admins to configure how IdP groups (from Okta, Azure AD, etc.)
 * map to application roles for automatic role assignment on SSO login.
 */
@Injectable({
    providedIn: 'root'
})
export class GroupMappingService extends BaseApiService {

    constructor() {
        super('/auth-service/api/v1/groups'); // Routes through gateway to auth-service
    }

    /**
     * Get all group-role mappings for the current tenant
     */
    getMappings(): Observable<GroupRoleMapping[]> {
        return this.http.get<GroupRoleMapping[]>(`${this.resourceUrl}/mappings`);
    }

    /**
     * Get a specific mapping by ID
     */
    getMapping(id: string): Observable<GroupRoleMapping> {
        return this.http.get<GroupRoleMapping>(`${this.resourceUrl}/mappings/${id}`);
    }

    /**
     * Create a new group-role mapping
     */
    createMapping(request: CreateMappingRequest): Observable<GroupRoleMapping> {
        return this.http.post<GroupRoleMapping>(`${this.resourceUrl}/mappings`, request);
    }

    /**
     * Update an existing mapping
     */
    updateMapping(id: string, request: UpdateMappingRequest): Observable<GroupRoleMapping> {
        return this.http.put<GroupRoleMapping>(`${this.resourceUrl}/mappings/${id}`, request);
    }

    /**
     * Delete a mapping
     */
    deleteMapping(id: string): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/mappings/${id}`);
    }

    /**
     * Get synced IdP groups from platform service
     * (for displaying available groups to map)
     */
    getIdpGroups(): Observable<IdpGroup[]> {
        // This endpoint is on the platform service, not auth service
        const platformUrl = this.baseUrl.replace(':8080', ':8082');
        return this.http.get<IdpGroup[]>(`${platformUrl}/api/v1/idp-groups`);
    }
}
