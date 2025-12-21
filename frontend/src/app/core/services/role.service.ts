import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Role {
    id: string;
    name: string;
    description: string;
    scope: 'PLATFORM' | 'TENANT';
    accessLevel?: string; // admin, editor, viewer - for custom roles
    createdAt: string;
}

export interface Permission {
    id: string;
    resource: string;
    action: string;
    description: string;
    fullPermission?: string; // Optional for simplified permissions
}

export interface RoleAssignmentRequest {
    userId: string;
    roleId: string;
}

@Injectable({
    providedIn: 'root'
})
export class RoleService {
    private rolesUrl = `${environment.apiUrl}/auth/api/v1/roles`;
    private permissionsUrl = `${environment.apiUrl}/auth/api/v1/permissions`;

    constructor(private http: HttpClient) { }

    getRoles(): Observable<Role[]> {
        return this.http.get<Role[]>(this.rolesUrl);
    }

    getPermissions(): Observable<Permission[]> {
        return this.http.get<Permission[]>(this.permissionsUrl);
    }

    assignRole(userId: string, roleId: string): Observable<void> {
        return this.http.post<void>(`${this.rolesUrl}/assign`, { userId, roleId });
    }

    revokeRole(userId: string, roleId: string): Observable<void> {
        return this.http.post<void>(`${this.rolesUrl}/revoke`, { userId, roleId });
    }

    getUserRoles(userId: string): Observable<any[]> {
        return this.http.get<any[]>(`${this.rolesUrl}/user/${userId}`);
    }

    createRole(role: { name: string; description: string; scope: string }): Observable<Role> {
        return this.http.post<Role>(this.rolesUrl, role);
    }
}
