import { HttpErrorResponse } from '@angular/common/http';

/** Envelope de paginação retornado pela API. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldError {
  field: string;
  message: string;
}

/** Erro no formato RFC 9457 (problem+json) produzido pela API. */
export interface ApiProblem {
  status: number;
  code: string;
  detail: string;
  errors?: FieldError[];
}

export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiProblem> | null;
    if (body && typeof body === 'object' && typeof body.code === 'string') {
      return {
        status: error.status,
        code: body.code,
        detail: body.detail ?? 'Erro ao processar a solicitação.',
        errors: body.errors,
      };
    }
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK', detail: 'Não foi possível conectar ao servidor. Verifique sua conexão.' };
    }
    return { status: error.status, code: 'HTTP_' + error.status, detail: 'Erro ao processar a solicitação.' };
  }
  return { status: -1, code: 'UNKNOWN', detail: 'Ocorreu um erro inesperado.' };
}
