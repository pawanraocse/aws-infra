import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { DividerModule } from 'primeng/divider';

@Component({
    selector: 'app-sso-config',
    standalone: true,
    imports: [
        CommonModule, CardModule, ButtonModule,
        InputTextModule, MessageModule, TagModule, DividerModule
    ],
    template: `
    <div class="surface-ground p-4">
      <div class="flex justify-content-between align-items-center mb-4">
        <div>
          <h2 class="text-2xl font-bold text-900 m-0">SSO Configuration</h2>
          <p class="text-600 mt-1 mb-0">Configure Single Sign-On for your organization</p>
        </div>
        <p-tag severity="info" value="Enterprise Feature" icon="pi pi-star"></p-tag>
      </div>

      <!-- SAML Section -->
      <p-card styleClass="shadow-2 mb-4">
        <ng-template pTemplate="header">
          <div class="p-3 border-bottom-1 surface-border">
            <span class="font-semibold text-lg"><i class="pi pi-shield mr-2"></i>SAML 2.0 Configuration</span>
          </div>
        </ng-template>
        <div class="flex flex-column gap-3">
          <p-message severity="info" 
            text="SAML 2.0 integration allows authentication using Okta, Azure AD, Ping Identity, etc.">
          </p-message>
          
          <div class="grid">
            <div class="col-12 md:col-6">
              <label class="font-medium block mb-2">Identity Provider (IdP)</label>
              <select class="w-full p-inputtext" disabled>
                <option>Select Provider...</option>
                <option>Okta</option>
                <option>Azure AD</option>
              </select>
            </div>
            <div class="col-12 md:col-6">
              <label class="font-medium block mb-2">Entity ID</label>
              <input pInputText class="w-full" placeholder="https://your-idp.com/entity" disabled />
            </div>
          </div>

          <p-message severity="warn" text="SAML requires Enterprise tier. Contact sales to upgrade."></p-message>
        </div>
      </p-card>

      <!-- OIDC Section -->
      <p-card styleClass="shadow-2 mb-4">
        <ng-template pTemplate="header">
          <div class="p-3 border-bottom-1 surface-border">
            <span class="font-semibold text-lg"><i class="pi pi-key mr-2"></i>OpenID Connect (OIDC)</span>
          </div>
        </ng-template>
        <div class="flex flex-column gap-3">
          <p-message severity="info" 
            text="OIDC allows OAuth 2.0 authentication with Google Workspace, Microsoft 365, etc.">
          </p-message>

          <div class="grid">
            <div class="col-12 md:col-6">
              <label class="font-medium block mb-2">Client ID</label>
              <input pInputText class="w-full" placeholder="your-client-id" disabled />
            </div>
            <div class="col-12 md:col-6">
              <label class="font-medium block mb-2">Client Secret</label>
              <input pInputText class="w-full" type="password" placeholder="••••••••" disabled />
            </div>
          </div>

          <p-message severity="warn" text="OIDC requires Enterprise tier. Contact sales to upgrade."></p-message>
        </div>
      </p-card>

      <!-- Domain Verification Section -->
      <p-card styleClass="shadow-2">
        <ng-template pTemplate="header">
          <div class="p-3 border-bottom-1 surface-border">
            <span class="font-semibold text-lg"><i class="pi pi-globe mr-2"></i>Domain Verification</span>
          </div>
        </ng-template>
        <div class="flex flex-column gap-3">
          <p-message severity="info" text="Verify your company domain to enable automatic team member detection."></p-message>

          <div class="field">
            <label class="font-medium block mb-2">Company Domain</label>
            <div class="p-inputgroup">
              <span class="p-inputgroup-addon">&#64;</span>
              <input pInputText placeholder="yourcompany.com" disabled />
              <p-button label="Verify" disabled="true"></p-button>
            </div>
          </div>

          <p-message severity="warn" text="Domain verification requires Premium or Enterprise tier."></p-message>
        </div>
      </p-card>
    </div>
  `
})
export class SsoConfigComponent {
    private http = inject(HttpClient);
    loading = signal(false);
}
