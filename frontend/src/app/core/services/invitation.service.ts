import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface InvitationRequest {
    email: string;
    roleId: string;
}

export interface InvitationResponse {
    id: string;
    tenantId: string;
    email: string;
    roleId: string;
    status: 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED';
    invitedBy: string;
    expiresAt: string;
    createdAt: string;
}

@Injectable({
    providedIn: 'root'
})
export class InvitationService {
    private apiUrl = `${environment.apiUrl}/auth/api/v1/invitations`;

    constructor(private http: HttpClient) { }

    getInvitations(): Observable<InvitationResponse[]> {
        return this.http.get<InvitationResponse[]>(this.apiUrl);
    }

    createInvitation(request: InvitationRequest): Observable<InvitationResponse> {
        return this.http.post<InvitationResponse>(this.apiUrl, request);
    }

    revokeInvitation(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }

    resendInvitation(id: string): Observable<void> {
        return this.http.post<void>(`${this.apiUrl}/${id}/resend`, {});
    }
}
