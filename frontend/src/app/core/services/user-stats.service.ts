import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UserStats {
    totalUsers: number;
    pendingInvitations: number;
    expiredInvitations: number;
    revokedInvitations: number;
    roleDistribution: { [roleId: string]: number };
    adminCount: number;
    regularUserCount: number;
}

@Injectable({
    providedIn: 'root'
})
export class UserStatsService {
    private apiUrl = `${environment.apiUrl}/auth/api/v1/stats/users`;

    constructor(private http: HttpClient) { }

    getUserStats(): Observable<UserStats> {
        return this.http.get<UserStats>(this.apiUrl);
    }
}
