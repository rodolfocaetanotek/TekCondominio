import { FormControl, FormGroup, Validators } from '@angular/forms';
import { errorMessage } from '../../shared/forms/server-errors';
import { passwordsMatch } from './password-match';

describe('passwordsMatch', () => {
  function form() {
    return new FormGroup(
      {
        newPassword: new FormControl('', { nonNullable: true }),
        confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      },
      { validators: passwordsMatch('newPassword', 'confirm') },
    );
  }

  it('marca divergência e a remove quando as senhas coincidem', () => {
    const f = form();
    f.setValue({ newPassword: 'SenhaForte2026', confirm: 'Outra' });
    expect(errorMessage(f.controls.confirm)).toBe('As senhas não conferem.');

    f.controls.confirm.setValue('SenhaForte2026');
    expect(f.controls.confirm.errors).toBeNull();
    expect(f.valid).toBe(true);
  });

  it('preserva outros erros do campo de confirmação', () => {
    const f = form();
    f.setValue({ newPassword: 'SenhaForte2026', confirm: '' });
    expect(f.controls.confirm.hasError('required')).toBe(true);
    expect(f.controls.confirm.hasError('mismatch')).toBe(false);
  });
});
