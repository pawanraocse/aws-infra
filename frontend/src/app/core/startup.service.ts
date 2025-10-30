import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class StartupService {
  private authService = inject(AuthService);

  /**
   * Call this in main.ts or app bootstrap to check auth status on startup
   */
  init(): Promise<void> {
    return new Promise(resolve => {
      this.authService.checkAuth().subscribe(() => resolve());
    });
  }
}

