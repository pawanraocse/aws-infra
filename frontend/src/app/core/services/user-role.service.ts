import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UserRole {
    userId: string;
    tenantId: string;
    roleId: string;
    assignedBy: string;
    assignedAt: string;
}

export interface RoleAssignmentRequest {
    userId: string;
    roleId: string;
}

@Injectable({
    providedIn: 'root'
})
export class UserRoleService {
    private http = inject(HttpClient);
    private apiUrl = `${environment.apiUrl}/auth/api/v1/roles`;

    getUserRoles(userId: string): Observable<UserRole[]> {
        return this.http.get<UserRole[]>(`${this.apiUrl}/user/${userId}`);
    }

    updateUserRole(userId: string, roleId: string): Observable<void> {
        const request: RoleAssignmentRequest = { userId, roleId };
        return this.http.put<void>(`${this.apiUrl}/users/${userId}`, request);
    }

    assignRole(userId: string, roleId: string): Observable<void> {
        const request: RoleAssignmentRequest = { userId, roleId };
        return this.http.post<void>(`${this.apiUrl}/assign`, request);
    }
}
