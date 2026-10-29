import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { errorMessage } from './erros-servidor';

/** Mensagem de erro de um campo; usar dentro de <mat-error>. */
@Component({
  selector: 'tc-field-error',
  template: `{{ message() }}`,
})
export class FieldError {
  readonly control = input.required<AbstractControl | null>();

  message(): string | null {
    return errorMessage(this.control());
  }
}
