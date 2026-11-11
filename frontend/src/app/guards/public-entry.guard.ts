import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const publicEntryRedirectGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const currentUser = authService.restoreSession();
  if (!currentUser) {
    return true;
  }

  const dashboardRoute = authService.getDashboardRoute(currentUser);
  if (dashboardRoute) {
    return router.createUrlTree([dashboardRoute]);
  }

  return true;
};
