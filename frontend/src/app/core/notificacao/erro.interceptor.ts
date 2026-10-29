import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { toProblem } from '../api/api.models';
import { NotificationService } from './notificacao.service';

/**
 * Feedback global para falhas que a tela não trata: sem conexão, sem permissão e erros do servidor.
 * Erros de validação (400), conflito (409) e regra de negócio (422) ficam a cargo de cada formulário.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notify = inject(NotificationService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && req.url.startsWith('/api/')) {
        if (error.status === 0 || error.status === 403 || error.status >= 500) {
          notify.error(toProblem(error).detail);
        }
      }
      return throwError(() => error);
    }),
  );
};
