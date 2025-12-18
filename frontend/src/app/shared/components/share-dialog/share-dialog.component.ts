import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { Select } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AclService, AclEntry, RoleBundle, GrantAccessRequest } from '../../../core/services/acl.service';

interface RoleBundleOption {
  label: string;
  value: string;
  description: string;
}

@Component({
  selector: 'app-share-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, Select,
    InputTextModule, TableModule, TagModule, TooltipModule,
    ToastModule, ConfirmDialogModule
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <div class="p-4">
      <p-toast></p-toast>
      <p-confirmDialog></p-confirmDialog>
      
      <!-- Resource Info -->
      <div class="mb-4 p-3 bg-gray-100 rounded-lg">
        <div class="flex items-center gap-2">
          <i class="pi" [ngClass]="getResourceIcon()"></i>
          <span class="font-semibold">{{ resourceName }}</span>
        </div>
        <p class="text-sm text-gray-600 mt-1">{{ resourceType }}</p>
      </div>

      <!-- Add User Section -->
      <div class="mb-6">
        <h4 class="text-lg font-semibold mb-3">Share with User</h4>
        <div class="flex gap-2">
          <input 
            type="text" 
            pInputText 
            [(ngModel)]="newUserId" 
            placeholder="Enter user ID or email"
            class="flex-1" />
          <p-select 
            [options]="roleBundleOptions" 
            [(ngModel)]="selectedRoleBundle"
            optionLabel="label"
            optionValue="value"
            placeholder="Select access level"
            [style]="{ width: '180px' }">
          </p-select>
          <p-button 
            icon="pi pi-plus" 
            label="Add" 
            (onClick)="grantAccess()"
            [disabled]="!newUserId || !selectedRoleBundle">
          </p-button>
        </div>
      </div>

      <!-- Current Access List -->
      <div>
        <h4 class="text-lg font-semibold mb-3">People with Access</h4>
        <p-table [value]="aclEntries" [loading]="loading" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>User/Group</th>
              <th>Access Level</th>
              <th>Granted By</th>
              <th>Actions</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-entry>
            <tr>
              <td>
                <div class="flex items-center gap-2">
                  <i class="pi" [ngClass]="entry.principalType === 'USER' ? 'pi-user' : 'pi-users'"></i>
                  <span>{{ entry.principalId }}</span>
                </div>
              </td>
              <td>
                <p-tag [value]="entry.roleBundle" [severity]="getRoleSeverity(entry.roleBundle)"></p-tag>
              </td>
              <td class="text-sm text-gray-600">{{ entry.grantedBy || '—' }}</td>
              <td>
                <p-button 
                  icon="pi pi-trash" 
                  [text]="true" 
                  severity="danger"
                  pTooltip="Remove access"
                  (onClick)="confirmRevoke(entry)">
                </p-button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="4" class="text-center p-4 text-gray-500">
                No one has access yet. Add users above.
              </td>
            </tr>
          </ng-template>
        </p-table>
      </div>

      <!-- Footer -->
      <div class="flex justify-end mt-4 pt-4 border-t">
        <p-button label="Done" icon="pi pi-check" (onClick)="close()"></p-button>
      </div>
    </div>
  `
})
export class ShareDialogComponent implements OnInit {
  resourceId = '';
  resourceType = '';
  resourceName = '';

  newUserId = '';
  selectedRoleBundle = '';

  aclEntries: AclEntry[] = [];
  roleBundleOptions: RoleBundleOption[] = [];
  loading = true;

  private aclService = inject(AclService);
  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);

  ngOnInit() {
    // Get resource info from dialog config
    this.resourceId = this.config.data?.resourceId || '';
    this.resourceType = this.config.data?.resourceType || 'FOLDER';
    this.resourceName = this.config.data?.resourceName || 'Resource';

    this.loadRoleBundles();
    this.loadPermissions();
  }

  loadRoleBundles() {
    this.aclService.getRoleBundles().subscribe({
      next: (bundles) => {
        this.roleBundleOptions = bundles.map(b => ({
          label: b.name,
          value: b.name,
          description: b.description
        }));
      },
      error: (err) => {
        console.error('Failed to load role bundles', err);
        // Use default options
        this.roleBundleOptions = [
          { label: 'VIEWER', value: 'VIEWER', description: 'Read-only access' },
          { label: 'CONTRIBUTOR', value: 'CONTRIBUTOR', description: 'Can add content' },
          { label: 'EDITOR', value: 'EDITOR', description: 'Can modify content' },
          { label: 'MANAGER', value: 'MANAGER', description: 'Full control' }
        ];
      }
    });
  }

  loadPermissions() {
    this.loading = true;
    this.aclService.getResourcePermissions(this.resourceId).subscribe({
      next: (entries) => {
        this.aclEntries = entries;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load permissions', err);
        this.loading = false;
      }
    });
  }

  grantAccess() {
    const request: GrantAccessRequest = {
      resourceId: this.resourceId,
      resourceType: this.resourceType,
      principalType: 'USER',
      principalId: this.newUserId,
      roleBundle: this.selectedRoleBundle
    };

    this.aclService.grantAccess(request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Access Granted',
          detail: `${this.newUserId} now has ${this.selectedRoleBundle} access`
        });
        this.newUserId = '';
        this.loadPermissions();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to grant access'
        });
      }
    });
  }

  confirmRevoke(entry: AclEntry) {
    this.confirmationService.confirm({
      message: `Remove ${entry.principalId}'s access to this ${this.resourceType.toLowerCase()}?`,
      header: 'Confirm Removal',
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.revokeAccess(entry)
    });
  }

  revokeAccess(entry: AclEntry) {
    this.aclService.revokeAccess(entry.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Access Revoked',
          detail: `${entry.principalId}'s access has been removed`
        });
        this.loadPermissions();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to revoke access'
        });
      }
    });
  }

  getResourceIcon(): string {
    switch (this.resourceType) {
      case 'PROJECT': return 'pi-folder-open';
      case 'FOLDER': return 'pi-folder';
      case 'FILE': return 'pi-file';
      default: return 'pi-box';
    }
  }

  getRoleSeverity(role: string): 'success' | 'info' | 'warn' | 'danger' {
    switch (role) {
      case 'VIEWER': return 'info';
      case 'CONTRIBUTOR': return 'success';
      case 'EDITOR': return 'warn';
      case 'MANAGER': return 'danger';
      default: return 'info';
    }
  }

  close() {
    this.ref.close();
  }
}
