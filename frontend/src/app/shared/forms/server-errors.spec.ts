import { FormControl, FormGroup } from '@angular/forms';
import { applyServerErrors, errorMessage } from './server-errors';

describe('applyServerErrors', () => {
  it('associa erros da API aos campos, inclusive aninhados', () => {
    const form = new FormGroup({
      cnpj: new FormControl(''),
      address: new FormGroup({ zipCode: new FormControl('') }),
    });

    const rest = applyServerErrors(form, {
      status: 400,
      code: 'VALIDATION_ERROR',
      detail: 'Há campos inválidos.',
      errors: [
        { field: 'cnpj', message: 'CNPJ inválido.' },
        { field: 'address.zipCode', message: 'CEP inválido.' },
        { field: 'inexistente', message: 'Erro geral.' },
      ],
    });

    expect(errorMessage(form.get('cnpj'))).toBe('CNPJ inválido.');
    expect(errorMessage(form.get('address.zipCode'))).toBe('CEP inválido.');
    expect(form.get('cnpj')?.touched).toBe(true);
    expect(rest).toEqual(['Erro geral.']);
  });
});
