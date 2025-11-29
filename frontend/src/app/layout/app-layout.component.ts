import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { MenubarModule } from 'primeng/menubar';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { MenuItem } from 'primeng/api';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, MenubarModule, ButtonModule, AvatarModule],
  templateUrl: './app-layout.component.html',
  styleUrls: ['./app-layout.component.scss']
})
export class AppLayoutComponent {
  authService = inject(AuthService);
  items: MenuItem[] = [
    { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/app/dashboard' },
    { label: 'Entries', icon: 'pi pi-list', routerLink: '/app/entries' }
  ];

  logout() {
    this.authService.logout();
  }
}
