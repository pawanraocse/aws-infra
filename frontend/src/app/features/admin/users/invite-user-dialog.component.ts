import {Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {SelectModule} from 'primeng/select';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {InvitationService} from '../../../core/services/invitation.service';

@Component({
  selector: 'app-invite-user-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ButtonModule, InputTextModule, SelectModule],
  template: `
    <form [formGroup]="inviteForm" (ngSubmit)="onSubmit()" class="flex flex-column gap-4">
      <div class="flex flex-column gap-2">
        <label htmlFor="email">Email Address</label>
        <input pInputText id="email" formControlName="email" placeholder="user@example.com" />
        <small *ngIf="inviteForm.get('email')?.invalid && inviteForm.get('email')?.touched" class="p-error">
          Valid email is required
        </small>
      </div>

      <div class="flex flex-column gap-2">
        <label htmlFor="role">Role</label>
        <p-select
          id="role"
          [options]="roles"
          formControlName="roleId"
          optionLabel="name"
          optionValue="id"
          placeholder="Select a role"
          appendTo="body"
          [style]="{'width':'100%'}">
        </p-select>
      </div>

      <div class="flex justify-content-end gap-2 mt-4">
        <p-button label="Cancel" styleClass="p-button-text" (onClick)="close()"></p-button>
        <p-button label="Send Invitation" type="submit" [loading]="loading()" [disabled]="inviteForm.invalid"></p-button>
      </div>

      <div *ngIf="error()" class="p-error mt-2">{{ error() }}</div>
    </form>
  `
})
export class InviteUserDialogComponent {
  private fb = inject(FormBuilder);
  private invitationService = inject(InvitationService);
  private ref = inject(DynamicDialogRef);

  loading = signal(false);
  error = signal('');

  // Roles matching database seed data (V1__authorization_schema.sql)
  // TODO: Fetch roles from backend API for dynamic role management
  roles = [
    { name: 'Admin', id: 'admin', description: 'Full tenant access' },
    { name: 'Editor', id: 'editor', description: 'Read, edit, delete, share' },
    { name: 'Viewer', id: 'viewer', description: 'Read-only access' },
    { name: 'Guest', id: 'guest', description: 'Limited access' }
  ];

  inviteForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    roleId: ['', Validators.required]
  });

  onSubmit() {
    if (this.inviteForm.valid) {
      this.loading.set(true);
      this.error.set('');

      const req = {
        email: this.inviteForm.value.email!,
        roleId: this.inviteForm.value.roleId!
      };

      this.invitationService.createInvitation(req).subscribe({
        next: () => {
          this.loading.set(false);
          this.ref.close(true);
        },
        error: (err) => {
          this.loading.set(false);
          if (err.status !== 401 && err.status !== 403) {
            this.error.set(err.error?.message || 'Failed to send invitation');
          }
        }
      });
    }
  }

  close() {
    this.ref.close(false);
  }
}
