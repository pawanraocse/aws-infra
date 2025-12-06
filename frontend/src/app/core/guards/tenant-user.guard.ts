import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * Guard for tenant-specific routes (not for super-admins).
 * Super-admins are redirected to platform dashboard.
 * Used for routes like data entries, tenant-specific dashboard, etc.
 */
export const tenantUserGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.user();
    const isSuperAdmin = user?.role === 'super-admin';

    // Super-admin should not access tenant-specific routes
    if (isSuperAdmin) {
        return router.createUrlTree(['/app/admin/dashboard']);
    }

    return true;
};
