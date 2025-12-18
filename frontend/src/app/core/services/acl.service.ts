import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AclEntry {
    id: string;
    resourceId: string;
    resourceType: string;
    principalType: string;
    principalId: string;
    roleBundle: string;
    grantedBy: string;
    grantedAt: string;
    expiresAt?: string;
}

export interface GrantAccessRequest {
    resourceId: string;
    resourceType: string;
    principalType: string;
    principalId: string;
    roleBundle: string;
    expiresAt?: string;
}

export interface RoleBundle {
    name: string;
    description: string;
    capabilities: string[];
}

/**
 * Service for managing resource-level access control (ACLs).
 */
@Injectable({
    providedIn: 'root'
})
export class AclService {
    private http = inject(HttpClient);
    private baseUrl = `${environment.apiUrl}/auth/api/v1/acl`;

    /**
     * Get available role bundles with descriptions.
     */
    getRoleBundles(): Observable<RoleBundle[]> {
        return this.http.get<RoleBundle[]>(`${this.baseUrl}/role-bundles`);
    }

    /**
     * Grant access to a resource.
     */
    grantAccess(request: GrantAccessRequest): Observable<AclEntry> {
        return this.http.post<AclEntry>(this.baseUrl, request);
    }

    /**
     * Revoke access from a resource.
     */
    revokeAccess(aclEntryId: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${aclEntryId}`);
    }

    /**
     * Get all users with access to a resource.
     */
    getResourcePermissions(resourceId: string): Observable<AclEntry[]> {
        return this.http.get<AclEntry[]>(`${this.baseUrl}/resource/${resourceId}`);
    }

    /**
     * Get all resources a user has access to.
     */
    getUserPermissions(userId: string): Observable<AclEntry[]> {
        return this.http.get<AclEntry[]>(`${this.baseUrl}/user/${userId}`);
    }

    /**
     * Check if user has a specific capability on a resource.
     */
    checkPermission(userId: string, resourceId: string, capability: string): Observable<{ allowed: boolean }> {
        return this.http.get<{ allowed: boolean }>(`${this.baseUrl}/check`, {
            params: { userId, resourceId, capability }
        });
    }
}
