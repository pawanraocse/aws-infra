import { HttpInterceptorFn } from '@angular/common/http';
import { fetchAuthSession } from 'aws-amplify/auth';
import { from, switchMap } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  return from(fetchAuthSession()).pipe(
    switchMap(session => {
      // Use idToken instead of accessToken because:
      // - AWS Cognito ID tokens contain custom attributes (custom:tenantId, custom:role)
      // - Access tokens do NOT contain custom attributes
      // - Gateway needs custom:tenantId claim for tenant routing
      const token = session.tokens?.idToken?.toString();
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
