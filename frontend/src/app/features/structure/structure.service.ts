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
} from './structure.models';

@Injectable({ providedIn: 'root' })
export class StructureService {
  private readonly http = inject(HttpClient);

  private base(idCondominio: string): string {
    return `/api/v1/condominios/${idCondominio}`;
  }

  buildings(c: string): Observable<Bloco[]> {
    return this.http.get<Bloco[]>(`${this.base(c)}/blocos`);
  }

  saveBuilding(c: string, body: { nome: string; descricao: string | null }, id?: string): Observable<Bloco> {
    return id
      ? this.http.put<Bloco>(`${this.base(c)}/blocos/${id}`, body)
      : this.http.post<Bloco>(`${this.base(c)}/blocos`, body);
  }

  setBuildingActive(c: string, id: string, active: boolean): Observable<Bloco> {
    return this.http.post<Bloco>(`${this.base(c)}/blocos/${id}/${active ? 'ativar' : 'desativar'}`, null);
  }

  units(c: string, q: { search?: string; buildingId?: string; active?: string; page: number; size: number }): Observable<PaginaResponse<Unidade>> {
    let params = new HttpParams().set('pagina', q.page).set('tamanho', q.size);
    if (q.search) params = params.set('busca', q.search);
    if (q.buildingId) params = params.set('idBloco', q.buildingId);
    if (q.active) params = params.set('ativo', q.active);
    return this.http.get<PaginaResponse<Unidade>>(`${this.base(c)}/unidades`, { params });
  }

  unit(c: string, id: string): Observable<Unidade> {
    return this.http.get<Unidade>(`${this.base(c)}/unidades/${id}`);
  }

  saveUnit(c: string, body: UnidadeRequest, id?: string): Observable<Unidade> {
    return id
      ? this.http.put<Unidade>(`${this.base(c)}/unidades/${id}`, body)
      : this.http.post<Unidade>(`${this.base(c)}/unidades`, body);
  }

  setUnitActive(c: string, id: string, active: boolean): Observable<Unidade> {
    return this.http.post<Unidade>(`${this.base(c)}/unidades/${id}/${active ? 'ativar' : 'desativar'}`, null);
  }

  fractionSummary(c: string): Observable<ResumoFracoes> {
    return this.http.get<ResumoFracoes>(`${this.base(c)}/unidades/resumo-fracoes`);
  }

  importUnits(c: string, file: File, simulacao: boolean): Observable<ResultadoImportacao> {
    const form = new FormData();
    form.append('arquivo', file);
    return this.http.post<ResultadoImportacao>(`${this.base(c)}/unidades/importar`, form, { params: { simulacao } });
  }

  occupancies(c: string, idUnidade: string): Observable<Vinculo[]> {
    return this.http.get<Vinculo[]>(`${this.base(c)}/unidades/${idUnidade}/vinculos`);
  }

  addOccupancy(c: string, body: { idUnidade: string; idPessoa: string; tipo: TipoVinculo; responsavelFinanceiro: boolean; dataInicio: string }): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${this.base(c)}/vinculos`, body);
  }

  endOccupancy(c: string, id: string, dataFim: string, motivo: string | null): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${this.base(c)}/vinculos/${id}/encerrar`, { dataFim, motivo });
  }

  makeFinancialResponsible(c: string, id: string): Observable<Vinculo> {
    return this.http.post<Vinculo>(`${this.base(c)}/vinculos/${id}/responsavel-financeiro`, null);
  }

  // ---------- pessoas
  people(q: { search?: string; active?: string; condominiumId?: string; page: number; size: number }): Observable<PaginaResponse<PessoaResumo>> {
    let params = new HttpParams().set('pagina', q.page).set('tamanho', q.size);
    if (q.search) params = params.set('busca', q.search);
    if (q.active) params = params.set('ativo', q.active);
    if (q.condominiumId) params = params.set('idCondominio', q.condominiumId);
    return this.http.get<PaginaResponse<PessoaResumo>>('/api/v1/pessoas', { params });
  }

  person(id: string): Observable<PessoaDetalhe> {
    return this.http.get<PessoaDetalhe>(`/api/v1/pessoas/${id}`);
  }

  savePerson(body: PessoaRequest, id?: string): Observable<PessoaDetalhe> {
    return id
      ? this.http.put<PessoaDetalhe>(`/api/v1/pessoas/${id}`, body)
      : this.http.post<PessoaDetalhe>('/api/v1/pessoas', body);
  }

  setPersonActive(id: string, active: boolean): Observable<PessoaDetalhe> {
    return this.http.post<PessoaDetalhe>(`/api/v1/pessoas/${id}/${active ? 'ativar' : 'desativar'}`, null);
  }

  grantPortalAccess(id: string): Observable<PessoaDetalhe> {
    return this.http.post<PessoaDetalhe>(`/api/v1/pessoas/${id}/acesso-portal`, null);
  }
}
