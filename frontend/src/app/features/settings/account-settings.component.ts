import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { AuthService } from '../../core/auth.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [CommonModule, CardModule, ButtonModule, DialogModule, InputTextModule, ToastModule, ConfirmDialogModule],
  providers: [MessageService, ConfirmationService],
  template: `
    <div class="p-4">
      <p-toast></p-toast>
      <p-confirmDialog></p-confirmDialog>
      
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
                (click)="confirmDelete()">
              </p-button>
            </div>
          </p-card>
        </div>
      </div>
    </div>
  `

})
export class AccountSettingsComponent {
  authService = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);

  deleting = signal(false);

  isSuperAdmin(): boolean {
    return this.authService.user()?.role === 'super-admin';
  }

  getAccountType(user: any): string {
    if (user?.role === 'super-admin') {
      return 'PLATFORM';
    }
    return user?.tenantType || 'PERSONAL';
  }

  confirmDelete(): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to delete your account? This will permanently delete all your data and cannot be undone.',
      header: 'Confirm Account Deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.deleteAccount();
      }
    });
  }

  deleteAccount(): void {
    this.deleting.set(true);

    this.http.delete(`${environment.apiUrl}/auth/api/v1/auth/me`).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Account Deleted',
          detail: 'Your account has been successfully deleted.'
        });
        // Logout and redirect to login
        setTimeout(() => {
          this.authService.logout();
        }, 1500);
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
