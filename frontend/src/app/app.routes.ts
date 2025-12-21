import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { AppLayoutComponent } from './layout/app-layout.component';
import { adminGuard } from './core/guards/admin.guard';
import { superAdminGuard } from './core/guards/super-admin.guard';
import { tenantUserGuard } from './core/guards/tenant-user.guard';

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
      },
      {
        path: 'forgot-password',
        loadComponent: () => import('./features/auth/pwd-reset.component').then(m => m.PasswordResetComponent)
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
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
        canActivate: [tenantUserGuard]  // Redirect super-admin to platform dashboard
      },

      // Platform Admin Routes (super-admin only)
      {
        path: 'admin/dashboard',
        loadComponent: () => import('./features/admin/platform/platform-dashboard.component').then(m => m.PlatformDashboardComponent),
        canActivate: [superAdminGuard]
      },
      {
        path: 'admin/tenants',
        loadComponent: () => import('./features/admin/platform/tenant-list.component').then(m => m.TenantListComponent),
        canActivate: [superAdminGuard]
      },
      // Tenant Admin Routes
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
        path: 'admin/settings/sso',
        loadComponent: () => import('./features/admin/settings/sso-config.component').then(m => m.SsoConfigComponent),
        canActivate: [adminGuard]
      },
      {
        path: 'admin/settings/group-mapping',
        loadComponent: () => import('./features/admin/settings/group-mapping.component').then(m => m.GroupMappingComponent),
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
