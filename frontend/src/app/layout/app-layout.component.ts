import {Component, computed, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {AuthService} from '../core/auth.service';
import {MenubarModule} from 'primeng/menubar';
import {ButtonModule} from 'primeng/button';
import {AvatarModule} from 'primeng/avatar';
import {MenuItem} from 'primeng/api';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, MenubarModule, ButtonModule, AvatarModule],
  templateUrl: './app-layout.component.html',
  styleUrls: ['./app-layout.component.scss']
})
export class AppLayoutComponent {
  authService = inject(AuthService);

  items = computed<MenuItem[]>(() => {
    const user = this.authService.user();
    const isOrganization = user?.tenantType === 'ORGANIZATION';
    const isSuperAdmin = user?.role === 'super-admin';
    const isTenantAdmin = user?.role === 'admin';

    // SUPER-ADMIN: Platform management menu (no personal data)
    if (isSuperAdmin) {
      return [
        { label: 'Platform', icon: 'pi pi-home', routerLink: '/app/admin/dashboard' },
        { label: 'Tenants', icon: 'pi pi-building', routerLink: '/app/admin/tenants' },
        { label: 'Settings', icon: 'pi pi-user', routerLink: '/app/settings/account' }
      ];
    }


    // PERSONAL users: Simple menu
    if (!isOrganization) {
      return [
        { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/app/dashboard' },
        {
          label: 'Settings',
          icon: 'pi pi-cog',
          items: [
            { label: 'Account', icon: 'pi pi-user', routerLink: '/app/settings/account' },
            { label: 'Billing', icon: 'pi pi-credit-card', routerLink: '/app/settings/billing' }
          ]
        }
      ];
    }

    // ORGANIZATION users: Full admin menu
    const items: MenuItem[] = [
      { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/app/dashboard' },
      { label: 'Entries', icon: 'pi pi-list', routerLink: '/app/dashboard' }
    ];

    if (isTenantAdmin) {
      items.push({
        label: 'Admin',
        icon: 'pi pi-shield',
        items: [
          { label: 'Users', icon: 'pi pi-users', routerLink: '/app/admin/users' },
          { label: 'Roles', icon: 'pi pi-id-card', routerLink: '/app/admin/roles' },
          { label: 'Organization', icon: 'pi pi-building', routerLink: '/app/admin/settings/organization' },
          { separator: true },
          { label: 'Billing & Subscription', icon: 'pi pi-credit-card', routerLink: '/app/settings/billing' }
        ]
      });
    }

    // Add Settings for all organization users
    items.push({
      label: 'Settings',
      icon: 'pi pi-cog',
      items: [
        { label: 'Account', icon: 'pi pi-user', routerLink: '/app/settings/account' },
        { label: 'API Keys', icon: 'pi pi-key', routerLink: '/app/settings/api-keys' }
      ]
    });

    return items;
  });


  logout() {
    this.authService.logout();
  }
}
