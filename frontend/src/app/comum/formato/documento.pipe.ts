import { Pipe, PipeTransform } from '@angular/core';
import { FORMATADORES, TipoMascara } from './formato-br';

/** Exibe CNPJ, CEP ou telefone formatados: {{ c.cnpj | documento: 'cnpj' }} */
@Pipe({ name: 'documento' })
export class DocumentoPipe implements PipeTransform {
  transform(valor: string | null | undefined, tipo: TipoMascara): string {
    return valor ? FORMATADORES[tipo](valor) : '—';
  }
}
