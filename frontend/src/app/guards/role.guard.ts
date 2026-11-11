import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const currentUser = authService.restoreSession();
  const expectedRoles: string[] = route.data?.['roles'] || [];

  if (!currentUser || !currentUser.token) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  if (expectedRoles.length > 0 && !expectedRoles.includes(currentUser.role)) {
    const dashboardRoute = authService.getDashboardRoute(currentUser);
    return router.createUrlTree([dashboardRoute ?? '/home']);
  }

  return true;
};
