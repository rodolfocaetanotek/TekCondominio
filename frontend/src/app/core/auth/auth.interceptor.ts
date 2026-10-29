import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const ENDPOINTS_AUTH = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'];

/**
 * Anexa o access token às chamadas da API e, ao receber 401, tenta renovar a sessão uma única vez
 * antes de repetir a requisição. Se a renovação falhar, encerra a sessão.
 */
export const authInterceptor: HttpInterceptorFn = (req, proximo) => {
  if (!req.url.startsWith('/api/')) {
    return proximo(req);
  }
  const auth = inject(AuthService);
  const ehEndpointAuth = ENDPOINTS_AUTH.some((url) => req.url.startsWith(url));
  const comToken = (solicitacao: HttpRequest<unknown>) => {
    const token = auth.accessToken();
    return token && !ehEndpointAuth ? solicitacao.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : solicitacao;
  };

  return proximo(comToken(req)).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || erro.status !== 401 || ehEndpointAuth || !auth.accessToken()) {
        return throwError(() => erro);
      }
      return auth.renovar().pipe(
        catchError(() => {
          auth.encerrarSessao(true);
          return throwError(() => erro);
        }),
        switchMap(() => proximo(comToken(req))),
      );
    }),
  );
};
