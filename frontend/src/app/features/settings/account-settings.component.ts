import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../core/auth.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, CardModule, ButtonModule, DialogModule, InputTextModule, ToastModule],
  providers: [MessageService],
  template: `
    <div class="p-4">
      <p-toast></p-toast>
      
      <h1 class="text-3xl font-bold mb-4">Account Settings</h1>
      
      <div class="grid">
        <!-- Profile Info -->
        <div class="col-12 md:col-6">
          <p-card header="Profile Information" styleClass="h-full">
            <div class="flex flex-column gap-3" *ngIf="authService.user() as user">
              <div class="flex justify-content-between p-2 surface-50 border-round">
                <span class="font-medium text-600">Email</span>
                <span class="font-semibold">{{ user.email }}</span>
              </div>
              <div class="flex justify-content-between p-2 surface-50 border-round">
                <span class="font-medium text-600">Account Type</span>
                <span class="font-semibold">{{ getAccountType(user) }}</span>
              </div>
              <div class="flex justify-content-between p-2 surface-50 border-round">
                <span class="font-medium text-600">Role</span>
                <span class="font-semibold">{{ user.role }}</span>
              </div>
            </div>
          </p-card>
        </div>

        <!-- Danger Zone - Hidden for super-admin -->
        <div class="col-12 md:col-6" *ngIf="!isSuperAdmin()">
          <p-card header="Danger Zone" styleClass="h-full border-red-500 border-1">
            <div class="flex flex-column gap-3">
              <p class="text-600 line-height-3">
                Deleting your account will permanently remove all your data, including any entries 
                you've created. This action cannot be undone.
              </p>
              <p-button 
                label="Delete My Account" 
                icon="pi pi-trash" 
                severity="danger"
                [loading]="deleting()"
                (click)="showDeleteDialog = true">
              </p-button>
            </div>
          </p-card>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Dialog -->
    <p-dialog 
      header="Delete Account" 
      [(visible)]="showDeleteDialog" 
      [modal]="true" 
      [style]="{width: '450px'}"
      [closable]="!deleting()">
      <div class="flex flex-column gap-4">
        <div class="p-3 surface-100 border-round">
          <i class="pi pi-exclamation-triangle text-orange-500 mr-2"></i>
          <span class="font-medium">This action is permanent and cannot be undone.</span>
        </div>
        
        <p class="text-600">
          Type <span class="font-bold text-red-500">DELETE</span> below to confirm:
        </p>
        
        <input 
          pInputText 
          [(ngModel)]="confirmationText" 
          placeholder="Type DELETE to confirm"
          class="w-full"
          [disabled]="deleting()">
        
        <div class="flex gap-2 justify-content-end">
          <p-button 
            label="Cancel" 
            severity="secondary" 
            (click)="showDeleteDialog = false; confirmationText = ''"
            [disabled]="deleting()">
          </p-button>
          <p-button 
            label="Delete Account" 
            severity="danger"
            icon="pi pi-trash"
            [loading]="deleting()"
            [disabled]="confirmationText !== 'DELETE'"
            (click)="deleteAccount()">
          </p-button>
        </div>
      </div>
    </p-dialog>
  `
})
export class AccountSettingsComponent {
  authService = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private messageService = inject(MessageService);

  deleting = signal(false);
  showDeleteDialog = false;
  confirmationText = '';

  isSuperAdmin(): boolean {
    return this.authService.user()?.role === 'super-admin';
  }

  getAccountType(user: any): string {
    if (user?.role === 'super-admin') {
      return 'PLATFORM';
    }
    return user?.tenantType || 'PERSONAL';
  }

  deleteAccount(): void {
    if (this.confirmationText !== 'DELETE') {
      return;
    }

    this.deleting.set(true);

    this.http.post(`${environment.apiUrl}/auth/api/v1/account/delete`, {
      confirmation: this.confirmationText
    }).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Account Deleted',
          detail: 'Your account has been successfully deleted. Redirecting to login...'
        });
        // Logout and redirect to login
        setTimeout(() => {
          this.authService.logout();
        }, 2000);
      },
      error: (err) => {
        this.deleting.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: (err as any).error?.message || 'Failed to delete account. Please try again.'
        });
      }
    });
  }
}

