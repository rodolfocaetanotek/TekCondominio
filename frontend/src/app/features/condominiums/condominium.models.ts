export type TipoCondominio = 'RESIDENTIAL' | 'COMMERCIAL' | 'MIXED';
export type SituacaoCondominio = 'ACTIVE' | 'INACTIVE';

export const CONDOMINIUM_TYPE_LABELS: Record<TipoCondominio, string> = {
  RESIDENTIAL: 'Residencial',
  COMMERCIAL: 'Comercial',
  MIXED: 'Misto',
};

export const CONDOMINIUM_STATUS_LABELS: Record<SituacaoCondominio, string> = {
  ACTIVE: 'Ativo',
  INACTIVE: 'Inativo',
};

export const BRAZILIAN_STATES = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const;

export interface Endereco {
  cep?: string | null;
  logradouro?: string | null;
  numero?: string | null;
  complemento?: string | null;
  bairro?: string | null;
  cidade?: string | null;
  uf?: string | null;
}

export interface CondominioResumo {
  id: string;
  nome: string;
  cnpj?: string;
  tipo: TipoCondominio;
  cidade?: string;
  uf?: string;
  situacao: SituacaoCondominio;
}

export interface Condominio {
  id: string;
  nome: string;
  razaoSocial?: string;
  cnpj?: string;
  tipo: TipoCondominio;
  endereco?: Endereco;
  email?: string;
  telefone?: string;
  situacao: SituacaoCondominio;
  inativadoEm?: string;
  motivoInativacao?: string;
  criadoEm: string;
  atualizadoEm: string;
  versao: number;
}

export interface CondominioRequest {
  nome: string;
  razaoSocial: string | null;
  cnpj: string | null;
  tipo: TipoCondominio;
  endereco: Endereco | null;
  email: string | null;
  telefone: string | null;
  versao?: number;
}

export interface CondominiumQuery {
  search?: string;
  status?: SituacaoCondominio | '';
  page: number;
  size: number;
  sort?: string;
}
