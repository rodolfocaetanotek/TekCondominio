import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const AUTH_ENDPOINTS = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'];

/**
 * Anexa o access token às chamadas da API e, ao receber 401, tenta renovar a sessão uma única vez
 * antes de repetir a requisição. Se a renovação falhar, encerra a sessão.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const auth = inject(AuthService);
  const isAuthEndpoint = AUTH_ENDPOINTS.some((url) => req.url.startsWith(url));
  const withToken = (request: HttpRequest<unknown>) => {
    const token = auth.accessToken();
    return token && !isAuthEndpoint ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  };

  return next(withToken(req)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint || !auth.accessToken()) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError(() => {
          auth.endSession(true);
          return throwError(() => error);
        }),
        switchMap(() => next(withToken(req))),
      );
    }),
  );
};
