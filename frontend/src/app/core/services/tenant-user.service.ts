import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TenantUser {
    userId: string;
    email: string;
    name: string;
    avatarUrl: string | null;
    status: string;
    source: string;
    firstLoginAt: string | null;
    lastLoginAt: string | null;
    createdAt: string;
}

export interface UserStats {
    total: number;
    active: number;
    invited: number;
    disabled: number;
}

/**
 * Service for tenant user management.
 */
@Injectable({
    providedIn: 'root'
})
export class TenantUserService {
    private http = inject(HttpClient);
    private apiUrl = `${environment.apiUrl}/auth/api/v1/users`;

    /**
     * Get all users in the tenant.
     */
    getAllUsers(status?: string): Observable<TenantUser[]> {
        const params: Record<string, string> = status ? { status } : {};
        return this.http.get<TenantUser[]>(this.apiUrl, { params });
    }

    /**
     * Search users by name or email.
     */
    searchUsers(query: string): Observable<TenantUser[]> {
        return this.http.get<TenantUser[]>(`${this.apiUrl}/search`, {
            params: { q: query }
        });
    }

    /**
     * Get user by ID.
     */
    getUserById(userId: string): Observable<TenantUser> {
        return this.http.get<TenantUser>(`${this.apiUrl}/${userId}`);
    }

    /**
     * Get user statistics.
     */
    getUserStats(): Observable<UserStats> {
        return this.http.get<UserStats>(`${this.apiUrl}/stats`);
    }

    /**
     * Disable a user.
     */
    disableUser(userId: string): Observable<void> {
        return this.http.post<void>(`${this.apiUrl}/${userId}/disable`, {});
    }
}
