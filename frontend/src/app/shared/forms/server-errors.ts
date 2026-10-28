import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiProblem } from '../../core/api/api.models';

/**
 * Exibe nos campos do formulário os erros de validação devolvidos pela API (fonte única das regras).
 * Retorna as mensagens que não puderam ser associadas a nenhum campo.
 */
export function applyServerErrors(form: FormGroup, problem: ApiProblem): string[] {
  const unmatched: string[] = [];
  for (const error of problem.errors ?? []) {
    const control: AbstractControl | null = form.get(error.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: error.message });
      control.markAsTouched();
    } else {
      unmatched.push(error.message);
    }
  }
  return unmatched;
}

/** Primeira mensagem de erro amigável de um controle. */
export function errorMessage(control: AbstractControl | null): string | null {
  const errors = control?.errors;
  if (!errors) {
    return null;
  }
  if (errors['server']) return errors['server'] as string;
  if (errors['required']) return 'Campo obrigatório.';
  if (errors['mismatch']) return 'As senhas não conferem.';
  if (errors['email']) return 'E-mail inválido.';
  if (errors['maxlength']) return `Máximo de ${(errors['maxlength'] as { requiredLength: number }).requiredLength} caracteres.`;
  if (errors['minlength']) return `Mínimo de ${(errors['minlength'] as { requiredLength: number }).requiredLength} caracteres.`;
  if (errors['pattern']) return 'Formato inválido.';
  return 'Valor inválido.';
}
