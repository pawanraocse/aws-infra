import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { OrganizationService, OrganizationProfile } from '../../../core/services/organization.service';

@Component({
  selector: 'app-organization-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CardModule, ButtonModule, InputTextModule, SelectModule, TagModule, ToastModule],
  providers: [MessageService],
  template: `
    <div class="settings-container">
      <p-toast></p-toast>
      <h1 class="text-3xl font-bold mb-4">Organization Settings</h1>

      <p-card>
        <ng-template pTemplate="header">
          <div class="p-3">
            <h3 class="m-0">Company Profile</h3>
          </div>
        </ng-template>

        <form [formGroup]="settingsForm" (ngSubmit)="saveSettings()">
          <div class="grid">
            <div class="col-12 md:col-6">
              <div class="flex flex-column mb-3">
                <label for="tenantId" class="font-medium mb-2">Tenant ID <span class="text-500">(Read-only)</span></label>
                <div class="p-inputgroup">
                  <input 
                    id="tenantId" 
                    type="text" 
                    pInputText 
                    [value]="organization?.tenantId || ''" 
                    [readonly]="true" 
                    class="bg-gray-100">
                  <button 
                    type="button" 
                    pButton 
                    icon="pi pi-copy" 
                    (click)="copyTenantId()" 
                    pTooltip="Copy Tenant ID">
                  </button>
                </div>
              </div>

              <div class="flex flex-column mb-3">
                <label for="companyName" class="font-medium mb-2">Company Name</label>
                <input 
                  id="companyName" 
                  type="text" 
                  pInputText 
                  formControlName="companyName"
                  placeholder="Enter company name">
                <small *ngIf="settingsForm.get('companyName')?.invalid && settingsForm.get('companyName')?.touched" 
                       class="p-error">
                  Maximum 255 characters allowed
                </small>
              </div>

              <div class="flex flex-column mb-3">
                <label for="industry" class="font-medium mb-2">Industry</label>
                <p-select 
                  id="industry" 
                  formControlName="industry"
                  [options]="industryOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Select industry"
                  [showClear]="true">
                </p-select>
              </div>

              <div class="flex flex-column mb-3">
                <label for="companySize" class="font-medium mb-2">Company Size</label>
                <p-select 
                  id="companySize" 
                  formControlName="companySize"
                  [options]="companySizeOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Select company size"
                  [showClear]="true">
                </p-select>
              </div>
            </div>

            <div class="col-12 md:col-6">
              <div class="flex flex-column mb-3">
                <label for="slaTier" class="font-medium mb-2">Current Tier <span class="text-500">(Read-only)</span></label>
                <p-tag [value]="organization?.slaTier || 'STANDARD'" [severity]="getTierSeverity()"></p-tag>
              </div>

              <div class="flex flex-column mb-3">
                <label for="website" class="font-medium mb-2">Website</label>
                <input 
                  id="website" 
                  type="url" 
                  pInputText 
                  formControlName="website"
                  placeholder="https://example.com">
                <small *ngIf="settingsForm.get('website')?.invalid && settingsForm.get('website')?.touched" 
                       class="p-error">
                  Please enter a valid URL
                </small>
              </div>

              <div class="flex flex-column mb-3">
                <label for="logoUrl" class="font-medium mb-2">Logo URL</label>
                <input 
                  id="logoUrl" 
                  type="url" 
                  pInputText 
                  formControlName="logoUrl"
                  placeholder="https://example.com/logo.png">
                <small *ngIf="settingsForm.get('logoUrl')?.invalid && settingsForm.get('logoUrl')?.touched" 
                       class="p-error">
                  Please enter a valid image URL (png, jpg, jpeg, svg)
                </small>
              </div>

              <div class="flex flex-column mb-3">
                <label class="font-medium mb-2">Organization Type <span class="text-500">(Read-only)</span></label>
                <p-tag [value]="organization?.tenantType || 'PERSONAL'" severity="info"></p-tag>
              </div>
            </div>
          </div>

          <div class="flex gap-2 justify-content-end mt-4">
            <p-button 
              label="Cancel" 
              (onClick)="resetForm()" 
              severity="secondary" 
              [outlined]="true"
              type="button">
            </p-button>
            <p-button 
              label="Save Changes" 
              icon="pi pi-save" 
              type="submit"
              [disabled]="!settingsForm.dirty || settingsForm.invalid"
              [loading]="saving">
            </p-button>
          </div>
        </form>

        <div *ngIf="loading" class="flex justify-content-center align-items-center" style="min-height: 300px;">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .settings-container {
      padding: 1.5rem;
      max-width: 1200px;
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
    { label: '1-10 employees', value: '1-10' },
    { label: '11-50 employees', value: '11-50' },
    { label: '51-200 employees', value: '51-200' },
    { label: '201-500 employees', value: '201-500' },
    { label: '501-1000 employees', value: '501-1000' },
    { label: '1000+ employees', value: '1001+' }
  ];

  private fb = inject(FormBuilder);
  private organizationService = inject(OrganizationService);
  private messageService = inject(MessageService);

  constructor() {
    this.settingsForm = this.fb.group({
      companyName: ['', [Validators.maxLength(255)]],
      industry: [''],
      companySize: [''],
      website: ['', [Validators.pattern(/^(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?$/)]],
      logoUrl: ['', [Validators.pattern(/^(https?:\/\/).+\.(png|jpg|jpeg|svg)$/)]]
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
          website: org.website || '',
          logoUrl: org.logoUrl || ''
        });
        this.settingsForm.markAsPristine();
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading organization:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load organization settings'
        });
        this.loading = false;
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
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: 'Organization settings updated successfully'
          });
          this.saving = false;
        },
        error: (err) => {
          console.error('Error updating organization:', err);
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to update organization settings'
          });
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
        website: this.organization.website || '',
        logoUrl: this.organization.logoUrl || ''
      });
      this.settingsForm.markAsPristine();
    }
  }

  getTierSeverity(): 'success' | 'info' | 'warn' | 'danger' {
    const tier = this.organization?.slaTier?.toUpperCase();
    if (tier === 'ENTERPRISE') return 'success';
    if (tier === 'PREMIUM') return 'info';
    if (tier === 'STANDARD') return 'warn';
    return 'info';
  }

  copyTenantId() {
    if (this.organization?.tenantId) {
      navigator.clipboard.writeText(this.organization.tenantId);
      this.messageService.add({
        severity: 'success',
        summary: 'Copied',
        detail: 'Tenant ID copied to clipboard'
      });
    }
  }
}
