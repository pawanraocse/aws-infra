import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth.service';

export const adminGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.user();
    const isAdmin = user?.role === 'tenant-admin';

    if (isAdmin) {
        return true;
    }

    // Redirect to dashboard if not admin
    return router.createUrlTree(['/app/dashboard']);
};
