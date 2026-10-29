import { Permissao } from '../../core/auth/auth.models';

export type EscopoPapel = 'ORGANIZATION' | 'CONDOMINIUM';

export interface PapelOpcao {
  codigo: string;
  nome: string;
  descricao?: string;
  escopo: EscopoPapel;
  permissoes: Permissao[];
}

export interface Atribuicao {
  id: string;
  codigoPapel: string;
  nomePapel: string;
  escopo: EscopoPapel;
  idCondominio?: string;
  nomeCondominio?: string;
}

export interface UsuarioResumo {
  id: string;
  nome: string;
  email: string;
  ativo: boolean;
  convitePendente: boolean;
  ultimoLoginEm?: string;
  papeis: string[];
}

export interface UsuarioDetalhe {
  id: string;
  nome: string;
  email: string;
  ativo: boolean;
  convitePendente: boolean;
  ultimoLoginEm?: string;
  criadoEm: string;
  versao: number;
  atribuicoes: Atribuicao[];
}

export interface AtribuicaoRequest {
  codigoPapel: string;
  idCondominio: string | null;
}

export interface ConsultaUsuario {
  busca?: string;
  ativo?: '' | 'true' | 'false';
  pagina: number;
  tamanho: number;
}
