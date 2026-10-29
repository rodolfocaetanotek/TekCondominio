import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { paraProblema } from '../api/api.models';
import { NotificacaoService } from './notificacao.service';

/**
 * Feedback global para falhas que a tela não trata: sem conexão, sem permissão e erros do servidor.
 * Erros de validação (400), conflito (409) e regra de negócio (422) ficam a cargo de cada formulário.
 */
export const erroInterceptor: HttpInterceptorFn = (req, proximo) => {
  const notificacao = inject(NotificacaoService);
  return proximo(req).pipe(
    catchError((erro: unknown) => {
      if (erro instanceof HttpErrorResponse && req.url.startsWith('/api/')) {
        if (erro.status === 0 || erro.status === 403 || erro.status >= 500) {
          notificacao.erro(paraProblema(erro).detail);
        }
      }
      return throwError(() => erro);
    }),
  );
};
