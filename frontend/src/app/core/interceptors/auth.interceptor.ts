import { HttpInterceptorFn } from '@angular/common/http';
import { fetchAuthSession } from 'aws-amplify/auth';
import { from, switchMap } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  return from(fetchAuthSession()).pipe(
    switchMap(session => {
      const token = session.tokens?.accessToken?.toString();
      if (token) {
        const cloned = req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          }
        });
        return next(cloned);
      }
      return next(req);
    })
  );
};
