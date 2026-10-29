import { FormControl, FormGroup, Validators } from '@angular/forms';
import { mensagemErro } from '../../comum/formularios/erros-servidor';
import { senhasIguais } from './senhas-iguais';

describe('senhasIguais', () => {
  function form() {
    return new FormGroup(
      {
        novaSenha: new FormControl('', { nonNullable: true }),
        confirmar: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      },
      { validators: senhasIguais('novaSenha', 'confirmar') },
    );
  }

  it('marca divergência e a remove quando as senhas coincidem', () => {
    const f = form();
    f.setValue({ novaSenha: 'SenhaForte2026', confirmar: 'Outra' });
    expect(mensagemErro(f.controls.confirmar)).toBe('As senhas não conferem.');

    f.controls.confirmar.setValue('SenhaForte2026');
    expect(f.controls.confirmar.errors).toBeNull();
    expect(f.valid).toBe(true);
  });

  it('preserva outros erros do campo de confirmação', () => {
    const f = form();
    f.setValue({ novaSenha: 'SenhaForte2026', confirmar: '' });
    expect(f.controls.confirmar.hasError('required')).toBe(true);
    expect(f.controls.confirmar.hasError('mismatch')).toBe(false);
  });
});
