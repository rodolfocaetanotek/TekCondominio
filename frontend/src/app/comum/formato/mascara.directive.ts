import { Directive, ElementRef, inject, input } from '@angular/core';
import { NgControl } from '@angular/forms';
import { FORMATADORES, TipoMascara } from './formato-br';

/** Aplica máscara brasileira enquanto o usuário digita. Uso: <input tcMascara="cnpj" formControlName="cnpj"> */
@Directive({
  selector: 'input[tcMascara]',
  host: { '(input)': 'aoDigitar()', '(blur)': 'aoDigitar()' },
})
export class MascaraDirective {
  readonly tcMascara = input.required<TipoMascara>();
  private readonly controle = inject(NgControl, { self: true });
  private readonly elemento = inject<ElementRef<HTMLInputElement>>(ElementRef);

  aoDigitar(): void {
    const campo = this.elemento.nativeElement;
    const formatado = FORMATADORES[this.tcMascara()](campo.value);
    if (formatado !== campo.value) {
      campo.value = formatado;
    }
    if (this.controle.value !== formatado) {
      this.controle.control?.setValue(formatado);
    }
  }
}
