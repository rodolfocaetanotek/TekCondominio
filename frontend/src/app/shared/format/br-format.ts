/**
 * Formatação de exibição/digitação para documentos brasileiros. Apenas apresentação:
 * a validação de conteúdo (dígitos verificadores etc.) é responsabilidade do backend.
 */

/** CNPJ numérico ou alfanumérico (IN RFB 2.229/2024): 12 posições [0-9A-Z] + 2 dígitos. */
export function formatCnpj(value: string | null | undefined): string {
  const raw = (value ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, 14);
  const parts = [raw.slice(0, 2), raw.slice(2, 5), raw.slice(5, 8), raw.slice(8, 12), raw.slice(12, 14)];
  let out = parts[0];
  if (parts[1]) out += '.' + parts[1];
  if (parts[2]) out += '.' + parts[2];
  if (parts[3]) out += '/' + parts[3];
  if (parts[4]) out += '-' + parts[4];
  return out;
}

export function formatZipCode(value: string | null | undefined): string {
  const digits = (value ?? '').replace(/\D/g, '').slice(0, 8);
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits;
}

export function formatPhone(value: string | null | undefined): string {
  const digits = (value ?? '').replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 2) {
    return digits.length ? `(${digits}` : '';
  }
  const ddd = digits.slice(0, 2);
  const rest = digits.slice(2);
  if (rest.length <= 4) {
    return `(${ddd}) ${rest}`;
  }
  const split = rest.length === 9 ? 5 : 4;
  return `(${ddd}) ${rest.slice(0, split)}-${rest.slice(split)}`;
}

/** CPF: 000.000.000-00 */
export function formatCpf(value: string | null | undefined): string {
  const d = (value ?? '').replace(/\D/g, '').slice(0, 11);
  let out = d.slice(0, 3);
  if (d.length > 3) out += '.' + d.slice(3, 6);
  if (d.length > 6) out += '.' + d.slice(6, 9);
  if (d.length > 9) out += '-' + d.slice(9, 11);
  return out;
}

/** CPF ou CNPJ conforme o tamanho digitado (até 11 dígitos numéricos = CPF). */
export function formatCpfCnpj(value: string | null | undefined): string {
  const raw = (value ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '');
  return raw.length <= 11 && /^\d*$/.test(raw) ? formatCpf(raw) : formatCnpj(raw);
}

export type MaskKind = 'cnpj' | 'cep' | 'phone' | 'cpf' | 'cpfCnpj';

export const FORMATTERS: Record<MaskKind, (v: string | null | undefined) => string> = {
  cnpj: formatCnpj,
  cep: formatZipCode,
  phone: formatPhone,
  cpf: formatCpf,
  cpfCnpj: formatCpfCnpj,
};
