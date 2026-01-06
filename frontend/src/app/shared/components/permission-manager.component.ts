import {Component, inject, Input, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {AutoCompleteCompleteEvent, AutoCompleteModule} from 'primeng/autocomplete';
import {ToastModule} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {AccessGrant, PermissionService} from '../../core/services/permission.service';
import {TenantUser, TenantUserService} from '../../core/services/tenant-user.service';

interface RelationOption {
  label: string;
  value: string;
}

/**
 * Permission Manager Component for managing fine-grained access control.
 *
 * Usage:
 * <app-permission-manager
 *   resourceType="document"
 *   resourceId="doc-123">
 * </app-permission-manager>
 */
@Component({
  selector: 'app-permission-manager',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DialogModule,
    ButtonModule,
    TableModule,
    InputTextModule,
    SelectModule,
    AutoCompleteModule,
    ToastModule
  ],
  providers: [MessageService],
  template: `
    <div class="permission-manager">
      <!-- Access List Table -->
      <div class="card">
        <div class="flex justify-content-between align-items-center mb-3">
          <h4 class="m-0">
            <i class="pi pi-users mr-2"></i>
            Access Permissions
          </h4>
          <p-button
            label="Share Access"
            icon="pi pi-share-alt"
            severity="success"
            (click)="openShareDialog()"
          />
        </div>

        @if (loading()) {
          <div class="flex justify-content-center p-4">
            <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
          </div>
        } @else if (grants().length === 0) {
          <div class="text-center p-4 text-500">
            <i class="pi pi-lock mb-2" style="font-size: 2rem"></i>
            <p>No access grants yet. Click "Share Access" to add users.</p>
          </div>
        } @else {
          <p-table [value]="grants()" styleClass="p-datatable-sm" [paginator]="grants().length > 10" [rows]="10">
            <ng-template pTemplate="header">
              <tr>
                <th>User</th>
                <th>Access Level</th>
                <th style="width: 100px">Actions</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-grant>
              <tr>
                <td>
                  <i class="pi pi-user mr-2"></i>
                  {{ grant.userId }}
                </td>
                <td>
                  <span class="badge" [ngClass]="getRelationClass(grant.relation)">
                    {{ grant.relation }}
                  </span>
                </td>
                <td>
                  <p-button
                    icon="pi pi-trash"
                    severity="danger"
                    [text]="true"
                    [rounded]="true"
                    (click)="revokeAccess(grant)"
                    pTooltip="Revoke access"
                  />
                </td>
              </tr>
            </ng-template>
          </p-table>
        }
      </div>

      <!-- Share Dialog -->
      <p-dialog
        [(visible)]="showShareDialog"
        header="Share Access"
        [modal]="true"
        [style]="{width: '400px'}"
        [draggable]="false"
      >
        <div class="flex flex-column gap-3">
          <div class="field">
            <label for="userId" class="font-medium">Select User</label>
            <p-autoComplete
              id="userId"
              [(ngModel)]="selectedUser"
              [suggestions]="filteredUsers"
              (completeMethod)="searchUsers($event)"
              field="email"
              [dropdown]="true"
              [forceSelection]="false"
              placeholder="Search by name or email..."
              appendTo="body"
              [style]="{width: '100%'}"
              [inputStyle]="{width: '100%'}"
            >
              <ng-template let-user pTemplate="item">
                <div class="flex align-items-center gap-2">
                  <i class="pi pi-user"></i>
                  <div>
                    <div class="font-medium">{{ user.name || user.email }}</div>
                    <div class="text-sm text-500">{{ user.email }}</div>
                  </div>
                </div>
              </ng-template>
            </p-autoComplete>
          </div>

          <div class="field">
            <label for="relation" class="font-medium">Access Level</label>
            <p-select
              id="relation"
              [options]="relationOptions"
              [(ngModel)]="shareRelation"
              optionLabel="label"
              optionValue="value"
              [style]="{width: '100%'}"
              placeholder="Select access level"
              appendTo="body"
            />
          </div>
        </div>

        <ng-template pTemplate="footer">
          <p-button label="Cancel" severity="secondary" [text]="true" (click)="showShareDialog = false" />
          <p-button
            label="Share"
            icon="pi pi-check"
            (click)="shareAccess()"
            [disabled]="!selectedUser || !shareRelation"
            [loading]="sharing()"
          />
        </ng-template>
      </p-dialog>

      <p-toast />
    </div>
  `,
  styles: [`
    .badge {
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.85rem;
      font-weight: 500;
    }
    .badge-owner {
      background-color: #FEF3C7;
      color: #92400E;
    }
    .badge-editor {
      background-color: #DBEAFE;
      color: #1E40AF;
    }
    .badge-viewer {
      background-color: #D1FAE5;
      color: #065F46;
    }
    .badge-default {
      background-color: #F3F4F6;
      color: #374151;
    }
  `]
})
export class PermissionManagerComponent implements OnInit {
  private permissionService = inject(PermissionService);
  private tenantUserService = inject(TenantUserService);
  private messageService = inject(MessageService);

  @Input({ required: true }) resourceType!: string;
  @Input({ required: true }) resourceId!: string;

  grants = signal<AccessGrant[]>([]);
  loading = signal(false);
  sharing = signal(false);
  showShareDialog = false;
  selectedUser: TenantUser | string | null = null;
  shareRelation = '';

  // User autocomplete
  allUsers: TenantUser[] = [];
  filteredUsers: TenantUser[] = [];

  relationOptions: RelationOption[] = [
    { label: 'Viewer (Read Only)', value: 'viewer' },
    { label: 'Editor (Read & Write)', value: 'editor' },
    { label: 'Owner (Full Access)', value: 'owner' }
  ];

  ngOnInit() {
    this.loadGrants();
    this.loadUsers();
  }

  loadUsers() {
    this.tenantUserService.getAllUsers().subscribe({
      next: (users) => {
        this.allUsers = users;
      },
      error: (err) => console.error('Failed to load users:', err)
    });
  }

  searchUsers(event: AutoCompleteCompleteEvent) {
    const query = event.query.toLowerCase();
    this.filteredUsers = this.allUsers.filter(user =>
      user.userId.toLowerCase().includes(query) ||
      user.email.toLowerCase().includes(query) ||
      (user.name && user.name.toLowerCase().includes(query))
    );
  }

  loadGrants() {
    this.loading.set(true);
    this.permissionService.listAccess(this.resourceType, this.resourceId).subscribe({
      next: (response) => {
        this.grants.set(response.grants);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load access grants:', err);
        this.loading.set(false);
        // Gracefully handle when OpenFGA is disabled
        if (err.status === 404) {
          this.grants.set([]);
        }
      }
    });
  }

  openShareDialog() {
    this.selectedUser = null;
    this.shareRelation = '';
    this.showShareDialog = true;
  }

  getSelectedUserId(): string {
    if (!this.selectedUser) return '';
    if (typeof this.selectedUser === 'string') return this.selectedUser;
    return this.selectedUser.userId || this.selectedUser.email;
  }

  shareAccess() {
    const userId = this.getSelectedUserId();
    if (!userId || !this.shareRelation) return;

    this.sharing.set(true);
    this.permissionService.shareAccess({
      targetUserId: userId,
      resourceType: this.resourceType,
      resourceId: this.resourceId,
      relation: this.shareRelation
    }).subscribe({
      next: (response) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Access Granted',
          detail: response.message
        });
        this.showShareDialog = false;
        this.sharing.set(false);
        this.loadGrants();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err.error?.message || 'Failed to share access'
        });
        this.sharing.set(false);
      }
    });
  }

  revokeAccess(grant: AccessGrant) {
    this.permissionService.revokeAccess({
      targetUserId: grant.userId,
      resourceType: this.resourceType,
      resourceId: this.resourceId,
      relation: grant.relation
    }).subscribe({
      next: (response) => {
        this.messageService.add({
          severity: 'success',
          summary: 'Access Revoked',
          detail: response.message
        });
        this.loadGrants();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err.error?.message || 'Failed to revoke access'
        });
      }
    });
  }

  getRelationClass(relation: string): string {
    switch (relation.toLowerCase()) {
      case 'owner': return 'badge-owner';
      case 'editor': return 'badge-editor';
      case 'viewer': return 'badge-viewer';
      default: return 'badge-default';
    }
  }
}
