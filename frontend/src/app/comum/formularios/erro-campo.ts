import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { mensagemErro } from './erros-servidor';

/** Mensagem de erro de um campo; usar dentro de <mat-error>. */
@Component({
  selector: 'tc-erro-campo',
  template: `{{ mensagem() }}`,
})
export class MensagemErroCampo {
  readonly controle = input.required<AbstractControl | null>();

  mensagem(): string | null {
    return mensagemErro(this.controle());
  }
}
