import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {MessageModule} from 'primeng/message';
import {TagModule} from 'primeng/tag';
import {DividerModule} from 'primeng/divider';
import {SelectModule} from 'primeng/select';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {ToastModule} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {PasswordModule} from 'primeng/password';
import {
  OidcConfigRequest,
  SamlConfigRequest,
  SSO_PROVIDERS,
  SsoConfig,
  SsoConfigService
} from '../../../core/services/sso-config.service';

interface Provider {
  value: string;
  label: string;
  icon: string;
  protocol: string;
}

@Component({
  selector: 'app-sso-config',
  standalone: true,
  imports: [
    CommonModule, FormsModule, CardModule, ButtonModule,
    InputTextModule, MessageModule, TagModule, DividerModule,
    SelectModule, ToggleSwitchModule, ToastModule, PasswordModule
  ],
  providers: [MessageService],
  template: `
    <p-toast></p-toast>

    <div class="surface-ground p-4">
      <div class="flex justify-content-between align-items-center mb-4">
        <div>
          <h2 class="text-2xl font-bold text-900 m-0">SSO Configuration</h2>
          <p class="text-600 mt-1 mb-0">Configure Single Sign-On for your organization</p>
        </div>
        <div class="flex align-items-center gap-3">
          @if (config()) {
            <span class="text-sm text-600">SSO Status:</span>
            <p-tag
              [severity]="config()?.ssoEnabled ? 'success' : 'secondary'"
              [value]="config()?.ssoEnabled ? 'Enabled' : 'Disabled'"
              [icon]="config()?.ssoEnabled ? 'pi pi-check-circle' : 'pi pi-times-circle'">
            </p-tag>
          }
        </div>
      </div>

      @if (loading()) {
        <div class="flex justify-content-center align-items-center p-8">
          <i class="pi pi-spin pi-spinner text-4xl text-primary"></i>
        </div>
      } @else {
        <!-- Provider Selection -->
        <p-card styleClass="shadow-2 mb-4">
          <ng-template pTemplate="header">
            <div class="p-3 border-bottom-1 surface-border">
              <span class="font-semibold text-lg"><i class="pi pi-cog mr-2"></i>Identity Provider Setup</span>
            </div>
          </ng-template>

          <div class="flex flex-column gap-4">
            <div class="field">
              <label class="font-medium block mb-2">Select Identity Provider</label>
              <p-select
                [options]="providers"
                [(ngModel)]="selectedProvider"
                optionLabel="label"
                placeholder="Choose your SSO provider..."
                styleClass="w-full md:w-25rem"
                (onChange)="onProviderChange()">
                <ng-template pTemplate="item" let-item>
                  <div class="flex align-items-center gap-2">
                    <i [class]="item.icon"></i>
                    <span>{{ item.label }}</span>
                  </div>
                </ng-template>
              </p-select>
            </div>

            @if (selectedProvider) {
              <p-divider></p-divider>

              @if (isOidcProvider()) {
                <!-- OIDC Configuration Form (Azure AD OIDC) -->
                <div class="grid">
                  <div class="col-12">
                    <p-message severity="info"
                      text="Configure OpenID Connect for Microsoft Azure AD. Best for organizations with fewer than 200 groups.">
                    </p-message>
                  </div>

                  <div class="col-12 md:col-6 field">
                    <label class="font-medium block mb-2">Client ID *</label>
                    <input pInputText
                      class="w-full"
                      [(ngModel)]="oidcForm.clientId"
                      placeholder="Enter your Azure AD Application (Client) ID" />
                  </div>

                  <div class="col-12 md:col-6 field">
                    <label class="font-medium block mb-2">Client Secret *</label>
                    <p-password
                      [(ngModel)]="oidcForm.clientSecret"
                      [feedback]="false"
                      [toggleMask]="true"
                      styleClass="w-full"
                      inputStyleClass="w-full"
                      placeholder="Enter your Azure AD Client Secret">
                    </p-password>
                  </div>

                  <div class="col-12 field">
                    <label class="font-medium block mb-2">Issuer URL *</label>
                    <input pInputText
                      class="w-full"
                      [(ngModel)]="oidcForm.issuerUrl"
                      placeholder="https://login.microsoftonline.com/YOUR-TENANT-ID/v2.0" />
                    <small class="text-500">Replace YOUR-TENANT-ID with your Azure AD tenant ID</small>
                  </div>
                </div>
              } @else {
                <!-- SAML Configuration Form -->
                <div class="grid">
                  <div class="col-12">
                    <p-message severity="info"
                      [text]="'Configure SAML 2.0 for ' + selectedProvider.label">
                    </p-message>
                  </div>

                  <div class="col-12 field">
                    <label class="font-medium block mb-2">IdP Metadata URL *</label>
                    <input pInputText
                      class="w-full"
                      [(ngModel)]="samlForm.metadataUrl"
                      placeholder="https://your-idp.com/saml/metadata" />
                    <small class="text-500">The URL to your IdP's SAML metadata XML</small>
                  </div>

                  <div class="col-12 md:col-6 field">
                    <label class="font-medium block mb-2">Entity ID</label>
                    <input pInputText
                      class="w-full"
                      [(ngModel)]="samlForm.entityId"
                      placeholder="https://your-idp.com/entity-id" />
                  </div>

                  <div class="col-12 md:col-6 field">
                    <label class="font-medium block mb-2">Sign-In URL (Optional)</label>
                    <input pInputText
                      class="w-full"
                      [(ngModel)]="samlForm.ssoUrl"
                      placeholder="https://your-idp.com/sso/saml" />
                  </div>
                </div>
              }

              <!-- Action Buttons -->
              <div class="flex gap-2 mt-3">
                <p-button
                  label="Save Configuration"
                  icon="pi pi-save"
                  [loading]="saving()"
                  (onClick)="saveConfiguration()">
                </p-button>
                <p-button
                  label="Test Connection"
                  icon="pi pi-play"
                  severity="secondary"
                  [disabled]="!canTest()"
                  [loading]="testing()"
                  (onClick)="testConnection()">
                </p-button>
              </div>
            }
          </div>
        </p-card>

        <!-- Current Configuration Status -->
        @if (config()?.idpType) {
          <p-card styleClass="shadow-2 mb-4">
            <ng-template pTemplate="header">
              <div class="p-3 border-bottom-1 surface-border flex justify-content-between align-items-center">
                <span class="font-semibold text-lg"><i class="pi pi-check-circle mr-2 text-green-500"></i>Active Configuration</span>
                <p-toggleswitch
                  [(ngModel)]="ssoEnabled"
                  (onChange)="toggleSso()">
                </p-toggleswitch>
              </div>
            </ng-template>

            <div class="grid">
              <div class="col-12 md:col-4">
                <span class="text-500 text-sm">Provider Type</span>
                <p class="font-semibold mt-1">{{ config()?.idpType }}</p>
              </div>
              <div class="col-12 md:col-4">
                <span class="text-500 text-sm">Last Tested</span>
                <p class="font-semibold mt-1">{{ config()?.lastTestedAt || 'Never' }}</p>
              </div>
              <div class="col-12 md:col-4">
                <span class="text-500 text-sm">Test Status</span>
                <p class="mt-1">
                  <p-tag
                    [value]="config()?.testStatus || 'Not tested'"
                    [severity]="getTestStatusSeverity()">
                  </p-tag>
                </p>
              </div>
            </div>

            <p-divider></p-divider>

            <div class="flex gap-2">
              <p-button
                label="Test Connection"
                icon="pi pi-play"
                severity="info"
                [loading]="testing()"
                (onClick)="testConnection()">
              </p-button>
              <p-button
                label="Remove Configuration"
                icon="pi pi-trash"
                severity="danger"
                [text]="true"
                (onClick)="deleteConfiguration()">
              </p-button>
            </div>
          </p-card>
        }

        <!-- SP Metadata (for SAML) -->
        <p-card styleClass="shadow-2">
          <ng-template pTemplate="header">
            <div class="p-3 border-bottom-1 surface-border">
              <span class="font-semibold text-lg"><i class="pi pi-download mr-2"></i>Service Provider Metadata</span>
            </div>
          </ng-template>

          <p-message severity="info" styleClass="mb-3 w-full"
            text="Use this metadata when configuring your Identity Provider.">
          </p-message>

          <div class="grid">
            <div class="col-12 md:col-6 field">
              <label class="font-medium block mb-2">SP Entity ID</label>
              <div class="p-inputgroup">
                <input pInputText [value]="spEntityId" readonly class="surface-100" />
                <button pButton icon="pi pi-copy" (click)="copyToClipboard(spEntityId)"></button>
              </div>
            </div>
            <div class="col-12 md:col-6 field">
              <label class="font-medium block mb-2">ACS URL</label>
              <div class="p-inputgroup">
                <input pInputText [value]="acsUrl" readonly class="surface-100" />
                <button pButton icon="pi pi-copy" (click)="copyToClipboard(acsUrl)"></button>
              </div>
            </div>
          </div>
        </p-card>
      }
    </div>
  `
})
export class SsoConfigComponent implements OnInit {
  private ssoService = inject(SsoConfigService);
  private messageService = inject(MessageService);

  config = signal<SsoConfig | null>(null);
  loading = signal(true);
  saving = signal(false);
  testing = signal(false);
  ssoEnabled = false;

  providers: Provider[] = SSO_PROVIDERS.map(p => ({ ...p }));
  selectedProvider: Provider | null = null;

  oidcForm: OidcConfigRequest = {
    idpType: 'AZURE_AD',
    providerName: '',
    clientId: '',
    clientSecret: '',
    issuerUrl: ''
  };

  samlForm: SamlConfigRequest = {
    idpType: 'SAML',
    providerName: '',
    metadataUrl: '',
    entityId: '',
    ssoUrl: ''
  };

  spEntityId = 'urn:amazon:cognito:sp:YOUR_USER_POOL_ID';
  acsUrl = 'https://YOUR_DOMAIN.auth.us-east-1.amazoncognito.com/saml2/idpresponse';

  ngOnInit() {
    this.loadConfiguration();
  }

  loadConfiguration() {
    this.loading.set(true);
    this.ssoService.getConfiguration().subscribe({
      next: (data) => {
        this.config.set(data);
        this.ssoEnabled = data?.ssoEnabled ?? false;
        this.loading.set(false);
        this.loadSpMetadata();
      },
      error: () => {
        this.config.set(null);
        this.ssoEnabled = false;
        this.loading.set(false);
      }
    });
  }

  loadSpMetadata() {
    this.ssoService.getSpMetadata().subscribe({
      next: (metadata) => {
        this.spEntityId = metadata.entityId;
        this.acsUrl = metadata.acsUrl;
      },
      error: () => { } // Use defaults
    });
  }

  onProviderChange() {
    // Reset forms
    this.oidcForm = { idpType: 'AZURE_AD', providerName: '', clientId: '', clientSecret: '', issuerUrl: '' };
    this.samlForm = { idpType: 'SAML', providerName: '', metadataUrl: '', entityId: '', ssoUrl: '' };

    if (this.selectedProvider) {
      if (this.isOidcProvider()) {
        this.oidcForm.idpType = 'AZURE_AD';
        this.oidcForm.providerName = this.selectedProvider.label;
      } else {
        this.samlForm.idpType = this.selectedProvider.value === 'OKTA' ? 'OKTA' : 'SAML';
        this.samlForm.providerName = this.selectedProvider.label;
      }
    }
  }

  isOidcProvider(): boolean {
    return this.selectedProvider?.protocol === 'OIDC';
  }

  canTest(): boolean {
    if (this.isOidcProvider()) {
      return !!(this.oidcForm.clientId && this.oidcForm.clientSecret && this.oidcForm.issuerUrl);
    }
    return !!this.samlForm.metadataUrl;
  }

  saveConfiguration() {
    this.saving.set(true);
    const request$ = this.isOidcProvider()
      ? this.ssoService.saveOidcConfig(this.oidcForm)
      : this.ssoService.saveSamlConfig(this.samlForm);

    request$.subscribe({
      next: (data) => {
        this.config.set(data);
        this.saving.set(false);
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'SSO configuration saved' });
      },
      error: (err: { message?: string }) => {
        this.saving.set(false);
        this.messageService.add({ severity: 'error', summary: 'Error', detail: err.message || 'Failed to save' });
      }
    });
  }

  testConnection() {
    this.testing.set(true);
    this.ssoService.testConnection().subscribe({
      next: (result) => {
        this.testing.set(false);
        const severity = result.success ? 'success' : 'warn';
        this.messageService.add({ severity, summary: 'Test Result', detail: result.message });
        this.loadConfiguration();
      },
      error: (err: { message?: string }) => {
        this.testing.set(false);
        this.messageService.add({ severity: 'error', summary: 'Test Failed', detail: err.message || 'Connection test failed' });
      }
    });
  }

  toggleSso() {
    this.ssoService.toggleSso(this.ssoEnabled).subscribe({
      next: (data) => {
        this.config.set(data);
        this.messageService.add({ severity: 'success', summary: this.ssoEnabled ? 'SSO Enabled' : 'SSO Disabled', detail: '' });
      },
      error: (err: { message?: string }) => {
        this.ssoEnabled = !this.ssoEnabled;
        this.messageService.add({ severity: 'error', summary: 'Error', detail: err.message || 'Failed to toggle' });
      }
    });
  }

  deleteConfiguration() {
    if (confirm('Remove SSO configuration? Users will no longer be able to login via SSO.')) {
      this.ssoService.deleteConfiguration().subscribe({
        next: () => {
          this.config.set(null);
          this.selectedProvider = null;
          this.messageService.add({ severity: 'success', summary: 'Removed', detail: 'SSO configuration deleted' });
        },
        error: (err: { message?: string }) => {
          this.messageService.add({ severity: 'error', summary: 'Error', detail: err.message || 'Failed to delete' });
        }
      });
    }
  }

  getTestStatusSeverity(): 'success' | 'danger' | 'warn' | 'info' {
    switch (this.config()?.testStatus) {
      case 'SUCCESS': return 'success';
      case 'FAILED': return 'danger';
      case 'PENDING': return 'warn';
      default: return 'info';
    }
  }

  copyToClipboard(value: string) {
    navigator.clipboard.writeText(value);
    this.messageService.add({ severity: 'info', summary: 'Copied', detail: 'Value copied to clipboard' });
  }
}
