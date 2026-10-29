import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Confirmação de senha (apenas UX; a política de senha é validada pelo backend). */
export function passwordsMatch(passwordKey: string, confirmKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const confirm = group.get(confirmKey);
    if (!confirm) {
      return null;
    }
    const mismatch = !!confirm.value && group.get(passwordKey)?.value !== confirm.value;
    const errors: ValidationErrors = { ...(confirm.errors ?? {}) };
    delete errors['mismatch'];
    if (mismatch) {
      errors['mismatch'] = true;
    }
    confirm.setErrors(Object.keys(errors).length ? errors : null);
    return null;
  };
}
