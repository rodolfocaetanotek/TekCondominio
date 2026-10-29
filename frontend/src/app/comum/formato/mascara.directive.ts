import { Directive, ElementRef, inject, input } from '@angular/core';
import { NgControl } from '@angular/forms';
import { FORMATTERS, MaskKind } from './formato-br';

/** Aplica máscara brasileira enquanto o usuário digita. Uso: <input tcMask="cnpj" formControlName="cnpj"> */
@Directive({
  selector: 'input[tcMask]',
  host: { '(input)': 'onInput()', '(blur)': 'onInput()' },
})
export class MaskDirective {
  readonly tcMask = input.required<MaskKind>();
  private readonly control = inject(NgControl, { self: true });
  private readonly el = inject<ElementRef<HTMLInputElement>>(ElementRef);

  onInput(): void {
    const input = this.el.nativeElement;
    const formatted = FORMATTERS[this.tcMask()](input.value);
    if (formatted !== input.value) {
      input.value = formatted;
    }
    if (this.control.value !== formatted) {
      this.control.control?.setValue(formatted);
    }
  }
}
