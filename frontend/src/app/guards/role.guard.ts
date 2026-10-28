import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const currentUser = authService.currentUserValue;
  const expectedRoles: string[] = route.data?.['roles'] || [];

  if (!currentUser || !currentUser.token) {
    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  if (expectedRoles.length > 0 && !expectedRoles.includes(currentUser.role)) {
    if (currentUser.role === 'ROLE_CUSTOMER') {
      router.navigate(['/customer']);
    } else {
      router.navigate(['/home']);
    }
    return false;
  }

  return true;
};
