import { Pipe, PipeTransform } from '@angular/core';
import { FORMATTERS, MaskKind } from './formato-br';

/** Exibe CNPJ, CEP ou telefone formatados: {{ c.cnpj | doc: 'cnpj' }} */
@Pipe({ name: 'doc' })
export class DocPipe implements PipeTransform {
  transform(value: string | null | undefined, kind: MaskKind): string {
    return value ? FORMATTERS[kind](value) : '—';
  }
}
