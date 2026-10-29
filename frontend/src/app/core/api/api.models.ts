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
export interface ProblemaApi {
  status: number;
  codigo: string;
  detail: string;
  erros?: ErroCampo[];
}

export function paraProblema(erro: unknown): ProblemaApi {
  if (erro instanceof HttpErrorResponse) {
    const corpo = erro.error as Partial<ProblemaApi> | null;
    if (corpo && typeof corpo === 'object' && typeof corpo.codigo === 'string') {
      return {
        status: erro.status,
        codigo: corpo.codigo,
        detail: corpo.detail ?? 'Erro ao processar a solicitação.',
        erros: corpo.erros,
      };
    }
    if (erro.status === 0) {
      return { status: 0, codigo: 'NETWORK', detail: 'Não foi possível conectar ao servidor. Verifique sua conexão.' };
    }
    return { status: erro.status, codigo: 'HTTP_' + erro.status, detail: 'Erro ao processar a solicitação.' };
  }
  return { status: -1, codigo: 'UNKNOWN', detail: 'Ocorreu um erro inesperado.' };
}
