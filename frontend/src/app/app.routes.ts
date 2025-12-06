import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { AppLayoutComponent } from './layout/app-layout.component';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: 'auth',
    canActivate: [guestGuard],
    children: [
      { path: '', redirectTo: 'login', pathMatch: 'full' },
      {
        path: 'login',
        loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
      },
      {
        path: 'signup/personal',
        loadComponent: () => import('./features/auth/signup-personal.component').then(m => m.SignupPersonalComponent)
      },
      {
        path: 'signup/organization',
        loadComponent: () => import('./features/auth/signup-organization.component').then(m => m.SignupOrganizationComponent)
      },
      {
        path: 'verify-email',
        loadComponent: () => import('./features/auth/verify-email/verify-email.component').then(m => m.VerifyEmailComponent)
      }
    ]
  },
  {
    path: 'app',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'admin/dashboard',
        loadComponent: () => import('./features/admin/dashboard/dashboard.component').then(m => m.DashboardComponent),
        canActivate: [adminGuard]
      },
      {
        path: 'admin/users',
        loadComponent: () => import('./features/admin/users/user-list.component').then(m => m.UserListComponent),
        canActivate: [adminGuard]
      },
      {
        path: 'admin/roles',
        loadComponent: () => import('./features/admin/roles/role-list.component').then(m => m.RoleListComponent),
        canActivate: [adminGuard]
      },
      {
        path: 'admin/settings/organization',
        loadComponent: () => import('./features/admin/settings/organization-settings.component').then(m => m.OrganizationSettingsComponent),
        canActivate: [adminGuard]
      },
      {
        path: 'settings/account',
        loadComponent: () => import('./features/settings/account-settings.component').then(m => m.AccountSettingsComponent)
      }
    ]
  },
  {
    path: 'auth/join',
    loadComponent: () => import('./features/auth/join-organization.component').then(m => m.JoinOrganizationComponent)
  },
  { path: '', redirectTo: 'app', pathMatch: 'full' },
  { path: '**', redirectTo: 'app' }
];
