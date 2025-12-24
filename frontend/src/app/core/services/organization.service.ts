import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface OrganizationProfile {
    tenantId: string;
    name: string;
    companyName?: string;
    industry?: string;
    companySize?: string;
    website?: string;
    logoUrl?: string;
    slaTier: string;
    tenantType: string;
    maxUsers: number;
    ownerEmail?: string;
    subscriptionStatus?: string;
    trialEndsAt?: string;
}

export interface UpdateOrganizationRequest {
    companyName?: string;
    industry?: string;
    companySize?: string;
    website?: string;
    logoUrl?: string;
}

@Injectable({
    providedIn: 'root'
})
export class OrganizationService {
    private apiUrl = `${environment.apiUrl}/platform/api/v1/organizations`;

    constructor(private http: HttpClient) { }

    getOrganization(): Observable<OrganizationProfile> {
        return this.http.get<OrganizationProfile>(this.apiUrl);
    }

    updateOrganization(request: UpdateOrganizationRequest): Observable<OrganizationProfile> {
        return this.http.put<OrganizationProfile>(this.apiUrl, request);
    }
}
