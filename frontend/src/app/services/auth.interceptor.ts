import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const userData = localStorage.getItem('traveleasy_user');
  if (userData) {
    try {
      const user = JSON.parse(userData);
      if (user && user.token) {
        const cloned = req.clone({
          setHeaders: {
            Authorization: `Bearer ${user.token}`
          }
        });
        return next(cloned);
      }
    } catch {
      // Ignored
    }
  }
  return next(req);
};
