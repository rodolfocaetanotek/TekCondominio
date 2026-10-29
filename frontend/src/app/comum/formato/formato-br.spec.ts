import { formatCnpj, formatCpf, formatCpfCnpj, formatPhone, formatZipCode } from './formato-br';

describe('br-format', () => {
  it('formata CNPJ numérico progressivamente', () => {
    expect(formatCnpj('11')).toBe('11');
    expect(formatCnpj('11222')).toBe('11.222');
    expect(formatCnpj('11222333000181')).toBe('11.222.333/0001-81');
  });

  it('formata CNPJ alfanumérico em maiúsculas e descarta excesso', () => {
    expect(formatCnpj('12abc34501de35999')).toBe('12.ABC.345/01DE-35');
  });

  it('formata CPF e escolhe CPF/CNPJ pelo tamanho', () => {
    expect(formatCpf('52998224725')).toBe('529.982.247-25');
    expect(formatCpfCnpj('529982')).toBe('529.982');
    expect(formatCpfCnpj('11222333000181')).toBe('11.222.333/0001-81');
    expect(formatCpfCnpj('12abc')).toBe('12.ABC');
  });

  it('formata CEP', () => {
    expect(formatZipCode('66010000')).toBe('66010-000');
    expect(formatZipCode('660')).toBe('660');
  });

  it('formata telefone fixo e celular', () => {
    expect(formatPhone('9132221234')).toBe('(91) 3222-1234');
    expect(formatPhone('91987651234')).toBe('(91) 98765-1234');
    expect(formatPhone('')).toBe('');
  });
});
