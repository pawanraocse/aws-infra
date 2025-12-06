import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { OrganizationService, OrganizationProfile } from '../../../core/services/organization.service';
import { UserStatsService, UserStats } from '../../../core/services/user-stats.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CardModule, ButtonModule, TagModule],
  template: `
    <div class="dashboard-container">
      <h1 class="text-3xl font-bold mb-4">Admin Dashboard</h1>

      <!-- Stats Cards -->
      <div class="grid">
        <div class="col-12 md:col-6 lg:col-3">
          <p-card>
            <div class="flex flex-column">
              <span class="text-500 font-medium mb-2">Total Users</span>
              <span class="text-3xl font-bold text-primary">{{ stats?.totalUsers || 0 }}</span>
              <span class="text-sm text-500 mt-2">
                <i class="pi pi-users mr-1"></i>Active members
              </span>
            </div>
          </p-card>
        </div>

        <div class="col-12 md:col-6 lg:col-3">
          <p-card>
            <div class="flex flex-column">
              <span class="text-500 font-medium mb-2">Pending Invites</span>
              <span class="text-3xl font-bold text-orange-500">{{ stats?.pendingInvitations || 0 }}</span>
              <span class="text-sm text-500 mt-2">
                <i class="pi pi-envelope mr-1"></i>Awaiting response
              </span>
            </div>
          </p-card>
        </div>

        <div class="col-12 md:col-6 lg:col-3">
          <p-card>
            <div class="flex flex-column">
              <span class="text-500 font-medium mb-2">Admins</span>
              <span class="text-3xl font-bold text-purple-500">{{ stats?.adminCount || 0 }}</span>
              <span class="text-sm text-500 mt-2">
                <i class="pi pi-shield mr-1"></i>Administrator roles
              </span>
            </div>
          </p-card>
        </div>

        <div class="col-12 md:col-6 lg:col-3">
          <p-card>
            <div class="flex flex-column">
              <span class="text-500 font-medium mb-2">Current Tier</span>
              <p-tag [value]="organization?.slaTier || 'STANDARD'" [severity]="getTierSeverity()"></p-tag>
              <span class="text-sm text-500 mt-2">
                <i class="pi pi-star mr-1"></i>Subscription plan
              </span>
            </div>
          </p-card>
        </div>
      </div>

      <!-- Organization Info Card -->
      <div class="grid mt-4">
        <div class="col-12 lg:col-8">
          <p-card>
            <ng-template pTemplate="header">
              <div class="p-3 flex justify-content-between align-items-center">
                <h3 class="m-0">Organization Information</h3>
                <p-button 
                  label="Edit Settings" 
                  icon="pi pi-cog" 
                  (onClick)="navigateToSettings()"
                  [outlined]="true">
                </p-button>
              </div>
            </ng-template>
            <div class="grid">
              <div class="col-12 md:col-6">
                <div class="flex flex-column mb-3">
                  <span class="text-500 text-sm mb-1">Organization Name</span>
                  <span class="font-semibold">{{ organization?.name || '-' }}</span>
                </div>
                <div class="flex flex-column mb-3">
                  <span class="text-500 text-sm mb-1">Company Name</span>
                  <span class="font-semibold">{{ organization?.companyName || 'Not set' }}</span>
                </div>
                <div class="flex flex-column mb-3">
                  <span class="text-500 text-sm mb-1">Industry</span>
                  <span class="font-semibold">{{ organization?.industry || 'Not set' }}</span>
                </div>
              </div>
              <div class="col-12 md:col-6">
                <div class="flex flex-column mb-3">
                  <span class="text-500 text-sm mb-1">Tenant ID</span>
                  <div class="flex align-items-center gap-2">
                    <code class="text-sm bg-gray-100 p-2 border-round">{{ organization?.tenantId }}</code>
                    <p-button 
                      icon="pi pi-copy" 
                      (onClick)="copyTenantId()" 
                      [text]="true" 
                      severity="secondary"
                      pTooltip="Copy Tenant ID">
                    </p-button>
                  </div>
                </div>
                <div class="flex flex-column mb-3">
                  <span class="text-500 text-sm mb-1">Company Size</span>
                  <span class="font-semibold">{{ organization?.companySize || 'Not set' }}</span>
                </div>
                <div class="flex flex-column mb-3">
                  <span class="text-500 text-sm mb-1">Website</span>
                  <a *ngIf="organization?.website" 
                     [href]="organization!.website" 
                     target="_blank" 
                     class="text-primary">
                    {{ organization!.website }}
                  </a>
                  <span *ngIf="!organization?.website" class="text-500">Not set</span>
                </div>
              </div>
            </div>
          </p-card>
        </div>

        <!-- Quick Actions -->
        <div class="col-12 lg:col-4">
          <p-card>
            <ng-template pTemplate="header">
              <div class="p-3">
                <h3 class="m-0">Quick Actions</h3>
              </div>
            </ng-template>
            <div class="flex flex-column gap-2">
              <p-button 
                label="Invite User" 
                icon="pi pi-user-plus" 
                (onClick)="navigateToUsers()"
                [outlined]="true"
                styleClass="w-full">
              </p-button>
              <p-button 
                label="Manage Roles" 
                icon="pi pi-shield" 
                (onClick)="navigateToRoles()"
                [outlined]="true"
                styleClass="w-full">
              </p-button>
              <p-button 
                label="View Users" 
                icon="pi pi-users" 
                (onClick)="navigateToUsers()"
                severity="secondary"
                [outlined]="true"
                styleClass="w-full">
              </p-button>
            </div>
          </p-card>
        </div>
      </div>

      <!-- Loading State -->
      <div *ngIf="loading" class="flex justify-content-center align-items-center" style="min-height: 400px;">
        <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-container {
      padding: 1.5rem;
    }

    code {
      font-family: monospace;
      font-size: 0.875rem;
    }
  `]
})
export class DashboardComponent implements OnInit {
  organization: OrganizationProfile | null = null;
  stats: UserStats | null = null;
  loading = true;

  private organizationService = inject(OrganizationService);
  private userStatsService = inject(UserStatsService);
  private router = inject(Router);

  ngOnInit() {
    this.loadDashboardData();
  }

  loadDashboardData() {
    this.loading = true;
    forkJoin({
      organization: this.organizationService.getOrganization(),
      stats: this.userStatsService.getUserStats()
    }).subscribe({
      next: (data) => {
        this.organization = data.organization;
        this.stats = data.stats;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading dashboard data:', err);
        this.loading = false;
      }
    });
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
    }
  }

  navigateToSettings() {
    this.router.navigate(['/admin/settings/organization']);
  }

  navigateToUsers() {
    this.router.navigate(['/admin/users']);
  }

  navigateToRoles() {
    this.router.navigate(['/admin/roles']);
  }
}
