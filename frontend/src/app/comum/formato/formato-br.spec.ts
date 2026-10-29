import { formatarCnpj, formatarCpf, formatarCpfCnpj, formatarTelefone, formatarCep } from './formato-br';

describe('formato-br', () => {
  it('formata CNPJ numérico progressivamente', () => {
    expect(formatarCnpj('11')).toBe('11');
    expect(formatarCnpj('11222')).toBe('11.222');
    expect(formatarCnpj('11222333000181')).toBe('11.222.333/0001-81');
  });

  it('formata CNPJ alfanumérico em maiúsculas e descarta excesso', () => {
    expect(formatarCnpj('12abc34501de35999')).toBe('12.ABC.345/01DE-35');
  });

  it('formata CPF e escolhe CPF/CNPJ pelo tamanho', () => {
    expect(formatarCpf('52998224725')).toBe('529.982.247-25');
    expect(formatarCpfCnpj('529982')).toBe('529.982');
    expect(formatarCpfCnpj('11222333000181')).toBe('11.222.333/0001-81');
    expect(formatarCpfCnpj('12abc')).toBe('12.ABC');
  });

  it('formata CEP', () => {
    expect(formatarCep('66010000')).toBe('66010-000');
    expect(formatarCep('660')).toBe('660');
  });

  it('formata telefone fixo e celular', () => {
    expect(formatarTelefone('9132221234')).toBe('(91) 3222-1234');
    expect(formatarTelefone('91987651234')).toBe('(91) 98765-1234');
    expect(formatarTelefone('')).toBe('');
  });
});
