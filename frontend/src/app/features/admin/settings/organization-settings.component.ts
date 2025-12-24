import {ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
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
  imports: [CommonModule, ReactiveFormsModule, CardModule, ButtonModule, InputTextModule, SelectModule, TagModule, ToastModule, DividerModule],
  providers: [MessageService],
  template: `
    <div class="settings-container">
      <p-toast></p-toast>
      <h1 class="text-3xl font-bold mb-4">Organization Settings</h1>

      <div *ngIf="loading" class="flex justify-content-center align-items-center" style="min-height: 300px;">
        <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
      </div>

      <div *ngIf="!loading" class="grid">
        <!-- Left Column: Organization Overview -->
        <div class="col-12 lg:col-4">
          <p-card styleClass="h-full">
            <ng-template pTemplate="header">
              <div class="p-3 pb-0">
                <h3 class="m-0 flex align-items-center gap-2">
                  <i class="pi pi-building text-primary"></i>
                  Organization Overview
                </h3>
              </div>
            </ng-template>

            <div class="flex flex-column gap-3">
              <!-- Company Name -->
              <div class="info-row">
                <span class="info-label">Company</span>
                <span class="info-value font-semibold">{{ organization?.companyName || organization?.name || 'Not set' }}</span>
              </div>

              <!-- Tenant ID -->
              <div class="info-row">
                <span class="info-label">Tenant ID</span>
                <div class="flex align-items-center gap-2">
                  <code class="text-sm">{{ organization?.tenantId }}</code>
                  <button pButton icon="pi pi-copy" class="p-button-text p-button-sm" (click)="copyTenantId()" pTooltip="Copy"></button>
                </div>
              </div>

              <!-- Organization Type -->
              <div class="info-row">
                <span class="info-label">Type</span>
                <p-tag [value]="organization?.tenantType || 'PERSONAL'" [severity]="organization?.tenantType === 'ORGANIZATION' ? 'info' : 'secondary'"></p-tag>
              </div>

              <!-- Industry -->
              <div class="info-row" *ngIf="organization?.industry">
                <span class="info-label">Industry</span>
                <span class="info-value">{{ organization?.industry }}</span>
              </div>

              <!-- Size -->
              <div class="info-row" *ngIf="organization?.companySize">
                <span class="info-label">Size</span>
                <span class="info-value">{{ organization?.companySize }} employees</span>
              </div>

              <!-- Website -->
              <div class="info-row" *ngIf="organization?.website">
                <span class="info-label">Website</span>
                <a [href]="organization?.website" target="_blank" class="text-primary">{{ organization?.website }}</a>
              </div>
            </div>
          </p-card>
        </div>

        <!-- Center Column: Subscription & Plan -->
        <div class="col-12 lg:col-4">
          <p-card styleClass="h-full">
            <ng-template pTemplate="header">
              <div class="p-3 pb-0">
                <h3 class="m-0 flex align-items-center gap-2">
                  <i class="pi pi-credit-card text-primary"></i>
                  Subscription Plan
                </h3>
              </div>
            </ng-template>

            <div class="flex flex-column gap-3">
              <!-- Current Plan -->
              <div class="plan-card p-3 border-round" [ngClass]="getPlanClass()">
                <div class="flex align-items-center justify-content-between mb-2">
                  <span class="text-xl font-bold">{{ organization?.slaTier || 'STANDARD' }}</span>
                  <p-tag [value]="organization?.subscriptionStatus || 'ACTIVE'" [severity]="getStatusSeverity()"></p-tag>
                </div>
                <p class="text-600 m-0">{{ getPlanDescription() }}</p>
              </div>

              <!-- User Limit -->
              <div class="info-row">
                <span class="info-label">User Limit</span>
                <span class="info-value font-semibold">{{ organization?.maxUsers || 1 }} users</span>
              </div>

              <!-- Trial Info -->
              <div class="info-row" *ngIf="organization?.trialEndsAt">
                <span class="info-label">Trial Ends</span>
                <span class="info-value">{{ formatDate(organization?.trialEndsAt) }}</span>
              </div>

              <!-- Upgrade Button -->
              <button pButton label="Upgrade Plan" icon="pi pi-arrow-up" class="w-full mt-2" severity="secondary" [outlined]="true"></button>
            </div>
          </p-card>
        </div>

        <!-- Right Column: Admin & Contact -->
        <div class="col-12 lg:col-4">
          <p-card styleClass="h-full">
            <ng-template pTemplate="header">
              <div class="p-3 pb-0">
                <h3 class="m-0 flex align-items-center gap-2">
                  <i class="pi pi-user text-primary"></i>
                  Admin Contact
                </h3>
              </div>
            </ng-template>

            <div class="flex flex-column gap-3">
              <!-- Admin Email -->
              <div class="info-row" *ngIf="organization?.ownerEmail">
                <span class="info-label">Admin Email</span>
                <div class="flex align-items-center gap-2">
                  <span class="info-value">{{ organization?.ownerEmail }}</span>
                  <button pButton icon="pi pi-copy" class="p-button-text p-button-sm" (click)="copyOwnerEmail()" pTooltip="Copy"></button>
                </div>
              </div>

              <p-divider></p-divider>

              <!-- Quick Actions -->
              <div class="flex flex-column gap-2">
                <button pButton label="Invite Users" icon="pi pi-user-plus" class="w-full" severity="secondary" [outlined]="true" routerLink="/app/admin/users"></button>
                <button pButton label="Manage Roles" icon="pi pi-shield" class="w-full" severity="secondary" [outlined]="true" routerLink="/app/admin/roles"></button>
              </div>
            </div>
          </p-card>
        </div>
      </div>

      <!-- Edit Section -->
      <p-card class="mt-4" *ngIf="!loading">
        <ng-template pTemplate="header">
          <div class="p-3 pb-0">
            <h3 class="m-0 flex align-items-center gap-2">
              <i class="pi pi-pencil text-primary"></i>
              Edit Organization Details
            </h3>
          </div>
        </ng-template>

        <form [formGroup]="settingsForm" (ngSubmit)="saveSettings()">
          <div class="grid">
            <div class="col-12 md:col-6">
              <div class="flex flex-column mb-3">
                <label for="companyName" class="font-medium mb-2">Company Name</label>
                <input id="companyName" type="text" pInputText formControlName="companyName" placeholder="Enter company name">
              </div>
            </div>

            <div class="col-12 md:col-6">
              <div class="flex flex-column mb-3">
                <label for="industry" class="font-medium mb-2">Industry</label>
                <p-select id="industry" formControlName="industry" [options]="industryOptions" optionLabel="label" optionValue="value" placeholder="Select industry" [showClear]="true"></p-select>
              </div>
            </div>

            <div class="col-12 md:col-6">
              <div class="flex flex-column mb-3">
                <label for="companySize" class="font-medium mb-2">Company Size</label>
                <p-select id="companySize" formControlName="companySize" [options]="companySizeOptions" optionLabel="label" optionValue="value" placeholder="Select size" [showClear]="true"></p-select>
              </div>
            </div>

            <div class="col-12 md:col-6">
              <div class="flex flex-column mb-3">
                <label for="website" class="font-medium mb-2">Website</label>
                <input id="website" type="url" pInputText formControlName="website" placeholder="https://example.com">
              </div>
            </div>
          </div>

          <div class="flex gap-2 justify-content-end mt-3">
            <p-button label="Cancel" (onClick)="resetForm()" severity="secondary" [outlined]="true" type="button"></p-button>
            <p-button label="Save Changes" icon="pi pi-save" type="submit" [disabled]="!settingsForm.dirty || settingsForm.invalid" [loading]="saving"></p-button>
          </div>
        </form>
      </p-card>
    </div>
  `,
  styles: [`
    .settings-container {
      padding: 1.5rem;
      max-width: 1400px;
    }
    .info-row {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .info-label {
      font-size: 0.875rem;
      color: var(--text-color-secondary);
    }
    .info-value {
      color: var(--text-color);
    }
    .plan-card {
      background: var(--surface-50);
      border: 1px solid var(--surface-200);
    }
    .plan-card.premium {
      background: linear-gradient(135deg, #667eea20, #764ba220);
      border-color: #667eea40;
    }
    .plan-card.enterprise {
      background: linear-gradient(135deg, #f5af1920, #f1295020);
      border-color: #f5af1940;
    }
    code {
      background: var(--surface-100);
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
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
