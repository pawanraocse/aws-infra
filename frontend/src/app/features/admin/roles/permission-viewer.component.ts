import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

interface RoleCapabilities {
  name: string;
  description: string;
  capabilities: string[];
  color: 'success' | 'info' | 'warn' | 'danger';
}

@Component({
  selector: 'app-permission-viewer',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule],
  template: `
    <div class="p-3">
      <!-- Role-Specific Access Level -->
      <div class="border border-gray-200 rounded-lg p-4 bg-white">
        <div class="flex align-items-center gap-2 mb-3">
          <p-tag [value]="roleCapabilities.name" [severity]="roleCapabilities.color"></p-tag>
        </div>
        <p class="text-base text-gray-700 mb-3 mt-0">{{ roleCapabilities.description }}</p>
        <h4 class="text-sm font-semibold text-gray-600 mb-2 mt-0">Capabilities:</h4>
        <ul class="text-sm text-gray-600 list-disc pl-4 m-0">
          <li *ngFor="let cap of roleCapabilities.capabilities" class="mb-2">{{ cap }}</li>
        </ul>
      </div>

      <!-- Footer with Close Button -->
      <div class="flex justify-content-end pt-4 mt-3 border-top-1 border-gray-200">
        <p-button label="Close" icon="pi pi-times" (onClick)="close()" severity="secondary"></p-button>
      </div>
    </div>
  `
})
export class PermissionViewerComponent implements OnInit {
  roleCapabilities: RoleCapabilities = {
    name: 'CUSTOM',
    description: 'Custom role with specific access',
    capabilities: ['Access defined by administrator'],
    color: 'info'
  };

  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);

  ngOnInit() {
    const roleId = this.config.data?.roleId || '';
    this.roleCapabilities = this.getCapabilitiesForRole(roleId);
  }

  getCapabilitiesForRole(roleId: string): RoleCapabilities {
    // Map role ID to its capabilities
    switch (roleId.toLowerCase()) {
      case 'super-admin':
        return {
          name: 'SUPER ADMIN',
          description: 'Platform-level administrator with full system access',
          capabilities: [
            'Manage all tenants (create, edit, delete)',
            'View platform-wide analytics',
            'Access system configuration',
            'Manage platform settings'
          ],
          color: 'danger'
        };
      case 'admin':
        return {
          name: 'ADMIN',
          description: 'Tenant administrator with full access within the organization',
          capabilities: [
            'Manage all users in organization',
            'Assign and revoke roles',
            'Create and manage custom roles',
            'Access all organization settings',
            'Manage invitations',
            'Full content access (create, edit, delete)'
          ],
          color: 'danger'
        };
      case 'editor':
        return {
          name: 'EDITOR',
          description: 'Can create, edit, and delete content within the organization',
          capabilities: [
            'Create new content and folders',
            'Edit existing content',
            'Delete content',
            'Share content with others',
            'View all organization content'
          ],
          color: 'warn'
        };
      case 'viewer':
        return {
          name: 'VIEWER',
          description: 'Read-only access to organization content',
          capabilities: [
            'View all organization content',
            'Download files',
            'View shared content',
            'Cannot modify or delete content'
          ],
          color: 'info'
        };
      case 'guest':
        return {
          name: 'GUEST',
          description: 'Limited access to shared content only',
          capabilities: [
            'View content explicitly shared with them',
            'Download shared files',
            'No access to organization-wide content'
          ],
          color: 'success'
        };
      default:
        // For custom roles, show generic message
        return {
          name: roleId.toUpperCase(),
          description: 'Custom role with specific access permissions',
          capabilities: [
            'Access level defined by administrator',
            'Contact your admin for specific permissions'
          ],
          color: 'info'
        };
    }
  }

  close() {
    this.ref.close();
  }
}
