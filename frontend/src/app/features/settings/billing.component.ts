import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {TagModule} from 'primeng/tag';
import {ToastModule} from 'primeng/toast';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {MessageService} from 'primeng/api';
import {BillingService, PricingTier, SubscriptionStatus} from '../../core/services/billing.service';

@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [CommonModule, CardModule, ButtonModule, TagModule, ToastModule, ProgressSpinnerModule],
  providers: [MessageService],
  template: `
    <div class="p-4">
      <p-toast></p-toast>

      <h1 class="text-3xl font-bold mb-2">Billing & Subscription</h1>
      <p class="text-600 mb-4">Manage your subscription and billing information</p>

      <!-- Loading State -->
      <div *ngIf="loading()" class="flex justify-content-center p-6">
        <p-progressSpinner strokeWidth="4"></p-progressSpinner>
      </div>

      <div *ngIf="!loading()">
        <!-- Trial Banner -->
        <div *ngIf="subscriptionStatus()?.status === 'TRIAL'"
             class="trial-banner mb-4 p-4 border-round-lg flex align-items-center justify-content-between">
          <div class="flex align-items-center gap-3">
            <i class="pi pi-clock text-3xl text-orange-400"></i>
            <div>
              <div class="font-bold text-lg text-white">Free Trial</div>
              <div class="text-orange-200">
                {{ getTrialDaysRemaining() }} days remaining - Upgrade to keep your data
              </div>
            </div>
          </div>
          <p-button label="Upgrade Now" icon="pi pi-arrow-up" severity="warn" (click)="scrollToPricing()"></p-button>
        </div>

        <!-- Current Plan Info -->
        <div *ngIf="subscriptionStatus()?.hasActiveSubscription"
             class="current-plan-banner mb-4 p-4 border-round-lg flex align-items-center justify-content-between">
          <div class="flex align-items-center gap-3">
            <i class="pi pi-check-circle text-3xl text-green-400"></i>
            <div>
              <div class="font-bold text-lg text-white">{{ getCurrentTierName() }} Plan</div>
              <div class="text-green-200">
                Active subscription • Renews {{ formatDate(subscriptionStatus()?.currentPeriodEnd) }}
              </div>
            </div>
          </div>
          <p-button label="Manage Billing" icon="pi pi-external-link" severity="secondary" (click)="openPortal()"></p-button>
        </div>

        <!-- Pricing Cards -->
        <div id="pricing-section" class="mb-4">
          <h2 class="text-2xl font-semibold mb-3">Choose Your Plan</h2>

          <div class="grid">
            <div *ngFor="let tier of tiers" class="col-12 md:col-4">
              <div class="pricing-card h-full p-4 border-round-xl"
                   [class.popular]="tier.popular"
                   [class.current]="isCurrentTier(tier.id)">

                <!-- Popular Badge -->
                <div *ngIf="tier.popular" class="popular-badge">
                  <span class="text-xs font-bold">MOST POPULAR</span>
                </div>

                <!-- Current Badge -->
                <div *ngIf="isCurrentTier(tier.id)" class="current-badge">
                  <span class="text-xs font-bold">CURRENT PLAN</span>
                </div>

                <!-- Tier Name -->
                <h3 class="text-xl font-bold mb-2">{{ tier.name }}</h3>

                <!-- Price -->
                <div class="price-section mb-4">
                  <span class="text-4xl font-bold">\${{ tier.price }}</span>
                  <span class="text-600">/month</span>
                </div>

                <!-- Features -->
                <ul class="feature-list p-0 mb-4">
                  <li *ngFor="let feature of tier.features" class="flex align-items-center gap-2 mb-2">
                    <i class="pi pi-check text-green-500"></i>
                    <span>{{ feature }}</span>
                  </li>
                </ul>

                <!-- Action Button -->
                <div class="mt-auto">
                  <p-button
                    *ngIf="!isCurrentTier(tier.id)"
                    [label]="tier.id === 'enterprise' ? 'Contact Sales' : 'Upgrade to ' + tier.name"
                    [severity]="tier.popular ? 'primary' : 'secondary'"
                    styleClass="w-full"
                    [loading]="upgradingTier() === tier.id"
                    [disabled]="!!upgradingTier()"
                    (click)="upgradeToPlan(tier.id)">
                  </p-button>

                  <p-button
                    *ngIf="isCurrentTier(tier.id)"
                    label="Current Plan"
                    severity="success"
                    styleClass="w-full"
                    [disabled]="true">
                  </p-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Billing Portal Link (only for trial users who don't have the banner button) -->
        <div *ngIf="!subscriptionStatus()?.hasActiveSubscription"
             class="mt-4 p-4 surface-100 border-round-lg flex justify-content-between align-items-center">
          <div>
            <div class="font-semibold">Billing History & Payment Methods</div>
            <div class="text-600 text-sm">View invoices, update payment methods, or manage subscription</div>
          </div>
          <p-button
            label="Open Billing Portal"
            icon="pi pi-external-link"
            severity="secondary"
            [loading]="openingPortal()"
            (click)="openPortal()">
          </p-button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .trial-banner {
      background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
    }

    .current-plan-banner {
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
    }

    .pricing-card {
      background: var(--surface-card);
      border: 2px solid var(--surface-border);
      transition: all 0.3s ease;
      position: relative;
      display: flex;
      flex-direction: column;
    }

    .pricing-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
    }

    .pricing-card.popular {
      border-color: var(--primary-color);
      background: linear-gradient(180deg, rgba(var(--primary-color-rgb), 0.05) 0%, var(--surface-card) 100%);
    }

    .pricing-card.current {
      border-color: #10b981;
    }

    .popular-badge {
      position: absolute;
      top: -12px;
      left: 50%;
      transform: translateX(-50%);
      background: var(--primary-color);
      color: white;
      padding: 4px 16px;
      border-radius: 20px;
    }

    .current-badge {
      position: absolute;
      top: -12px;
      left: 50%;
      transform: translateX(-50%);
      background: #10b981;
      color: white;
      padding: 4px 16px;
      border-radius: 20px;
    }

    .feature-list {
      list-style: none;
    }

    .price-section {
      padding: 16px 0;
      border-bottom: 1px solid var(--surface-border);
    }
  `]
})
export class BillingComponent implements OnInit {
  private billingService = inject(BillingService);
  private messageService = inject(MessageService);

  loading = signal(true);
  subscriptionStatus = signal<SubscriptionStatus | null>(null);
  upgradingTier = signal<string | null>(null);
  openingPortal = signal(false);

  tiers: PricingTier[] = this.billingService.tiers;

  ngOnInit(): void {
    this.loadSubscriptionStatus();
  }

  loadSubscriptionStatus(): void {
    this.loading.set(true);
    this.billingService.getSubscriptionStatus().subscribe({
      next: (status) => {
        this.subscriptionStatus.set(status);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load subscription status', err);
        // Set default trial status if API fails
        this.subscriptionStatus.set({
          status: 'TRIAL',
          tier: null,
          currentPeriodEnd: null,
          trialEndsAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
          hasActiveSubscription: false
        });
        this.loading.set(false);
      }
    });
  }

  getTrialDaysRemaining(): number {
    return this.billingService.getTrialDaysRemaining(this.subscriptionStatus()?.trialEndsAt || null);
  }

  getCurrentTierName(): string {
    const tier = this.tiers.find(t => t.id === this.subscriptionStatus()?.tier);
    return tier?.name || 'Unknown';
  }

  isCurrentTier(tierId: string): boolean {
    return this.subscriptionStatus()?.tier === tierId;
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  scrollToPricing(): void {
    document.getElementById('pricing-section')?.scrollIntoView({ behavior: 'smooth' });
  }

  upgradeToPlan(tier: string): void {
    if (tier === 'enterprise') {
      window.open('mailto:sales@example.com?subject=Enterprise Plan Inquiry', '_blank');
      return;
    }

    this.upgradingTier.set(tier);

    this.billingService.createCheckoutSession(tier).subscribe({
      next: (response) => {
        // Redirect to Stripe Checkout
        window.location.href = response.checkoutUrl;
      },
      error: (err) => {
        this.upgradingTier.set(null);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err.error?.message || 'Failed to start checkout. Please try again.'
        });
      }
    });
  }

  openPortal(): void {
    this.openingPortal.set(true);

    this.billingService.createPortalSession().subscribe({
      next: (response) => {
        window.location.href = response.portalUrl;
      },
      error: (err) => {
        this.openingPortal.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err.error?.message || 'Failed to open billing portal. Please try again.'
        });
      }
    });
  }
}
