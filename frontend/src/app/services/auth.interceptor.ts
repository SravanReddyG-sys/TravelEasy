import { HttpInterceptorFn } from '@angular/common/http';
import { readAndValidateStoredSession } from '../shared/utils/auth-session.util';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const user = readAndValidateStoredSession(typeof localStorage !== 'undefined' ? localStorage : null);
  if (user && user.token) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${user.token}`
      }
    });
    return next(cloned);
  }
  return next(req);
};
