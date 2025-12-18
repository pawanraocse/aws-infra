import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { GroupMappingService, GroupRoleMapping, CreateMappingRequest } from '../../../core/services/group-mapping.service';

interface Role {
  id: string;
  name: string;
}

@Component({
  selector: 'app-group-mapping',
  standalone: true,
  imports: [
    CommonModule, FormsModule, CardModule, ButtonModule, TableModule,
    TagModule, DialogModule, InputTextModule, InputNumberModule, MessageModule
  ],
  template: `
    <div class="surface-ground p-4">
      <div class="flex justify-content-between align-items-center mb-4">
        <div>
          <h2 class="text-2xl font-bold text-900 m-0">Group-Role Mappings</h2>
          <p class="text-600 mt-1 mb-0">Configure how IdP groups map to application roles</p>
        </div>
        <p-button 
          label="Add Mapping" 
          icon="pi pi-plus" 
          (onClick)="showAddDialog()"
          [disabled]="loading()">
        </p-button>
      </div>

      <p-message 
        severity="info" 
        styleClass="mb-4 w-full"
        text="When users login via SSO, their IdP groups are matched against these mappings to automatically assign roles.">
      </p-message>

      <p-card styleClass="shadow-2">
        <ng-template pTemplate="header">
          <div class="p-3 border-bottom-1 surface-border flex align-items-center justify-content-between">
            <span class="font-semibold text-lg">
              <i class="pi pi-sitemap mr-2"></i>Active Mappings
            </span>
            <p-tag [value]="mappings().length + ' mappings'" severity="info"></p-tag>
          </div>
        </ng-template>

        <p-table 
          [value]="mappings()" 
          [loading]="loading()"
          styleClass="p-datatable-striped"
          [paginator]="mappings().length > 10"
          [rows]="10">
          <ng-template pTemplate="header">
            <tr>
              <th>IdP Group</th>
              <th>Maps To Role</th>
              <th>Priority</th>
              <th>Auto-Assign</th>
              <th style="width: 120px">Actions</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-mapping>
            <tr>
              <td>
                <div class="flex flex-column">
                  <span class="font-medium">{{ mapping.groupName }}</span>
                  <small class="text-500">{{ mapping.externalGroupId }}</small>
                </div>
              </td>
              <td>
                <p-tag [value]="mapping.roleName" severity="success"></p-tag>
              </td>
              <td>
                <span class="font-mono">{{ mapping.priority }}</span>
              </td>
              <td>
                <i [class]="mapping.autoAssign ? 'pi pi-check-circle text-green-500' : 'pi pi-times-circle text-red-500'"></i>
              </td>
              <td>
                <p-button 
                  icon="pi pi-pencil" 
                  [rounded]="true" 
                  [text]="true"
                  severity="secondary"
                  (onClick)="editMapping(mapping)">
                </p-button>
                <p-button 
                  icon="pi pi-trash" 
                  [rounded]="true" 
                  [text]="true"
                  severity="danger"
                  (onClick)="deleteMapping(mapping)">
                </p-button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="5" class="text-center p-4">
                <i class="pi pi-inbox text-4xl text-300 mb-3"></i>
                <p class="text-600">No group mappings configured yet.</p>
                <p-button label="Add First Mapping" icon="pi pi-plus" (onClick)="showAddDialog()"></p-button>
              </td>
            </tr>
          </ng-template>
        </p-table>
      </p-card>
    </div>

    <!-- Add/Edit Dialog -->
    <p-dialog 
      [(visible)]="dialogVisible" 
      [modal]="true" 
      [style]="{width: '450px'}"
      [header]="editMode ? 'Edit Mapping' : 'Add Group Mapping'">
      <div class="flex flex-column gap-3">
        @if (!editMode) {
          <div class="field">
            <label class="font-medium block mb-2">External Group ID *</label>
            <input pInputText class="w-full" [(ngModel)]="formData.externalGroupId" 
                   placeholder="cn=Engineering,ou=Groups,dc=company" />
          </div>
          <div class="field">
            <label class="font-medium block mb-2">Group Name *</label>
            <input pInputText class="w-full" [(ngModel)]="formData.groupName" 
                   placeholder="Engineering" />
          </div>
        }
        <div class="field">
          <label class="font-medium block mb-2">Role *</label>
          <select class="w-full p-inputtext" [(ngModel)]="formData.roleId">
            <option value="">Select a role...</option>
            @for (role of availableRoles; track role.id) {
              <option [value]="role.id">{{ role.name }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label class="font-medium block mb-2">Priority</label>
          <p-inputNumber [(ngModel)]="formData.priority" [min]="0" [max]="100" styleClass="w-full"></p-inputNumber>
          <small class="text-500">Higher priority wins when user is in multiple groups</small>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancel" [text]="true" (onClick)="dialogVisible = false"></p-button>
        <p-button [label]="editMode ? 'Update' : 'Create'" icon="pi pi-check" (onClick)="saveMapping()"></p-button>
      </ng-template>
    </p-dialog>
  `
})
export class GroupMappingComponent implements OnInit {
  private groupMappingService = inject(GroupMappingService);

  mappings = signal<GroupRoleMapping[]>([]);
  loading = signal(false);
  dialogVisible = false;
  editMode = false;
  selectedMapping: GroupRoleMapping | null = null;
  errorMessage = '';

  formData: CreateMappingRequest & { priority: number } = {
    externalGroupId: '',
    groupName: '',
    roleId: '',
    priority: 0
  };

  availableRoles: Role[] = [
    { id: 'admin', name: 'Admin' },
    { id: 'editor', name: 'Editor' },
    { id: 'viewer', name: 'Viewer' }
  ];

  ngOnInit() {
    this.loadMappings();
  }

  loadMappings() {
    this.loading.set(true);
    this.groupMappingService.getMappings().subscribe({
      next: (data) => {
        this.mappings.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  showAddDialog() {
    this.editMode = false;
    this.formData = { externalGroupId: '', groupName: '', roleId: '', priority: 0 };
    this.dialogVisible = true;
  }

  editMapping(mapping: GroupRoleMapping) {
    this.editMode = true;
    this.selectedMapping = mapping;
    this.formData = {
      externalGroupId: mapping.externalGroupId,
      groupName: mapping.groupName,
      roleId: mapping.roleId,
      priority: mapping.priority
    };
    this.dialogVisible = true;
  }

  saveMapping() {
    if (this.editMode && this.selectedMapping) {
      this.groupMappingService.updateMapping(this.selectedMapping.id, {
        roleId: this.formData.roleId,
        priority: this.formData.priority
      }).subscribe({
        next: () => {
          this.dialogVisible = false;
          this.loadMappings();
        }
      });
    } else {
      this.groupMappingService.createMapping(this.formData).subscribe({
        next: () => {
          this.dialogVisible = false;
          this.loadMappings();
        }
      });
    }
  }

  deleteMapping(mapping: GroupRoleMapping) {
    if (confirm(`Delete mapping for "${mapping.groupName}"?`)) {
      this.groupMappingService.deleteMapping(mapping.id).subscribe({
        next: () => this.loadMappings()
      });
    }
  }
}
