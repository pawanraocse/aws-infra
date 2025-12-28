import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ButtonModule} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {DialogModule} from 'primeng/dialog';
import {InputTextModule} from 'primeng/inputtext';
import {InputNumberModule} from 'primeng/inputnumber';
import {ToastModule} from 'primeng/toast';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {TagModule} from 'primeng/tag';
import {TooltipModule} from 'primeng/tooltip';
import {CardModule} from 'primeng/card';
import {ConfirmationService, MessageService} from 'primeng/api';
import {ApiKey, ApiKeyService, CreateApiKeyResponse} from '../../core/services/api-key.service';

@Component({
    selector: 'app-api-keys',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ButtonModule,
        TableModule,
        DialogModule,
        InputTextModule,
        InputNumberModule,
        ToastModule,
        ConfirmDialogModule,
        TagModule,
        TooltipModule,
        CardModule
    ],
    providers: [MessageService, ConfirmationService],
    template: `
    <p-toast></p-toast>
    <p-confirmDialog></p-confirmDialog>

    <div class="api-keys-container">
      <!-- Header -->
      <div class="page-header">
        <div>
          <h1 class="text-3xl font-bold mb-2">API Keys</h1>
          <p class="text-600">Manage programmatic access to your workspace</p>
        </div>
        <p-button
          label="Create API Key"
          icon="pi pi-plus"
          (click)="showCreateDialog = true"
          [disabled]="loading()">
        </p-button>
      </div>

      <!-- Info Banner -->
      <div class="info-banner">
        <i class="pi pi-info-circle"></i>
        <span>API keys inherit your current permissions. Keep them secure — they provide full access to your workspace.</span>
      </div>

      <!-- Loading State -->
      @if (loading()) {
        <div class="loading-container">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
          <p>Loading API keys...</p>
        </div>
      }

      <!-- Empty State -->
      @if (!loading() && apiKeys().length === 0) {
        <div class="empty-state">
          <i class="pi pi-key" style="font-size: 4rem; color: var(--primary-color);"></i>
          <h3>No API Keys</h3>
          <p>Create your first API key to start integrating with our API.</p>
          <p-button
            label="Create Your First Key"
            icon="pi pi-plus"
            (click)="showCreateDialog = true">
          </p-button>
        </div>
      }

      <!-- Keys Table -->
      @if (!loading() && apiKeys().length > 0) {
        <p-table [value]="apiKeys()" [tableStyle]="{'min-width': '50rem'}" styleClass="p-datatable-striped">
          <ng-template pTemplate="header">
            <tr>
              <th>Name</th>
              <th>Key Prefix</th>
              <th>Status</th>
              <th>Created</th>
              <th>Expires</th>
              <th>Last Used</th>
              <th>Usage</th>
              <th style="text-align: center">Actions</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-key>
            <tr>
              <td class="font-semibold">{{ key.name }}</td>
              <td>
                <code class="key-prefix">{{ key.keyPrefix }}...</code>
              </td>
              <td>
                <p-tag
                  [value]="key.status"
                  [severity]="getStatusSeverity(key.status)">
                </p-tag>
              </td>
              <td>{{ key.createdAt | date:'medium' }}</td>
              <td>
                <span [class.text-red-500]="isExpiringSoon(key.expiresAt)">
                  {{ key.expiresAt | date:'medium' }}
                </span>
              </td>
              <td>{{ key.lastUsedAt ? (key.lastUsedAt | date:'medium') : 'Never' }}</td>
              <td>{{ key.usageCount | number }}</td>
              <td style="text-align: center">
                @if (key.status === 'ACTIVE') {
                  <p-button
                    icon="pi pi-trash"
                    severity="danger"
                    [text]="true"
                    pTooltip="Revoke Key"
                    (click)="confirmRevoke(key)">
                  </p-button>
                }
              </td>
            </tr>
          </ng-template>
        </p-table>
      }
    </div>

    <!-- Create Dialog -->
    <p-dialog
      header="Create API Key"
      [(visible)]="showCreateDialog"
      [modal]="true"
      [style]="{width: '500px'}"
      [draggable]="false"
      [closable]="!creating()">

      @if (!newKeyResponse()) {
        <div class="create-form">
          <div class="field">
            <label for="keyName" class="font-semibold">Key Name</label>
            <input
              id="keyName"
              type="text"
              pInputText
              [(ngModel)]="newKeyName"
              placeholder="e.g., CI/CD Integration"
              class="w-full"
              [disabled]="creating()">
            <small class="text-500">A friendly name to identify this key</small>
          </div>

          <div class="field">
            <label for="expiry" class="font-semibold">Expires In (Days)</label>
            <p-inputNumber
              id="expiry"
              [(ngModel)]="expiresInDays"
              [min]="1"
              [max]="730"
              [showButtons]="true"
              class="w-full"
              [disabled]="creating()">
            </p-inputNumber>
            <small class="text-500">Maximum: 730 days (2 years)</small>
          </div>

          <div class="dialog-footer">
            <p-button
              label="Cancel"
              [text]="true"
              (click)="showCreateDialog = false"
              [disabled]="creating()">
            </p-button>
            <p-button
              label="Create Key"
              icon="pi pi-key"
              (click)="createApiKey()"
              [loading]="creating()"
              [disabled]="!newKeyName.trim()">
            </p-button>
          </div>
        </div>
      } @else {
        <div class="key-created">
          <div class="success-icon">
            <i class="pi pi-check-circle"></i>
          </div>
          <h3>API Key Created!</h3>

          <div class="key-display">
            <div class="key-warning">
              <i class="pi pi-exclamation-triangle"></i>
              <span>Copy this key now. You won't be able to see it again!</span>
            </div>
            <div class="key-value">
              <code>{{ newKeyResponse()!.key }}</code>
              <p-button
                icon="pi pi-copy"
                [text]="true"
                pTooltip="Copy to clipboard"
                (click)="copyKey(newKeyResponse()!.key)">
              </p-button>
            </div>
          </div>

          <div class="key-details">
            <p><strong>Name:</strong> {{ newKeyResponse()!.name }}</p>
            <p><strong>Expires:</strong> {{ newKeyResponse()!.expiresAt | date:'medium' }}</p>
          </div>

          <div class="dialog-footer">
            <p-button
              label="Done"
              (click)="closeCreateDialog()">
            </p-button>
          </div>
        </div>
      }
    </p-dialog>
  `,
    styles: [`
    .api-keys-container {
      padding: 2rem;
      max-width: 1200px;
      margin: 0 auto;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
    }

    .info-banner {
      background: var(--blue-50);
      border: 1px solid var(--blue-200);
      border-radius: 8px;
      padding: 1rem;
      margin-bottom: 1.5rem;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--blue-700);
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 4rem;
      gap: 1rem;
      color: var(--text-color-secondary);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 4rem;
      background: var(--surface-card);
      border-radius: 12px;
      gap: 1rem;
      text-align: center;
    }

    .empty-state h3 {
      margin: 0;
      color: var(--text-color);
    }

    .empty-state p {
      color: var(--text-color-secondary);
      margin: 0;
    }

    .key-prefix {
      background: var(--surface-100);
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-family: monospace;
      font-size: 0.875rem;
    }

    .create-form {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .dialog-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1rem;
      padding-top: 1rem;
      border-top: 1px solid var(--surface-border);
    }

    .key-created {
      text-align: center;
    }

    .success-icon {
      font-size: 4rem;
      color: var(--green-500);
      margin-bottom: 1rem;
    }

    .key-display {
      margin: 1.5rem 0;
    }

    .key-warning {
      background: var(--yellow-50);
      border: 1px solid var(--yellow-200);
      border-radius: 8px;
      padding: 0.75rem;
      margin-bottom: 1rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: var(--yellow-700);
      font-size: 0.875rem;
    }

    .key-value {
      background: var(--surface-100);
      border-radius: 8px;
      padding: 1rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }

    .key-value code {
      font-family: monospace;
      font-size: 0.85rem;
      word-break: break-all;
      flex: 1;
    }

    .key-details {
      text-align: left;
      background: var(--surface-50);
      padding: 1rem;
      border-radius: 8px;
      margin-top: 1rem;
    }

    .key-details p {
      margin: 0.5rem 0;
    }
  `]
})
export class ApiKeysComponent implements OnInit {
    private apiKeyService = inject(ApiKeyService);
    private messageService = inject(MessageService);
    private confirmationService = inject(ConfirmationService);

    apiKeys = signal<ApiKey[]>([]);
    loading = signal(true);
    creating = signal(false);
    newKeyResponse = signal<CreateApiKeyResponse | null>(null);

    showCreateDialog = false;
    newKeyName = '';
    expiresInDays = 365;

    ngOnInit() {
        this.loadApiKeys();
    }

    loadApiKeys() {
        this.loading.set(true);
        this.apiKeyService.listApiKeys().subscribe({
            next: (keys) => {
                this.apiKeys.set(keys);
                this.loading.set(false);
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Failed to load API keys'
                });
                this.loading.set(false);
            }
        });
    }

    createApiKey() {
        if (!this.newKeyName.trim()) return;

        this.creating.set(true);
        this.apiKeyService.createApiKey(this.newKeyName, this.expiresInDays).subscribe({
            next: (response) => {
                this.newKeyResponse.set(response);
                this.creating.set(false);
                this.loadApiKeys();
            },
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: err.error?.message || 'Failed to create API key'
                });
                this.creating.set(false);
            }
        });
    }

    closeCreateDialog() {
        this.showCreateDialog = false;
        this.newKeyResponse.set(null);
        this.newKeyName = '';
        this.expiresInDays = 365;
    }

    confirmRevoke(key: ApiKey) {
        this.confirmationService.confirm({
            message: `Are you sure you want to revoke "${key.name}"? This action cannot be undone.`,
            header: 'Revoke API Key',
            icon: 'pi pi-exclamation-triangle',
            accept: () => this.revokeKey(key)
        });
    }

    revokeKey(key: ApiKey) {
        this.apiKeyService.revokeApiKey(key.id).subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: 'Success',
                    detail: 'API key revoked successfully'
                });
                this.loadApiKeys();
            },
            error: () => {
                this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: 'Failed to revoke API key'
                });
            }
        });
    }

    copyKey(key: string) {
        navigator.clipboard.writeText(key);
        this.messageService.add({
            severity: 'success',
            summary: 'Copied!',
            detail: 'API key copied to clipboard',
            life: 2000
        });
    }

    getStatusSeverity(status: string): 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast' {
        switch (status) {
            case 'ACTIVE': return 'success';
            case 'EXPIRED': return 'warn';
            case 'REVOKED': return 'danger';
            default: return 'info';
        }
    }

    isExpiringSoon(expiresAt: string): boolean {
        const expiry = new Date(expiresAt);
        const now = new Date();
        const daysUntilExpiry = (expiry.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);
        return daysUntilExpiry <= 30;
    }
}
