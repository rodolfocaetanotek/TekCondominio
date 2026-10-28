import { Pipe, PipeTransform } from '@angular/core';
import { formatIsoDate } from './dates';

/** Exibe datas de negócio (AAAA-MM-DD) como DD/MM/AAAA, sem fuso horário. */
@Pipe({ name: 'isoDate' })
export class IsoDatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatIsoDate(value);
  }
}
