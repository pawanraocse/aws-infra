import {Component, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CardModule} from 'primeng/card';
import {InputTextModule} from 'primeng/inputtext';
import {ButtonModule} from 'primeng/button';
import {PermissionManagerComponent} from '../../../shared/components/permission-manager.component';

/**
 * Demo page for the Permission Manager component.
 * Allows testing OpenFGA resource-level permissions.
 */
@Component({
  selector: 'app-permissions-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardModule,
    InputTextModule,
    ButtonModule,
    PermissionManagerComponent
  ],
  template: `
    <div class="p-4">
      <h2 class="text-2xl font-bold mb-4">
        <i class="pi pi-shield mr-2"></i>
        Resource Permissions (OpenFGA)
      </h2>

      <p class="text-color-secondary mb-4">
        Manage fine-grained access control for resources. Grant or revoke access at the resource level.
      </p>

      <!-- Resource Selector -->
      <p-card styleClass="mb-4">
        <div class="flex flex-column md:flex-row gap-3 align-items-end">
          <div class="field flex-1 mb-0">
            <label for="resourceType" class="block mb-2 font-medium">Resource Type</label>
            <input
              id="resourceType"
              type="text"
              pInputText
              [(ngModel)]="resourceType"
              placeholder="e.g., folder, document, project"
              class="w-full"
            />
          </div>
          <div class="field flex-1 mb-0">
            <label for="resourceId" class="block mb-2 font-medium">Resource ID</label>
            <input
              id="resourceId"
              type="text"
              pInputText
              [(ngModel)]="resourceId"
              placeholder="e.g., folder-123"
              class="w-full"
            />
          </div>
          <div class="mb-0">
            <p-button
              label="Load Permissions"
              icon="pi pi-refresh"
              (click)="loadResource()"
              [disabled]="!resourceType || !resourceId"
            />
          </div>
        </div>
      </p-card>

      <!-- Permission Manager -->
      @if (activeResource()) {
        <app-permission-manager
          [resourceType]="activeResource()!.type"
          [resourceId]="activeResource()!.id"
        />
      } @else {
        <p-card>
          <div class="text-center p-4 text-500">
            <i class="pi pi-info-circle mb-2" style="font-size: 2rem"></i>
            <p>Enter a resource type and ID above, then click "Load Permissions" to manage access.</p>
            <p class="text-sm mt-2">
              <strong>Example:</strong> Type: <code>folder</code>, ID: <code>my-folder-123</code>
            </p>
          </div>
        </p-card>
      }
    </div>
  `
})
export class PermissionsPageComponent {
  resourceType = 'folder';
  resourceId = 'demo-folder-001';

  // Use signal for proper change detection
  activeResource = signal<{ type: string; id: string } | null>(null);

  loadResource() {
    if (this.resourceType && this.resourceId) {
      // Set to null first to force component re-creation
      this.activeResource.set(null);
      // Use requestAnimationFrame to ensure DOM update
      requestAnimationFrame(() => {
        this.activeResource.set({ type: this.resourceType, id: this.resourceId });
      });
    }
  }
}

