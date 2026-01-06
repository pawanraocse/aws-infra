import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

export interface SubscriptionStatus {
    status: string;
    tier: string | null;
    currentPeriodEnd: string | null;
    trialEndsAt: string | null;
    hasActiveSubscription: boolean;
}

export interface CheckoutResponse {
    checkoutUrl: string;
    sessionId: string;
}

export interface PortalResponse {
    portalUrl: string;
}

export interface PricingTier {
    id: string;
    name: string;
    price: number;
    features: string[];
    maxUsers: number;
    popular?: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class BillingService {
    private http = inject(HttpClient);
    private baseUrl = `${environment.apiUrl}/platform-service/api/v1/billing`;

    // Available pricing tiers (can be fetched from backend in future)
    readonly tiers: PricingTier[] = [
        {
            id: 'starter',
            name: 'Starter',
            price: 9,
            maxUsers: 5,
            features: [
                'Up to 5 team members',
                'Basic analytics',
                'Email support',
                '10GB storage'
            ]
        },
        {
            id: 'pro',
            name: 'Pro',
            price: 29,
            maxUsers: 50,
            popular: true,
            features: [
                'Up to 50 team members',
                'Advanced analytics',
                'Priority support',
                '100GB storage',
                'API access',
                'Custom integrations'
            ]
        },
        {
            id: 'enterprise',
            name: 'Enterprise',
            price: 99,
            maxUsers: -1,
            features: [
                'Unlimited team members',
                'Enterprise analytics',
                'Dedicated support',
                'Unlimited storage',
                'Full API access',
                'SSO/SAML',
                'Custom contracts'
            ]
        }
    ];

    getSubscriptionStatus(): Observable<SubscriptionStatus> {
        return this.http.get<SubscriptionStatus>(`${this.baseUrl}/status`);
    }

    createCheckoutSession(tier: string): Observable<CheckoutResponse> {
        return this.http.post<CheckoutResponse>(`${this.baseUrl}/checkout`, { tier });
    }

    createPortalSession(): Observable<PortalResponse> {
        return this.http.post<PortalResponse>(`${this.baseUrl}/portal`, {});
    }

    getTrialDaysRemaining(trialEndsAt: string | null): number {
        if (!trialEndsAt) return 0;
        const end = new Date(trialEndsAt);
        const now = new Date();
        const diff = end.getTime() - now.getTime();
        return Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)));
    }
}
