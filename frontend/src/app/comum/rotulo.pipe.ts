import { Pipe, PipeTransform } from '@angular/core';
import { rotulo } from './rotulos';

/** {{ e.situacao | rotulo: 'entryStatus' }} */
@Pipe({ name: 'rotulo' })
export class RotuloPipe implements PipeTransform {
  transform(codigo: string | null | undefined, grupo: string): string {
    return rotulo(grupo, codigo);
  }
}
