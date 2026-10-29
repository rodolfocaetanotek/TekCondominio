import { FormControl, FormGroup } from '@angular/forms';
import { aplicarErrosServidor, mensagemErro } from './erros-servidor';

describe('aplicarErrosServidor', () => {
  it('associa erros da API aos campos, inclusive aninhados', () => {
    const form = new FormGroup({
      cnpj: new FormControl(''),
      address: new FormGroup({ zipCode: new FormControl('') }),
    });

    const restante = aplicarErrosServidor(form, {
      status: 400,
      codigo: 'VALIDATION_ERROR',
      detail: 'Há campos inválidos.',
      erros: [
        { campo: 'cnpj', mensagem: 'CNPJ inválido.' },
        { campo: 'address.zipCode', mensagem: 'CEP inválido.' },
        { campo: 'inexistente', mensagem: 'Erro geral.' },
      ],
    });

    expect(mensagemErro(form.get('cnpj'))).toBe('CNPJ inválido.');
    expect(mensagemErro(form.get('address.zipCode'))).toBe('CEP inválido.');
    expect(form.get('cnpj')?.touched).toBe(true);
    expect(restante).toEqual(['Erro geral.']);
  });
});
