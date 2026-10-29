export type TipoUnidade = 'APARTMENT' | 'HOUSE' | 'ROOM' | 'STORE' | 'PARKING' | 'OTHER';

export const ROTULOS_TIPO_UNIDADE: Record<TipoUnidade, string> = {
  APARTMENT: 'Apartamento',
  HOUSE: 'Casa',
  ROOM: 'Sala',
  STORE: 'Loja',
  PARKING: 'Vaga',
  OTHER: 'Outro',
};

export interface Bloco {
  id: string;
  nome: string;
  descricao?: string;
  ativo: boolean;
  unidadesAtivas: number;
}

export interface Unidade {
  id: string;
  idBloco?: string;
  nomeBloco?: string;
  identificacao: string;
  rotulo: string;
  tipo: TipoUnidade;
  andar?: string;
  area?: number;
  fracaoIdeal: number;
  observacoes?: string;
  ativo: boolean;
  versao: number;
  nomeProprietario?: string;
  nomeResponsavelFinanceiro?: string;
}

export interface UnidadeRequest {
  idBloco: string | null;
  identificacao: string;
  tipo: TipoUnidade;
  andar: string | null;
  area: number | null;
  fracaoIdeal: number | null;
  observacoes: string | null;
  versao?: number;
}

export interface ResumoFracoes {
  total: number;
  unidadesAtivas: number;
  fechada: boolean;
  diferenca: number;
}

export interface ResultadoImportacao {
  simulacao: boolean;
  linhas: number;
  blocosCriados: number;
  unidadesCriadas: number;
  pessoasCriadas: number;
  pessoasReaproveitadas: number;
  erros: { linha: number; mensagem: string }[];
}

export type TipoVinculo = 'OWNER' | 'TENANT' | 'RESIDENT';

export const ROTULOS_TIPO_VINCULO: Record<TipoVinculo, string> = {
  OWNER: 'Proprietário',
  TENANT: 'Inquilino',
  RESIDENT: 'Morador',
};

export interface Vinculo {
  id: string;
  idCondominio: string;
  idUnidade: string;
  rotuloUnidade: string;
  idPessoa: string;
  nomePessoa: string;
  tipo: TipoVinculo;
  responsavelFinanceiro: boolean;
  dataInicio: string;
  dataFim?: string;
  motivoEncerramento?: string;
  vigente: boolean;
}

export type TipoPessoa = 'INDIVIDUAL' | 'COMPANY';

export interface PessoaResumo {
  id: string;
  nome: string;
  tipo: TipoPessoa;
  documento?: string;
  email?: string;
  telefone?: string;
  ativo: boolean;
  unidades: string[];
}

export interface PessoaDetalhe {
  id: string;
  nome: string;
  tipo: TipoPessoa;
  documento?: string;
  email?: string;
  telefone?: string;
  telefoneSecundario?: string;
  observacoes?: string;
  ativo: boolean;
  versao: number;
  usuarioPortal: boolean;
  vinculos: Vinculo[];
}

export interface PessoaRequest {
  nome: string;
  tipo: TipoPessoa;
  documento: string | null;
  email: string | null;
  telefone: string | null;
  telefoneSecundario: string | null;
  observacoes: string | null;
  versao?: number;
}
