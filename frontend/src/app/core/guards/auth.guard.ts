import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../auth.service';

export const authGuard: CanActivateFn = async (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Check auth status (this will also load user info if session exists)
  const isAuthenticated = await authService.checkAuth();

  if (isAuthenticated) {
    const user = authService.user();
    // Super-admin should go to platform dashboard, not regular dashboard
    if (user?.role === 'super-admin') {
      const url = state.url;
      // Redirect if accessing root /app, /app/, or /app/dashboard
      if (url === '/app' || url === '/app/' || url.startsWith('/app/dashboard')) {
        return router.createUrlTree(['/app/admin/dashboard']);
      }
    }
    return true;
  }

  return router.createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: state.url }
  });
};
