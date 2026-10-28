export type CondominiumType = 'RESIDENTIAL' | 'COMMERCIAL' | 'MIXED';
export type CondominiumStatus = 'ACTIVE' | 'INACTIVE';

export const CONDOMINIUM_TYPE_LABELS: Record<CondominiumType, string> = {
  RESIDENTIAL: 'Residencial',
  COMMERCIAL: 'Comercial',
  MIXED: 'Misto',
};

export const CONDOMINIUM_STATUS_LABELS: Record<CondominiumStatus, string> = {
  ACTIVE: 'Ativo',
  INACTIVE: 'Inativo',
};

export const BRAZILIAN_STATES = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const;

export interface Address {
  zipCode?: string | null;
  street?: string | null;
  number?: string | null;
  complement?: string | null;
  district?: string | null;
  city?: string | null;
  state?: string | null;
}

export interface CondominiumSummary {
  id: string;
  name: string;
  cnpj?: string;
  type: CondominiumType;
  city?: string;
  state?: string;
  status: CondominiumStatus;
}

export interface Condominium {
  id: string;
  name: string;
  legalName?: string;
  cnpj?: string;
  type: CondominiumType;
  address?: Address;
  email?: string;
  phone?: string;
  status: CondominiumStatus;
  inactivatedAt?: string;
  inactivationReason?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CondominiumRequest {
  name: string;
  legalName: string | null;
  cnpj: string | null;
  type: CondominiumType;
  address: Address | null;
  email: string | null;
  phone: string | null;
  version?: number;
}

export interface CondominiumQuery {
  search?: string;
  status?: CondominiumStatus | '';
  page: number;
  size: number;
  sort?: string;
}
