import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { RoleService, Role } from '../../../core/services/role.service';
import { PermissionViewerComponent } from './permission-viewer.component';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';

@Component({
    selector: 'app-role-list',
    standalone: true,
    imports: [CommonModule, TableModule, ButtonModule, TagModule, ToastModule],
    providers: [DialogService, MessageService],
    template: `
    <div class="card">
      <p-toast></p-toast>
      <div class="flex justify-content-between align-items-center mb-4">
        <h2 class="text-2xl font-bold m-0">Role Management</h2>
      </div>

      <p-table [value]="roles" [tableStyle]="{ 'min-width': '50rem' }">
        <ng-template pTemplate="header">
          <tr>
            <th>Name</th>
            <th>Scope</th>
            <th>Description</th>
            <th>Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-role>
          <tr>
            <td class="font-bold">{{ role.name }}</td>
            <td>
              <p-tag [value]="role.scope" [severity]="getSeverity(role.scope)"></p-tag>
            </td>
            <td>{{ role.description }}</td>
            <td>
              <p-button 
                label="View Permissions" 
                icon="pi pi-eye" 
                [text]="true" 
                (onClick)="viewPermissions(role)">
              </p-button>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="4" class="text-center p-4">No roles found.</td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `
})
export class RoleListComponent implements OnInit {
    roles: Role[] = [];
    ref: DynamicDialogRef | undefined;

    private roleService = inject(RoleService);
    private dialogService = inject(DialogService);

    ngOnInit() {
        this.loadRoles();
    }

    loadRoles() {
        this.roleService.getRoles().subscribe({
            next: (data) => this.roles = data,
            error: (err) => console.error('Failed to load roles', err)
        });
    }

    viewPermissions(role: Role) {
        const ref = this.dialogService.open(PermissionViewerComponent, {
            header: `Permissions for ${role.name}`,
            width: '70%',
            contentStyle: { overflow: 'auto' },
            baseZIndex: 10000,
            data: {
                roleId: role.id
            }
        });
        this.ref = ref || undefined;
    }

    getSeverity(scope: string): 'success' | 'info' | 'warn' | 'danger' | undefined {
        switch (scope) {
            case 'PLATFORM': return 'danger';
            case 'TENANT': return 'info';
            default: return undefined;
        }
    }
}
