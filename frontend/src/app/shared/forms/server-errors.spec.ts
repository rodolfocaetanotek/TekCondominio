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
      codigo: 'VALIDATION_ERROR',
      detail: 'Há campos inválidos.',
      erros: [
        { campo: 'cnpj', mensagem: 'CNPJ inválido.' },
        { campo: 'address.zipCode', mensagem: 'CEP inválido.' },
        { campo: 'inexistente', mensagem: 'Erro geral.' },
      ],
    });

    expect(errorMessage(form.get('cnpj'))).toBe('CNPJ inválido.');
    expect(errorMessage(form.get('address.zipCode'))).toBe('CEP inválido.');
    expect(form.get('cnpj')?.touched).toBe(true);
    expect(rest).toEqual(['Erro geral.']);
  });
});
