import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { RoleService } from '../../../core/services/role.service';

interface RoleBundle {
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
      <!-- Access Level Bundles -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div *ngFor="let bundle of roleBundles" 
             class="border border-gray-200 rounded-lg p-3 hover:shadow-md transition-shadow bg-white">
          <div class="flex align-items-center gap-2 mb-2">
            <p-tag [value]="bundle.name" [severity]="bundle.color"></p-tag>
          </div>
          <p class="text-sm text-gray-600 mb-2 mt-0">{{ bundle.description }}</p>
          <ul class="text-xs text-gray-500 list-disc pl-4 m-0">
            <li *ngFor="let cap of bundle.capabilities" class="mb-1">{{ cap }}</li>
          </ul>
        </div>
      </div>

      <!-- Footer with Close Button -->
      <div class="flex justify-content-end pt-4 mt-3 border-top-1 border-gray-200">
        <p-button label="Close" icon="pi pi-times" (onClick)="close()" severity="secondary"></p-button>
      </div>
    </div>
  `
})
export class PermissionViewerComponent implements OnInit {
  loading = true;

  roleBundles: RoleBundle[] = [
    {
      name: 'VIEWER',
      description: 'Read-only access to files and folders',
      capabilities: ['View files', 'Download files', 'View metadata'],
      color: 'info'
    },
    {
      name: 'CONTRIBUTOR',
      description: 'Can add new content but not modify existing',
      capabilities: ['All Viewer capabilities', 'Upload new files', 'Create folders'],
      color: 'success'
    },
    {
      name: 'EDITOR',
      description: 'Can modify and organize content',
      capabilities: ['All Contributor capabilities', 'Edit files', 'Move/rename files', 'Delete own uploads'],
      color: 'warn'
    },
    {
      name: 'MANAGER',
      description: 'Full control including sharing permissions',
      capabilities: ['All Editor capabilities', 'Delete any files', 'Share with others', 'Manage access'],
      color: 'danger'
    }
  ];

  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);

  ngOnInit() {
    this.loading = false;
  }

  close() {
    this.ref.close();
  }
}

