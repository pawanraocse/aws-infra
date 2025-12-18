import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * Guard for admin routes.
 * Allows access to both admin and super-admin roles.
 */
export const adminGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.user();
    const isAdmin = user?.role === 'admin' || user?.role === 'super-admin';

    if (isAdmin) {
        return true;
    }

    // Redirect to dashboard if not admin
    return router.createUrlTree(['/app/dashboard']);
};
