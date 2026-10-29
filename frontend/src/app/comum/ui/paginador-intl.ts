import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

@Injectable()
export class PaginadorIntlPtBr extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Itens por página';
  override nextPageLabel = 'Próxima página';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primeira página';
  override lastPageLabel = 'Última página';

  override getRangeLabel = (pagina: number, tamanhoPagina: number, tamanho: number): string => {
    if (tamanho === 0) {
      return '0 de 0';
    }
    const inicio = pagina * tamanhoPagina + 1;
    const fim = Math.min(inicio + tamanhoPagina - 1, tamanho);
    return `${inicio}–${fim} de ${tamanho}`;
  };
}
