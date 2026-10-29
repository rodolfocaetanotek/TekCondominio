import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Confirmação de senha (apenas UX; a política de senha é validada pelo backend). */
export function senhasIguais(chaveSenha: string, chaveConfirmacao: string): ValidatorFn {
  return (grupo: AbstractControl): ValidationErrors | null => {
    const confirmar = grupo.get(chaveConfirmacao);
    if (!confirmar) {
      return null;
    }
    const divergencia = !!confirmar.value && grupo.get(chaveSenha)?.value !== confirmar.value;
    const erros: ValidationErrors = { ...(confirmar.errors ?? {}) };
    delete erros['mismatch'];
    if (divergencia) {
      erros['mismatch'] = true;
    }
    confirmar.setErrors(Object.keys(erros).length ? erros : null);
    return null;
  };
}
