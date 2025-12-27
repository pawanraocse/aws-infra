import {ChangeDetectorRef, Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {TagModule} from 'primeng/tag';
import {DialogModule} from 'primeng/dialog';
import {Select} from 'primeng/select';
import {InputTextModule} from 'primeng/inputtext';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Permission, Role, RoleService} from '../../../core/services/role.service';
import {UserRoleService} from '../../../core/services/user-role.service';
import {PermissionViewerComponent} from './permission-viewer.component';
import {MessageService} from 'primeng/api';
import {ToastModule} from 'primeng/toast';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, TagModule,
    ToastModule, DialogModule, Select, InputTextModule
  ],
  providers: [DialogService, MessageService],
  template: `
    <div class="card">
      <p-toast></p-toast>

      <!-- Header with Actions -->
      <div class="flex justify-content-between align-items-center mb-4">
        <h2 class="text-2xl font-bold m-0">Role Management</h2>
        <div class="flex gap-2">
          <p-button
            label="Assign Role to User"
            icon="pi pi-user-plus"
            severity="secondary"
            (onClick)="showAssignDialog = true">
          </p-button>
          <p-button
            label="Create Role"
            icon="pi pi-plus"
            (onClick)="showCreateDialog = true">
          </p-button>
        </div>
      </div>

      <!-- Roles Table -->
      <p-table [value]="roles" [tableStyle]="{ 'min-width': '60rem' }">
        <ng-template pTemplate="header">
          <tr>
            <th>Name</th>
            <th>Access Level</th>
            <th>Scope</th>
            <th>Description</th>
            <th style="width: 200px">Actions</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-role>
          <tr>
            <td class="font-bold">{{ role.name }}</td>
            <td>
              <p-tag [value]="getAccessLevelLabel(role)" [severity]="getAccessLevelSeverity(role)"></p-tag>
            </td>
            <td>
              <p-tag [value]="role.scope" [severity]="getSeverity(role.scope)"></p-tag>
            </td>
            <td>{{ role.description }}</td>
            <td>
              <div class="flex gap-1">
                <p-button
                  icon="pi pi-eye"
                  [text]="true"
                  pTooltip="View Permissions"
                  (onClick)="viewPermissions(role)">
                </p-button>
                <p-button
                  icon="pi pi-users"
                  [text]="true"
                  pTooltip="Assign to User"
                  (onClick)="openAssignDialog(role)">
                </p-button>
                <p-button
                  *ngIf="isCustomRole(role)"
                  icon="pi pi-pencil"
                  [text]="true"
                  pTooltip="Edit Role"
                  (onClick)="openEditDialog(role)">
                </p-button>
              </div>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr>
            <td colspan="5" class="text-center p-4">No roles found.</td>
          </tr>
        </ng-template>
      </p-table>
    </div>


    <!-- Create Role Dialog -->
    <p-dialog
      header="Create New Role"
      [(visible)]="showCreateDialog"
      [modal]="true"
      [style]="{ width: '500px' }"
      [closable]="true">
      <div class="flex flex-column gap-3">
        <div>
          <label for="roleName" class="block text-sm font-medium mb-1">Role Name</label>
          <input id="roleName" type="text" pInputText [(ngModel)]="newRole.name" class="w-full" placeholder="e.g., project-lead" />
        </div>
        <div>
          <label for="roleDesc" class="block text-sm font-medium mb-1">Description</label>
          <input id="roleDesc" type="text" pInputText [(ngModel)]="newRole.description" class="w-full" placeholder="Role description" />
        </div>
        <div>
          <label for="accessLevel" class="block text-sm font-medium mb-1">Access Level</label>
          <p-select
            id="accessLevel"
            [options]="accessLevels"
            [(ngModel)]="newRole.accessLevel"
            optionLabel="label"
            optionValue="value"
            placeholder="Select access level"
            appendTo="body"
            [style]="{ width: '100%' }">
          </p-select>
          <small class="text-gray-500 mt-1 block">{{ getAccessLevelDescription(newRole.accessLevel) }}</small>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" [text]="true" (onClick)="showCreateDialog = false"></p-button>
        <p-button label="Create" icon="pi pi-check" (onClick)="createRole()" [disabled]="!newRole.name || !newRole.accessLevel"></p-button>
      </ng-template>
    </p-dialog>

    <!-- Assign Role Dialog -->
    <p-dialog
      header="Assign Role to User"
      [(visible)]="showAssignDialog"
      [modal]="true"
      [style]="{ width: '450px' }"
      [closable]="true">
      <div class="flex flex-column gap-3">
        <div>
          <label for="userId" class="block text-sm font-medium mb-1">User ID or Email</label>
          <input id="userId" type="text" pInputText [(ngModel)]="assignData.userId" class="w-full" placeholder="Enter user ID or email" />
        </div>
        <div>
          <label for="assignRole" class="block text-sm font-medium mb-1">Role</label>
          <p-select
            id="assignRole"
            [options]="roles"
            [(ngModel)]="assignData.roleId"
            optionLabel="name"
            optionValue="id"
            placeholder="Select role"
            appendTo="body"
            [style]="{ width: '100%' }">
          </p-select>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" [text]="true" (onClick)="showAssignDialog = false"></p-button>
        <p-button label="Assign" icon="pi pi-check" (onClick)="assignRole()"></p-button>
      </ng-template>
    </p-dialog>
  `
})
export class RoleListComponent implements OnInit {
  roles: Role[] = [];
  ref: DynamicDialogRef | undefined;

  showCreateDialog = false;
  showAssignDialog = false;

  newRole: { name: string; description: string; accessLevel: string } = { name: '', description: '', accessLevel: '' };
  assignData = { userId: '', roleId: '' };

  // Access levels for simplified role-based model
  accessLevels = [
    { label: 'Admin - Full access', value: 'admin' },
    { label: 'Editor - Read & write', value: 'editor' },
    { label: 'Viewer - Read only', value: 'viewer' }
  ];

  getAccessLevelDescription(level: string): string {
    switch (level) {
      case 'admin': return 'Full access: manage users, settings, and all content';
      case 'editor': return 'Read & write: create, edit, and delete content';
      case 'viewer': return 'Read only: view content but cannot modify';
      default: return 'Select an access level to define role capabilities';
    }
  }

  permissions: Permission[] = [];
  selectedPermissions: { [key: string]: boolean } = {};

  private roleService = inject(RoleService);
  private userRoleService = inject(UserRoleService);
  private dialogService = inject(DialogService);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);

  ngOnInit() {
    this.loadRoles();
    // Granular permissions removed - using simplified role-based access
  }

  loadPermissions() {
    // Granular permissions deprecated - using simplified role-based access
    // Roles now have implicit access levels: admin (full), editor (read/write), viewer (read-only)
    this.permissions = [];
  }

  loadRoles() {
    this.roleService.getRoles().subscribe({
      next: (data) => {
        this.roles = data;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Failed to load roles', err)
    });
  }

  viewPermissions(role: Role) {
    this.dialogService.open(PermissionViewerComponent, {
      header: `Access Levels for ${role.name}`,
      width: '60%',
      contentStyle: { overflow: 'visible' },
      baseZIndex: 10000,
      closable: true,
      closeOnEscape: true,
      dismissableMask: true,
      data: { roleId: role.id }
    });
  }

  openAssignDialog(role: Role) {
    this.assignData.roleId = role.id;
    this.showAssignDialog = true;
  }

  createRole() {
    const payload = {
      ...this.newRole,
      scope: 'TENANT'
    };

    this.roleService.createRole(payload).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Role created successfully' });
        this.showCreateDialog = false;
        this.newRole = { name: '', description: '', accessLevel: '' };
        this.loadRoles();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to create role' });
      }
    });
  }

  assignRole() {
    this.userRoleService.assignRole(this.assignData.userId, this.assignData.roleId).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Role assigned to user' });
        this.showAssignDialog = false;
        this.assignData = { userId: '', roleId: '' };
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Failed to assign role' });
      }
    });
  }

  getSeverity(scope: string): 'success' | 'info' | 'warn' | 'danger' | undefined {
    switch (scope) {
      case 'PLATFORM': return 'danger';
      case 'TENANT': return 'info';
      default: return undefined;
    }
  }

  // Default role IDs that cannot be edited
  private defaultRoles = ['admin', 'editor', 'viewer', 'guest', 'super-admin'];

  /**
   * Check if a role is a custom role (can be edited)
   */
  isCustomRole(role: Role): boolean {
    return !this.defaultRoles.includes(role.id);
  }

  /**
   * Get the access level label for display
   * - Default roles: Use their ID as the access level
   * - Custom roles: Use stored accessLevel or show as "Custom"
   */
  getAccessLevelLabel(role: Role): string {
    if (!this.isCustomRole(role)) {
      // Default roles - use role ID as access level
      return role.id.toUpperCase();
    }
    // Custom roles - use stored accessLevel or "Custom"
    if (role.accessLevel) {
      return role.accessLevel.toUpperCase();
    }
    return 'CUSTOM';
  }

  /**
   * Get the severity/color for access level tag
   */
  getAccessLevelSeverity(role: Role): 'success' | 'info' | 'warn' | 'danger' {
    const level = role.accessLevel || role.id;
    switch (level.toLowerCase()) {
      case 'admin':
      case 'super-admin':
        return 'danger';
      case 'editor':
        return 'warn';
      case 'viewer':
        return 'info';
      case 'guest':
        return 'success';
      default:
        return 'info';
    }
  }

  /**
   * Open edit dialog for custom roles
   */
  openEditDialog(role: Role) {
    // TODO: Implement edit role dialog
    this.messageService.add({
      severity: 'info',
      summary: 'Coming Soon',
      detail: 'Edit role functionality will be available in a future update'
    });
  }
}

