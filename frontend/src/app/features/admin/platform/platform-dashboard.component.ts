import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface TenantSummary {
  id: string;
  name: string;
  tenantType: string;
  status: string;
  createdAt: string;
}

interface PlatformStats {
  totalTenants: number;
  activeTenants: number;
  personalTenants: number;
  organizationTenants: number;
}

@Component({
  selector: 'app-platform-dashboard',
  standalone: true,
  imports: [CommonModule, CardModule, ButtonModule, TagModule, TableModule],
  template: `
    <div class="p-4">
      <div class="flex justify-content-between align-items-center mb-4">
        <div>
          <h1 class="text-3xl font-bold text-900 m-0">Platform Dashboard</h1>
          <p class="text-600 mt-2">System-wide overview and tenant management</p>
        </div>
        <p-tag value="SUPER-ADMIN" severity="danger" styleClass="text-base px-3 py-2"></p-tag>
      </div>

      <!-- Stats Cards -->
      <div class="grid mb-4">
        <div class="col-12 md:col-3">
          <p-card styleClass="shadow-soft">
            <div class="flex flex-column align-items-center">
              <i class="pi pi-building text-4xl text-primary mb-2"></i>
              <span class="text-3xl font-bold text-primary">{{ stats().totalTenants }}</span>
              <span class="text-600">Total Tenants</span>
            </div>
          </p-card>
        </div>
        <div class="col-12 md:col-3">
          <p-card styleClass="shadow-soft">
            <div class="flex flex-column align-items-center">
              <i class="pi pi-check-circle text-4xl text-green-500 mb-2"></i>
              <span class="text-3xl font-bold text-green-500">{{ stats().activeTenants }}</span>
              <span class="text-600">Active</span>
            </div>
          </p-card>
        </div>
        <div class="col-12 md:col-3">
          <p-card styleClass="shadow-soft">
            <div class="flex flex-column align-items-center">
              <i class="pi pi-user text-4xl text-blue-500 mb-2"></i>
              <span class="text-3xl font-bold text-blue-500">{{ stats().personalTenants }}</span>
              <span class="text-600">Personal</span>
            </div>
          </p-card>
        </div>
        <div class="col-12 md:col-3">
          <p-card styleClass="shadow-soft">
            <div class="flex flex-column align-items-center">
              <i class="pi pi-users text-4xl text-purple-500 mb-2"></i>
              <span class="text-3xl font-bold text-purple-500">{{ stats().organizationTenants }}</span>
              <span class="text-600">Organizations</span>
            </div>
          </p-card>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="grid mb-4">
        <div class="col-12">
          <p-card header="Quick Actions" styleClass="shadow-soft">
            <div class="flex gap-3 flex-wrap">
              <p-button label="View All Tenants" icon="pi pi-list" (click)="navigateToTenants()"></p-button>
            </div>
          </p-card>
        </div>
      </div>


      <!-- Recent Tenants Preview -->
      <div class="grid">
        <div class="col-12">
          <p-card header="Recent Tenants" styleClass="shadow-soft">
            <p-table [value]="recentTenants()" [loading]="loading()" styleClass="p-datatable-sm">
              <ng-template pTemplate="header">
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Created</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-tenant>
                <tr>
                  <td>{{ tenant.name || tenant.id }}</td>
                  <td>
                    <p-tag [value]="tenant.tenantType" 
                           [severity]="tenant.tenantType === 'ORGANIZATION' ? 'info' : 'success'">
                    </p-tag>
                  </td>
                  <td>
                    <p-tag [value]="tenant.status" 
                           [severity]="getStatusSeverity(tenant.status)">
                    </p-tag>
                  </td>
                  <td>{{ tenant.createdAt | date:'short' }}</td>
                </tr>
              </ng-template>
              <ng-template pTemplate="emptymessage">
                <tr>
                  <td colspan="4" class="text-center text-600 p-4">
                    No tenants found. The system is ready for tenant provisioning.
                  </td>
                </tr>
              </ng-template>
            </p-table>
            <div class="mt-3 text-right" *ngIf="recentTenants().length > 0">
              <p-button label="View All" icon="pi pi-arrow-right" [text]="true" (click)="navigateToTenants()"></p-button>
            </div>
          </p-card>
        </div>
      </div>
    </div>
  `
})
export class PlatformDashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);

  loading = signal(false);
  stats = signal<PlatformStats>({ totalTenants: 0, activeTenants: 0, personalTenants: 0, organizationTenants: 0 });
  recentTenants = signal<TenantSummary[]>([]);

  ngOnInit() {
    this.loadDashboardData();
  }

  loadDashboardData() {
    this.loading.set(true);

    // Load tenants from platform service
    this.http.get<TenantSummary[]>(`${environment.apiUrl}/platform/api/tenants`).subscribe({
      next: (tenants) => {
        this.recentTenants.set(tenants.slice(0, 5));

        // Calculate stats
        const active = tenants.filter(t => t.status === 'ACTIVE').length;
        const personal = tenants.filter(t => t.tenantType === 'PERSONAL').length;
        const org = tenants.filter(t => t.tenantType === 'ORGANIZATION').length;

        this.stats.set({
          totalTenants: tenants.length,
          activeTenants: active,
          personalTenants: personal,
          organizationTenants: org
        });
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading tenants:', err);
        this.loading.set(false);
      }
    });
  }

  getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'PENDING': return 'warn';
      case 'SUSPENDED': return 'danger';
      default: return 'info';
    }
  }

  navigateToTenants() {
    this.router.navigate(['/app/admin/tenants']);
  }

  navigateToRoles() {
    this.router.navigate(['/app/admin/roles']);
  }
}
