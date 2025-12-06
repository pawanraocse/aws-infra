import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { CardModule } from 'primeng/card';
import { MessageService, ConfirmationService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface Tenant {
    id: string;
    name: string;
    tenantType: string;
    status: string;
    slaTier: string;
    createdAt: string;
    databaseName: string;
}

@Component({
    selector: 'app-tenant-list',
    standalone: true,
    imports: [CommonModule, TableModule, ButtonModule, TagModule, ToastModule, ConfirmDialogModule, CardModule],
    providers: [MessageService, ConfirmationService],
    template: `
    <div class="p-4">
      <p-toast></p-toast>
      <p-confirmDialog></p-confirmDialog>

      <div class="flex justify-content-between align-items-center mb-4">
        <div>
          <h1 class="text-3xl font-bold text-900 m-0">Tenant Management</h1>
          <p class="text-600 mt-2">View and manage all tenants in the platform</p>
        </div>
      </div>

      <p-card styleClass="shadow-soft">
        <p-table 
          [value]="tenants()" 
          [loading]="loading()"
          [paginator]="true"
          [rows]="10"
          [rowsPerPageOptions]="[10, 25, 50]"
          styleClass="p-datatable-striped">
          
          <ng-template pTemplate="header">
            <tr>
              <th pSortableColumn="id">Tenant ID <p-sortIcon field="id"></p-sortIcon></th>
              <th pSortableColumn="name">Name <p-sortIcon field="name"></p-sortIcon></th>
              <th pSortableColumn="tenantType">Type <p-sortIcon field="tenantType"></p-sortIcon></th>
              <th pSortableColumn="status">Status <p-sortIcon field="status"></p-sortIcon></th>
              <th pSortableColumn="slaTier">SLA Tier <p-sortIcon field="slaTier"></p-sortIcon></th>
              <th pSortableColumn="createdAt">Created <p-sortIcon field="createdAt"></p-sortIcon></th>
              <th>Actions</th>
            </tr>
          </ng-template>
          
          <ng-template pTemplate="body" let-tenant>
            <tr>
              <td class="font-mono text-sm">{{ tenant.id | slice:0:12 }}...</td>
              <td>{{ tenant.name || 'N/A' }}</td>
              <td>
                <p-tag [value]="tenant.tenantType" 
                       [severity]="tenant.tenantType === 'ORGANIZATION' ? 'info' : 'success'">
                </p-tag>
              </td>
              <td>
                <p-tag [value]="tenant.status" [severity]="getStatusSeverity(tenant.status)"></p-tag>
              </td>
              <td>
                <p-tag [value]="tenant.slaTier || 'STANDARD'" [severity]="getTierSeverity(tenant.slaTier)"></p-tag>
              </td>
              <td>{{ tenant.createdAt | date:'mediumDate' }}</td>
              <td>
                <div class="flex gap-2">
                  <p-button icon="pi pi-eye" [rounded]="true" [text]="true" 
                            severity="info" pTooltip="View Details"
                            (click)="viewTenant(tenant)"></p-button>
                  <p-button icon="pi pi-trash" [rounded]="true" [text]="true" 
                            severity="danger" pTooltip="Delete Tenant"
                            (click)="confirmDelete(tenant)"></p-button>
                </div>
              </td>
            </tr>
          </ng-template>
          
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="7" class="text-center text-600 p-4">
                <i class="pi pi-inbox text-4xl mb-3 block"></i>
                No tenants found in the system.
              </td>
            </tr>
          </ng-template>
        </p-table>
      </p-card>
    </div>
  `
})
export class TenantListComponent implements OnInit {
    private http = inject(HttpClient);
    private messageService = inject(MessageService);
    private confirmationService = inject(ConfirmationService);

    loading = signal(false);
    tenants = signal<Tenant[]>([]);

    ngOnInit() {
        this.loadTenants();
    }

    loadTenants() {
        this.loading.set(true);

        this.http.get<Tenant[]>(`${environment.apiUrl}/platform/api/tenants`).subscribe({
            next: (tenants) => {
                this.tenants.set(tenants);
                this.loading.set(false);
            },
            error: (err) => {
                console.error('Error loading tenants:', err);
                this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Failed to load tenants'
                });
                this.loading.set(false);
            }
        });
    }

    getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' {
        switch (status) {
            case 'ACTIVE': return 'success';
            case 'PENDING': return 'warn';
            case 'PROVISION_ERROR': return 'danger';
            case 'SUSPENDED': return 'danger';
            default: return 'info';
        }
    }

    getTierSeverity(tier: string): 'success' | 'info' | 'warn' | 'danger' {
        switch (tier?.toUpperCase()) {
            case 'ENTERPRISE': return 'success';
            case 'PREMIUM': return 'info';
            case 'STANDARD': return 'warn';
            default: return 'info';
        }
    }

    viewTenant(tenant: Tenant) {
        this.messageService.add({
            severity: 'info',
            summary: 'Tenant Details',
            detail: `Tenant: ${tenant.id}\nDatabase: ${tenant.databaseName || 'N/A'}`
        });
    }

    confirmDelete(tenant: Tenant) {
        this.confirmationService.confirm({
            message: `Are you sure you want to delete tenant "${tenant.name || tenant.id}"? This will permanently delete all tenant data.`,
            header: 'Confirm Deletion',
            icon: 'pi pi-exclamation-triangle',
            acceptButtonStyleClass: 'p-button-danger',
            accept: () => {
                this.deleteTenant(tenant);
            }
        });
    }

    deleteTenant(tenant: Tenant) {
        this.http.delete(`${environment.apiUrl}/platform/api/tenants/${tenant.id}`).subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: 'Deleted',
                    detail: `Tenant ${tenant.id} has been deleted`
                });
                this.loadTenants();
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Failed to delete tenant'
                });
            }
        });
    }
}
