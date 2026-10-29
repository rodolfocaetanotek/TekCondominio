/**
 * Formatação de exibição/digitação para documentos brasileiros. Apenas apresentação:
 * a validação de conteúdo (dígitos verificadores etc.) é responsabilidade do backend.
 */

/** CNPJ numérico ou alfanumérico (IN RFB 2.229/2024): 12 posições [0-9A-Z] + 2 dígitos. */
export function formatarCnpj(valor: string | null | undefined): string {
  const bruto = (valor ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, 14);
  const partes = [bruto.slice(0, 2), bruto.slice(2, 5), bruto.slice(5, 8), bruto.slice(8, 12), bruto.slice(12, 14)];
  let saida = partes[0];
  if (partes[1]) saida += '.' + partes[1];
  if (partes[2]) saida += '.' + partes[2];
  if (partes[3]) saida += '/' + partes[3];
  if (partes[4]) saida += '-' + partes[4];
  return saida;
}

export function formatarCep(valor: string | null | undefined): string {
  const digitos = (valor ?? '').replace(/\D/g, '').slice(0, 8);
  return digitos.length > 5 ? `${digitos.slice(0, 5)}-${digitos.slice(5)}` : digitos;
}

export function formatarTelefone(valor: string | null | undefined): string {
  const digitos = (valor ?? '').replace(/\D/g, '').slice(0, 11);
  if (digitos.length <= 2) {
    return digitos.length ? `(${digitos}` : '';
  }
  const ddd = digitos.slice(0, 2);
  const restante = digitos.slice(2);
  if (restante.length <= 4) {
    return `(${ddd}) ${restante}`;
  }
  const corte = restante.length === 9 ? 5 : 4;
  return `(${ddd}) ${restante.slice(0, corte)}-${restante.slice(corte)}`;
}

/** CPF: 000.000.000-00 */
export function formatarCpf(valor: string | null | undefined): string {
  const d = (valor ?? '').replace(/\D/g, '').slice(0, 11);
  let saida = d.slice(0, 3);
  if (d.length > 3) saida += '.' + d.slice(3, 6);
  if (d.length > 6) saida += '.' + d.slice(6, 9);
  if (d.length > 9) saida += '-' + d.slice(9, 11);
  return saida;
}

/** CPF ou CNPJ conforme o tamanho digitado (até 11 dígitos numéricos = CPF). */
export function formatarCpfCnpj(valor: string | null | undefined): string {
  const bruto = (valor ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '');
  return bruto.length <= 11 && /^\d*$/.test(bruto) ? formatarCpf(bruto) : formatarCnpj(bruto);
}

export type TipoMascara = 'cnpj' | 'cep' | 'telefone' | 'cpf' | 'cpfCnpj';

export const FORMATADORES: Record<TipoMascara, (v: string | null | undefined) => string> = {
  cnpj: formatarCnpj,
  cep: formatarCep,
  telefone: formatarTelefone,
  cpf: formatarCpf,
  cpfCnpj: formatarCpfCnpj,
};
