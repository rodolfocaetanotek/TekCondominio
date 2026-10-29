import { Pipe, PipeTransform } from '@angular/core';
import { formatarDataIso } from './datas';

/** Exibe datas de negócio (AAAA-MM-DD) como DD/MM/AAAA, sem fuso horário. */
@Pipe({ name: 'dataIso' })
export class DataIsoPipe implements PipeTransform {
  transform(valor: string | null | undefined): string {
    return formatarDataIso(valor);
  }
}
