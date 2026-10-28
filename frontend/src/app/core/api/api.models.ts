import { HttpErrorResponse } from '@angular/common/http';

/** Envelope de paginação retornado pela API. */
export interface PaginaResponse<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface ErroCampo {
  campo: string;
  mensagem: string;
}

/** Erro no formato RFC 9457 (problem+json) produzido pela API. */
export interface ApiProblem {
  status: number;
  codigo: string;
  detail: string;
  erros?: ErroCampo[];
}

export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiProblem> | null;
    if (body && typeof body === 'object' && typeof body.codigo === 'string') {
      return {
        status: error.status,
        codigo: body.codigo,
        detail: body.detail ?? 'Erro ao processar a solicitação.',
        erros: body.erros,
      };
    }
    if (error.status === 0) {
      return { status: 0, codigo: 'NETWORK', detail: 'Não foi possível conectar ao servidor. Verifique sua conexão.' };
    }
    return { status: error.status, codigo: 'HTTP_' + error.status, detail: 'Erro ao processar a solicitação.' };
  }
  return { status: -1, codigo: 'UNKNOWN', detail: 'Ocorreu um erro inesperado.' };
}
