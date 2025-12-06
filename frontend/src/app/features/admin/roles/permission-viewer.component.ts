import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { RoleService, Permission } from '../../../core/services/role.service';

@Component({
    selector: 'app-permission-viewer',
    standalone: true,
    imports: [CommonModule, TableModule],
    template: `
    <div class="card">
      <p-table [value]="permissions" [tableStyle]="{ 'min-width': '50rem' }" [loading]="loading">
        <ng-template pTemplate="header">
          <tr>
            <th>Resource</th>
            <th>Action</th>
            <th>Description</th>
            <th>Access</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-perm>
          <tr>
            <td class="font-bold">{{ perm.resource }}</td>
            <td>{{ perm.action }}</td>
            <td>{{ perm.description }}</td>
            <td>
              <i class="pi pi-check-circle text-green-500 text-xl" *ngIf="hasPermission(perm)"></i>
              <i class="pi pi-times-circle text-gray-300 text-xl" *ngIf="!hasPermission(perm)"></i>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `
})
export class PermissionViewerComponent implements OnInit {
    permissions: Permission[] = [];
    rolePermissions: string[] = []; // List of permission IDs for the role
    loading = true;

    private roleService = inject(RoleService);
    private config = inject(DynamicDialogConfig);

    ngOnInit() {
        // In a real app, we would fetch permissions specifically for this role
        // For now, we'll fetch ALL permissions and mock the selection logic
        // or we need an endpoint to get permissions for a specific role.
        // The backend PermissionService has 'getUserPermissions' but not 'getRolePermissions'.
        // We might need to add that or just show all permissions for now.

        // Let's fetch all permissions first
        this.roleService.getPermissions().subscribe({
            next: (data) => {
                this.permissions = data;
                this.loading = false;
                // TODO: Fetch actual permissions for the role
                // For MVP, we'll just show all available permissions
            },
            error: (err) => {
                console.error('Failed to load permissions', err);
                this.loading = false;
            }
        });
    }

    hasPermission(perm: Permission): boolean {
        // Placeholder logic: In real implementation, check if 'perm.id' is in 'rolePermissions'
        // For now, return true to show the UI
        return true;
    }
}
