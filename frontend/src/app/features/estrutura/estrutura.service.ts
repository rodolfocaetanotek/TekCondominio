import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import {
  Bloco,
  PessoaDetalhe,
  PessoaRequest,
  PessoaResumo,
  ResultadoImportacao,
  ResumoFracoes,
  TipoVinculo,
  Unidade,
  UnidadeRequest,
  Vinculo,
} from './estrutura.models';

@Injectable({ providedIn: 'root' })
export class EstruturaService {
  private readonly http = inject(HttpClient);

  private base(idCondominio: string): string {
    return `/api/v1/condominios/${idCondominio}`;
  }

  blocos(c: string): Observable<Bloco[]> {
    return this.http.get<Bloco[]>(`${this.base(c)}/blocos`);
  }

  salvarBloco(c: string, corpo: { nome: string; descricao: string | null }, id?: string): Observable<Bloco> {
    return id
      ? this.http.put<Bloco>(`${this.base(c)}/blocos/${id}`, corpo)
      : this.http.post<Bloco>(`${this.base(c)}/blocos`, corpo);
  }

  alterarBlocoAtivo(c: string, id: string, ativo: boolean): Observable<Bloco> {
    return this.http.post<Bloco>(`${this.base(c)}/blocos/${id}/${ativo ? 'ativar' : 'desativar'}`, null);
  }

  unidades(c: string, q: { busca?: string; idBloco?: string; ativo?: string; pagina: number; tamanho: number }): Observable<PaginaResponse<Unidade>> {
    let parametros = new HttpParams().set('pagina', q.pagina).set('tamanho', q.tamanho);
    if (q.busca) parametros = parametros.set('busca', q.busca);
    if (q.idBloco) parametros = parametros.set('idBloco', q.idBloco);
    if (q.ativo) parametros = parametros.set('ativo', q.ativo);
    return this.http.get<PaginaResponse<Unidade>>(`${this.base(c)}/unidades`, { params: parametros });
  }

  unidade(c: string, id: string): Observable<Unidade> {
    return this.http.get<Unidade>(`${this.base(c)}/unidades/${id}`);
  }

  salvarUnidade(c: string, corpo: UnidadeRequest, id?: string): Observable<Unidade> {
    return id
      ? this.http.put<Unidade>(`${this.base(c)}/unidades/${id}`, corpo)
      : this.http.post<Unidade>(`${this.base(c)}/unidades`, corpo);
  }

  alterarUnidadeAtiva(c: string, id: string, ativo: boolean): Observable<Unidade> {
    return this.http.post<Unidade>(`${this.base(c)}/unidades/${id}/${ativo ? 'ativar' : 'desativar'}`, null);
  }

  resumoFracoes(c: string): Observable<ResumoFracoes> {
    return this.http.get<ResumoFracoes>(`${this.base(c)}/unidades/resumo-fracoes`);
  }

  importarUnidades(c: string, arquivo: File, simulacao: boolean): Observable<ResultadoImportacao> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<ResultadoImportacao>(`${this.base(c)}/unidades/importar`, form, { params: { simulacao } });
  }

  vinculos(c: string, idUnidade: string): Observable<Vinculo[]> {
    return this.http.get<Vinculo[]>(`${this.base(c)}/unidades/${idUnidade}/vinculos`);
  }

  adicionarVinculo(c: string, corpo: { idUnidade: string; idPessoa: string; tipo: TipoVinculo; responsavelFinanceiro: boolean; dataInicio: string }): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${this.base(c)}/vinculos`, corpo);
  }

  encerrarVinculo(c: string, id: string, dataFim: string, motivo: string | null): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${this.base(c)}/vinculos/${id}/encerrar`, { dataFim, motivo });
  }

  tornarResponsavelFinanceiro(c: string, id: string): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${this.base(c)}/vinculos/${id}/responsavel-financeiro`, null);
  }

  // ---------- pessoas
  pessoas(q: { busca?: string; ativo?: string; idCondominio?: string; pagina: number; tamanho: number }): Observable<PaginaResponse<PessoaResumo>> {
    let parametros = new HttpParams().set('pagina', q.pagina).set('tamanho', q.tamanho);
    if (q.busca) parametros = parametros.set('busca', q.busca);
    if (q.ativo) parametros = parametros.set('ativo', q.ativo);
    if (q.idCondominio) parametros = parametros.set('idCondominio', q.idCondominio);
    return this.http.get<PaginaResponse<PessoaResumo>>('/api/v1/pessoas', { params: parametros });
  }

  pessoa(id: string): Observable<PessoaDetalhe> {
    return this.http.get<PessoaDetalhe>(`/api/v1/pessoas/${id}`);
  }

  salvarPessoa(corpo: PessoaRequest, id?: string): Observable<PessoaDetalhe> {
    return id
      ? this.http.put<PessoaDetalhe>(`/api/v1/pessoas/${id}`, corpo)
      : this.http.post<PessoaDetalhe>('/api/v1/pessoas', corpo);
  }

  alterarPessoaAtiva(id: string, ativo: boolean): Observable<PessoaDetalhe> {
    return this.http.post<PessoaDetalhe>(`/api/v1/pessoas/${id}/${ativo ? 'ativar' : 'desativar'}`, null);
  }

  concederAcessoPortal(id: string): Observable<PessoaDetalhe> {
    return this.http.post<PessoaDetalhe>(`/api/v1/pessoas/${id}/acesso-portal`, null);
  }
}
