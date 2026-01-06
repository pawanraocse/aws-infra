import {ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {TagModule} from 'primeng/tag';
import {ToastModule} from 'primeng/toast';
import {DividerModule} from 'primeng/divider';
import {MessageService} from 'primeng/api';
import {OrganizationProfile, OrganizationService} from '../../../core/services/organization.service';

@Component({
  selector: 'app-organization-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, CardModule, ButtonModule, InputTextModule, SelectModule, TagModule, ToastModule, DividerModule],
  providers: [MessageService],
  template: `
    <div class="settings-container fadein animation-duration-300">
      <p-toast></p-toast>

      <div class="mb-5">
        <h1 class="text-3xl font-bold mb-2 text-900">Organization Settings</h1>
        <p class="text-500 m-0">Manage your company profile, subscription, and team access.</p>
      </div>

      <div *ngIf="loading" class="flex justify-content-center align-items-center" style="min-height: 400px;">
        <i class="pi pi-spin pi-spinner text-primary" style="font-size: 2.5rem"></i>
      </div>

      <div *ngIf="!loading" class="grid">
        <!-- Left Column: Organization Overview -->
        <div class="col-12 lg:col-4">
          <p-card styleClass="h-full modern-card surface-card border-none shadow-2">
            <ng-template pTemplate="header">
              <div class="p-4 pb-0 flex align-items-center gap-3">
                <div class="icon-box bg-primary-50 text-primary border-circle p-2 flex align-items-center justify-content-center">
                  <i class="pi pi-building text-xl"></i>
                </div>
                <h3 class="m-0 text-xl font-semibold text-800">Overview</h3>
              </div>
            </ng-template>

            <div class="flex flex-column gap-4 mt-3">
              <div class="info-group">
                <span class="text-sm font-medium text-500 block mb-1">Company Name</span>
                <span class="text-lg font-medium text-900">{{ organization?.companyName || organization?.name || 'Not set' }}</span>
              </div>

              <div class="info-group">
                <span class="text-sm font-medium text-500 block mb-1">Tenant ID</span>
                <div class="flex align-items-center gap-2 surface-100 border-round p-2">
                  <code class="text-sm text-700 flex-1 overflow-hidden text-overflow-ellipsis" [title]="organization?.tenantId">{{ organization?.tenantId }}</code>
                  <button pButton icon="pi pi-copy" class="p-button-text p-button-secondary p-button-sm w-2rem h-2rem" (click)="copyTenantId()" pTooltip="Copy ID"></button>
                </div>
              </div>

              <div class="flex gap-4">
                <div class="info-group flex-1">
                  <span class="text-sm font-medium text-500 block mb-2">Type</span>
                  <p-tag [value]="organization?.tenantType || 'PERSONAL'" [severity]="organization?.tenantType === 'ORGANIZATION' ? 'info' : 'secondary'" styleClass="text-xs uppercase px-2 py-1"></p-tag>
                </div>
                <div class="info-group flex-1" *ngIf="organization?.companySize">
                  <span class="text-sm font-medium text-500 block mb-1">Size</span>
                  <span class="text-900">{{ organization?.companySize }} people</span>
                </div>
              </div>

              <div class="info-group" *ngIf="organization?.website">
                <span class="text-sm font-medium text-500 block mb-1">Website</span>
                <a [href]="organization?.website" target="_blank" class="text-primary hover:text-primary-600 no-underline font-medium hover:underline flex align-items-center gap-1">
                  {{ organization?.website }}
                  <i class="pi pi-external-link text-xs"></i>
                </a>
              </div>
            </div>
          </p-card>
        </div>

        <!-- Center Column: Subscription -->
        <div class="col-12 lg:col-4">
          <p-card styleClass="h-full modern-card surface-card border-none shadow-2">
            <ng-template pTemplate="header">
              <div class="p-4 pb-0 flex align-items-center gap-3">
                <div class="icon-box bg-purple-50 text-purple-500 border-circle p-2 flex align-items-center justify-content-center">
                  <i class="pi pi-credit-card text-xl"></i>
                </div>
                <h3 class="m-0 text-xl font-semibold text-800">Subscription</h3>
              </div>
            </ng-template>

            <div class="flex flex-column gap-4 mt-3">
              <!-- Plan Card -->
              <div class="plan-card p-4 border-round-xl relative overflow-hidden" [ngClass]="getPlanClass()">
                <div class="relative z-1">
                  <div class="flex align-items-center justify-content-between mb-3">
                    <span class="text-sm font-bold uppercase tracking-wide opacity-80">{{ organization?.slaTier || 'STANDARD' }} PLAN</span>
                    <p-tag [value]="organization?.subscriptionStatus || 'ACTIVE'" [severity]="getStatusSeverity()"></p-tag>
                  </div>
                  <div class="text-2xl font-bold mb-2">{{ organization?.slaTier === 'ENTERPRISE' ? 'Unlimited Access' : (organization?.slaTier === 'PREMIUM' ? 'Growth Toolkit' : 'Starter Kit') }}</div>
                  <p class="m-0 opacity-90 text-sm line-height-3">{{ getPlanDescription() }}</p>
                </div>
                <!-- Background decoration -->
                <div class="absolute top-0 right-0 w-full h-full opacity-10 pointer-events-none"
                     style="background: radial-gradient(circle at top right, white, transparent 70%)"></div>
              </div>

              <div class="flex justify-content-between align-items-center border-bottom-1 surface-border pb-3">
                <span class="text-500">User Limit</span>
                <span class="font-bold text-900">{{ organization?.maxUsers || 1 }} users</span>
              </div>

              <div class="flex justify-content-between align-items-center border-bottom-1 surface-border pb-3" *ngIf="organization?.trialEndsAt">
                <span class="text-500">Trial Ends</span>
                <span class="font-medium text-orange-500">{{ formatDate(organization?.trialEndsAt) }}</span>
              </div>

              <button pButton label="Upgrade Plan" icon="pi pi-bolt" class="w-full p-button-outlined p-button-secondary hover:surface-100 transition-colors"></button>
            </div>
          </p-card>
        </div>

        <!-- Right Column: Quick Actions -->
        <div class="col-12 lg:col-4">
          <p-card styleClass="h-full modern-card surface-card border-none shadow-2">
            <ng-template pTemplate="header">
              <div class="p-4 pb-0 flex align-items-center gap-3">
                <div class="icon-box bg-blue-50 text-blue-500 border-circle p-2 flex align-items-center justify-content-center">
                  <i class="pi pi-cog text-xl"></i>
                </div>
                <h3 class="m-0 text-xl font-semibold text-800">Management</h3>
              </div>
            </ng-template>

            <div class="flex flex-column gap-4 mt-3">
              <div class="info-group">
                <span class="text-sm font-medium text-500 block mb-1">Primary Admin</span>
                <div class="flex align-items-center gap-2 p-2 surface-50 border-round">
                    <div class="flex-1 overflow-hidden">
                        <span class="text-900 font-medium block text-overflow-ellipsis">{{ organization?.ownerEmail }}</span>
                    </div>
                    <button pButton icon="pi pi-copy" class="p-button-text p-button-secondary p-button-sm w-2rem h-2rem" (click)="copyOwnerEmail()" pTooltip="Copy Email"></button>
                </div>
              </div>

              <p-divider></p-divider>

              <div class="flex flex-column gap-2">
                <span class="text-sm font-medium text-500 block mb-2">Quick Actions</span>
                <button pButton label="Invite New Users" icon="pi pi-user-plus" class="w-full justify-content-start px-3 py-2 text-left" severity="secondary" [outlined]="true" routerLink="/app/admin/users"></button>
                <button pButton label="Configure Roles" icon="pi pi-shield" class="w-full justify-content-start px-3 py-2 text-left" severity="secondary" [outlined]="true" routerLink="/app/admin/roles"></button>
                <button pButton label="Access Permissions" icon="pi pi-lock" class="w-full justify-content-start px-3 py-2 text-left" severity="secondary" [outlined]="true" routerLink="/app/admin/permissions"></button>
              </div>
            </div>
          </p-card>
        </div>
      </div>

      <!-- Edit Form -->
      <div class="mt-5" *ngIf="!loading">
        <p-card styleClass="modern-card surface-card border-none shadow-2">
            <ng-template pTemplate="header">
                <div class="p-4 pb-0 border-bottom-1 surface-border">
                    <h3 class="m-0 text-xl font-semibold text-800 mb-2">Update Details</h3>
                    <p class="text-500 m-0 text-sm pb-3">Keep your organization profile up-to-date.</p>
                </div>
            </ng-template>

            <form [formGroup]="settingsForm" (ngSubmit)="saveSettings()" class="pt-4">
                <div class="grid formgrid p-fluid">
                    <div class="col-12 md:col-6 mb-4">
                        <label for="companyName" class="font-medium text-900 block mb-2">Company Name</label>
                        <input id="companyName" type="text" pInputText formControlName="companyName" class="w-full" placeholder="e.g. Acme Corp">
                    </div>

                    <div class="col-12 md:col-6 mb-4">
                        <label for="website" class="font-medium text-900 block mb-2">Website URL</label>
                        <input id="website" type="url" pInputText formControlName="website" class="w-full" placeholder="https://">
                    </div>

                    <div class="col-12 md:col-6 mb-4">
                        <label for="industry" class="font-medium text-900 block mb-2">Industry</label>
                        <p-select id="industry" formControlName="industry" [options]="industryOptions" optionLabel="label" optionValue="value" placeholder="Select industry" [showClear]="true" styleClass="w-full"></p-select>
                    </div>

                    <div class="col-12 md:col-6 mb-4">
                        <label for="companySize" class="font-medium text-900 block mb-2">Company Size</label>
                        <p-select id="companySize" formControlName="companySize" [options]="companySizeOptions" optionLabel="label" optionValue="value" placeholder="Select size" [showClear]="true" styleClass="w-full"></p-select>
                    </div>
                </div>

                <div class="flex gap-3 justify-content-end mt-4 pt-3 border-top-1 surface-border">
                    <p-button label="Discard Changes" (onClick)="resetForm()" severity="secondary" [outlined]="true" styleClass="w-auto"></p-button>
                    <p-button label="Save Changes" icon="pi pi-check" type="submit" [disabled]="!settingsForm.dirty || settingsForm.invalid" [loading]="saving" styleClass="w-auto"></p-button>
                </div>
            </form>
        </p-card>
      </div>
    </div>
  `,
  styles: [`
    .settings-container {
      padding: 2rem;
      max-width: 1400px;
      margin: 0 auto;
    }
    .modern-card {
        border-radius: 12px;
        transition: transform 0.2s, box-shadow 0.2s;
    }
    .modern-card:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 20px rgba(0,0,0,0.06) !important;
    }
    .icon-box {
        width: 40px;
        height: 40px;
    }
    .plan-card {
      background: var(--surface-0);
      border: 1px solid var(--surface-200);
      transition: all 0.3s ease;
      color: var(--text-color);
    }
    .plan-card.premium {
      background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
      color: white;
      border: none;
    }
    .plan-card.enterprise {
      background: linear-gradient(135deg, #0f172a 0%, #334155 100%);
      color: white;
      border: none;
    }
    .plan-card.premium p,
    .plan-card.enterprise p {
        color: rgba(255,255,255,0.8) !important;
    }
    code {
      background: var(--surface-100);
      padding: 0.25rem 0.5rem;
      border-radius: 6px;
      font-family: 'JetBrains Mono', monospace;
    }
  `]
})
export class OrganizationSettingsComponent implements OnInit {
  settingsForm: FormGroup;
  organization: OrganizationProfile | null = null;
  loading = true;
  saving = false;

  industryOptions = [
    { label: 'Technology', value: 'Technology' },
    { label: 'Healthcare', value: 'Healthcare' },
    { label: 'Finance', value: 'Finance' },
    { label: 'Education', value: 'Education' },
    { label: 'Retail', value: 'Retail' },
    { label: 'Manufacturing', value: 'Manufacturing' },
    { label: 'Professional Services', value: 'Professional Services' },
    { label: 'Other', value: 'Other' }
  ];

  companySizeOptions = [
    { label: '1-10', value: '1-10' },
    { label: '11-50', value: '11-50' },
    { label: '51-200', value: '51-200' },
    { label: '201-500', value: '201-500' },
    { label: '501-1000', value: '501-1000' },
    { label: '1000+', value: '1001+' }
  ];

  private fb = inject(FormBuilder);
  private organizationService = inject(OrganizationService);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);

  constructor() {
    this.settingsForm = this.fb.group({
      companyName: ['', [Validators.maxLength(255)]],
      industry: [''],
      companySize: [''],
      website: ['', [Validators.pattern(/^(https?:\/\/)?[\w.-]+\.[a-z]{2,}(\/.*)?$/i)]]
    });
  }

  ngOnInit() {
    this.loadOrganization();
  }

  loadOrganization() {
    this.loading = true;
    this.organizationService.getOrganization().subscribe({
      next: (org) => {
        this.organization = org;
        this.settingsForm.patchValue({
          companyName: org.companyName || '',
          industry: org.industry || '',
          companySize: org.companySize || '',
          website: org.website || ''
        });
        this.settingsForm.markAsPristine();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading organization:', err);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to load organization settings' });
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  saveSettings() {
    if (this.settingsForm.valid && this.settingsForm.dirty) {
      this.saving = true;
      this.organizationService.updateOrganization(this.settingsForm.value).subscribe({
        next: (updatedOrg) => {
          this.organization = updatedOrg;
          this.settingsForm.markAsPristine();
          this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Organization updated' });
          this.saving = false;
        },
        error: (err) => {
          console.error('Error updating organization:', err);
          this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to update' });
          this.saving = false;
        }
      });
    }
  }

  resetForm() {
    if (this.organization) {
      this.settingsForm.patchValue({
        companyName: this.organization.companyName || '',
        industry: this.organization.industry || '',
        companySize: this.organization.companySize || '',
        website: this.organization.website || ''
      });
      this.settingsForm.markAsPristine();
    }
  }

  getPlanClass(): string {
    const tier = this.organization?.slaTier?.toUpperCase();
    if (tier === 'PREMIUM') return 'premium';
    if (tier === 'ENTERPRISE') return 'enterprise';
    return '';
  }

  getPlanDescription(): string {
    const tier = this.organization?.slaTier?.toUpperCase();
    if (tier === 'ENTERPRISE') return 'Full access with priority support';
    if (tier === 'PREMIUM') return 'Advanced features for growing teams';
    return 'Essential features for small teams';
  }

  getTierSeverity(): 'success' | 'info' | 'warn' | 'danger' {
    const tier = this.organization?.slaTier?.toUpperCase();
    if (tier === 'ENTERPRISE') return 'success';
    if (tier === 'PREMIUM') return 'info';
    return 'warn';
  }

  getStatusSeverity(): 'success' | 'info' | 'warn' | 'danger' {
    const status = this.organization?.subscriptionStatus?.toUpperCase();
    if (status === 'ACTIVE') return 'success';
    if (status === 'TRIAL') return 'info';
    if (status === 'EXPIRED') return 'danger';
    return 'warn';
  }

  formatDate(dateStr: string | undefined): string {
    if (!dateStr) return '';
    try {
      return new Date(dateStr).toLocaleDateString();
    } catch {
      return dateStr;
    }
  }

  copyTenantId() {
    if (this.organization?.tenantId) {
      navigator.clipboard.writeText(this.organization.tenantId);
      this.messageService.add({ severity: 'success', summary: 'Copied', detail: 'Tenant ID copied' });
    }
  }

  copyOwnerEmail() {
    if (this.organization?.ownerEmail) {
      navigator.clipboard.writeText(this.organization.ownerEmail);
      this.messageService.add({ severity: 'success', summary: 'Copied', detail: 'Email copied' });
    }
  }
}
