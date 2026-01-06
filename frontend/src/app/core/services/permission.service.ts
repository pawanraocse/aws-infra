import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface AccessGrant {
    userId: string;
    relation: string;
}

export interface ResourceAccessResponse {
    resourceType: string;
    resourceId: string;
    grants: AccessGrant[];
}

export interface SharePermissionRequest {
    targetUserId: string;
    resourceType: string;
    resourceId: string;
    relation: string;
}

export interface RevokePermissionRequest {
    targetUserId: string;
    resourceType: string;
    resourceId: string;
    relation: string;
}

/**
 * Service for managing fine-grained permissions via OpenFGA.
 * Only active when OpenFGA is enabled on the backend.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
    private http = inject(HttpClient);
    private baseUrl = `${environment.apiUrl}/auth/api/v1/resource-permissions`;

    /**
     * Get all access grants for a specific resource.
     */
    listAccess(resourceType: string, resourceId: string): Observable<ResourceAccessResponse> {
        return this.http.get<ResourceAccessResponse>(`${this.baseUrl}/${resourceType}/${resourceId}`);
    }

    /**
     * Grant access to a user for a specific resource.
     */
    shareAccess(request: SharePermissionRequest): Observable<{ status: string; message: string }> {
        return this.http.post<{ status: string; message: string }>(`${this.baseUrl}/share`, request);
    }

    /**
     * Revoke access from a user for a specific resource.
     */
    revokeAccess(request: RevokePermissionRequest): Observable<{ status: string; message: string }> {
        return this.http.request<{ status: string; message: string }>('DELETE', `${this.baseUrl}/revoke`, {
            body: request
        });
    }
}
