import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { UserRoleService } from '../../../core/services/user-role.service';

@Component({
    selector: 'app-user-details-dialog',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, ButtonModule, InputTextModule, SelectModule],
    template: `
    <form [formGroup]="userForm" (ngSubmit)="onSubmit()" class="flex flex-column gap-4">
      <div class="flex flex-column gap-2">
        <label htmlFor="email">Email Address</label>
        <input pInputText id="email" formControlName="email" [readonly]="true" class="bg-gray-100" />
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
          [style]="{'width':'100%'}">
        </p-select>
      </div>

      <div class="flex justify-content-end gap-2 mt-4">
        <p-button label="Cancel" styleClass="p-button-text" (onClick)="close()"></p-button>
        <p-button label="Save Changes" type="submit" [loading]="loading" [disabled]="userForm.invalid || userForm.pristine"></p-button>
      </div>
      
      <div *ngIf="error" class="p-error mt-2">{{ error }}</div>
    </form>
  `
})
export class UserDetailsDialogComponent implements OnInit {
    private fb = inject(FormBuilder);
    private userRoleService = inject(UserRoleService);
    private ref = inject(DynamicDialogRef);
    private config = inject(DynamicDialogConfig);

    loading = false;
    error = '';
    userId = '';

    // TODO: Fetch roles from backend
    roles = [
        { name: 'Admin', id: 'admin' },
        { name: 'User', id: 'user' }
    ];

    userForm = this.fb.group({
        email: ['', Validators.required],
        roleId: ['', Validators.required]
    });

    ngOnInit() {
        const data = this.config.data;
        if (data) {
            this.userId = data.userId; // Assuming userId is passed, or we use email if userId not available in list
            this.userForm.patchValue({
                email: data.email,
                roleId: data.roleId
            });
        }
    }

    onSubmit() {
        if (this.userForm.valid) {
            this.loading = true;
            this.error = '';

            const newRoleId = this.userForm.value.roleId!;

            this.userRoleService.updateUserRole(this.userId, newRoleId).subscribe({
                next: () => {
                    this.loading = false;
                    this.ref.close(true);
                },
                error: (err) => {
                    this.loading = false;
                    this.error = err.error?.message || 'Failed to update user role';
                }
            });
        }
    }

    close() {
        this.ref.close(false);
    }
}
