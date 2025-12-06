import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * Guard for platform-level routes.
 * Only allows super-admin users (system administrators).
 * Used for routes like tenant management, system settings, etc.
 */
export const superAdminGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.user();
    const isSuperAdmin = user?.role === 'super-admin';

    if (isSuperAdmin) {
        return true;
    }

    // Redirect to dashboard if not super-admin
    return router.createUrlTree(['/app/dashboard']);
};
