import { Pipe, PipeTransform } from '@angular/core';
import { label } from './labels';

/** {{ e.status | label: 'entryStatus' }} */
@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(code: string | null | undefined, group: string): string {
    return label(group, code);
  }
}
