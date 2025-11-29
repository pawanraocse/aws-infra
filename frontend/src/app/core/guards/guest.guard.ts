import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../auth.service';

export const guestGuard: CanActivateFn = async (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const isAuthenticated = await authService.checkAuth();

    if (!isAuthenticated) {
        return true;
    }

    return router.createUrlTree(['/app']);
};
