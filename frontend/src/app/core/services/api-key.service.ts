import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface ApiKey {
    id: string;
    name: string;
    keyPrefix: string;
    createdByEmail: string;
    rateLimitPerMinute: number;
    expiresAt: string;
    lastUsedAt: string | null;
    usageCount: number;
    createdAt: string;
    status: 'ACTIVE' | 'EXPIRED' | 'REVOKED';
}

export interface CreateApiKeyResponse {
    id: string;
    name: string;
    key: string;  // Raw key - shown only once
    expiresAt: string;
    createdAt: string;
}

@Injectable({
    providedIn: 'root'
})
export class ApiKeyService {
    private http = inject(HttpClient);
    private baseUrl = `${environment.apiUrl}/platform-service/platform/api/v1/api-keys`;

    /**
     * List all API keys for the current tenant.
     */
    listApiKeys(): Observable<ApiKey[]> {
        return this.http.get<ApiKey[]>(this.baseUrl);
    }

    /**
     * Create a new API key.
     * The raw key is returned ONLY in this response.
     */
    createApiKey(name: string, expiresInDays: number): Observable<CreateApiKeyResponse> {
        return this.http.post<CreateApiKeyResponse>(this.baseUrl, {
            name,
            expiresInDays
        });
    }

    /**
     * Revoke an API key.
     */
    revokeApiKey(keyId: string): Observable<{ message: string }> {
        return this.http.delete<{ message: string }>(`${this.baseUrl}/${keyId}`);
    }
}
