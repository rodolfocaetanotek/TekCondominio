import { AbstractControl, FormGroup } from '@angular/forms';
import { ProblemaApi } from '../../core/api/api.models';

/**
 * Exibe nos campos do formulário os erros de validação devolvidos pela API (fonte única das regras).
 * Retorna as mensagens que não puderam ser associadas a nenhum campo.
 */
export function aplicarErrosServidor(form: FormGroup, problema: ProblemaApi): string[] {
  const naoConciliado: string[] = [];
  for (const erro of problema.erros ?? []) {
    const controle: AbstractControl | null = form.get(erro.campo);
    if (controle) {
      controle.setErrors({ ...(controle.errors ?? {}), server: erro.mensagem });
      controle.markAsTouched();
    } else {
      naoConciliado.push(erro.mensagem);
    }
  }
  return naoConciliado;
}

/** Primeira mensagem de erro amigável de um controle. */
export function mensagemErro(controle: AbstractControl | null): string | null {
  const erros = controle?.errors;
  if (!erros) {
    return null;
  }
  if (erros['server']) return erros['server'] as string;
  if (erros['required']) return 'Campo obrigatório.';
  if (erros['mismatch']) return 'As senhas não conferem.';
  if (erros['email']) return 'E-mail inválido.';
  if (erros['maxlength']) return `Máximo de ${(erros['maxlength'] as { requiredLength: number }).requiredLength} caracteres.`;
  if (erros['minlength']) return `Mínimo de ${(erros['minlength'] as { requiredLength: number }).requiredLength} caracteres.`;
  if (erros['pattern']) return 'Formato inválido.';
  return 'Valor inválido.';
}
