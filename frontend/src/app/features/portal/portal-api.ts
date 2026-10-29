import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { baixarArquivo } from '../../comum/baixar-arquivo';
import { LinhaDebito } from '../financeiro/financeiro-api';
import { Comunicado, DetalheOcorrencia, Documento, ResumoOcorrencia } from '../operacao/operacao-api';

export interface PerfilPortal { nome: string; email: string; condominios: { id: string; nome: string; unidades: { idUnidade: string; rotulo: string; tipo: string; responsavelFinanceiro: boolean; veCobrancas: boolean }[] }[]; }
export interface CobrancasUnidade { idUnidade: string; rotuloUnidade: string; totalAberto: number; linhas: LinhaDebito[]; }
export interface AssembleiaPortal { id: string; titulo: string; tipo: string; primeiraConvocacaoEm: string; local: string; situacao: string; ata?: string; }
export interface EventoCalendario { data: string; tipo: string; titulo: string; }

@Injectable({ providedIn: 'root' })
export class PortalApi {
  private readonly http = inject(HttpClient);
  perfil(): Observable<PerfilPortal> { return this.http.get<PerfilPortal>('/api/v1/portal/perfil'); }
  encargos(c: string): Observable<CobrancasUnidade[]> { return this.http.get<CobrancasUnidade[]>(`/api/v1/portal/${c}/cobrancas`); }
  comunicados(c: string): Observable<Comunicado[]> { return this.http.get<Comunicado[]>(`/api/v1/portal/${c}/comunicados`); }
  ler(c: string, id: string): Observable<void> { return this.http.post<void>(`/api/v1/portal/${c}/comunicados/${id}/marcar-lido`, null); }
  documentos(c: string): Observable<Documento[]> { return this.http.get<Documento[]>(`/api/v1/portal/${c}/documentos`); }
  baixar(c: string, id: string): Observable<void> { return baixarArquivo(this.http, `/api/v1/portal/${c}/documentos/${id}/baixar`); }
  ocorrencias(c: string): Observable<ResumoOcorrencia[]> { return this.http.get<ResumoOcorrencia[]>(`/api/v1/portal/${c}/ocorrencias`); }
  ocorrencia(c: string, id: string): Observable<DetalheOcorrencia> { return this.http.get<DetalheOcorrencia>(`/api/v1/portal/${c}/ocorrencias/${id}`); }
  abrirOcorrencia(c: string, corpo: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`/api/v1/portal/${c}/ocorrencias`, corpo); }
  comentar(c: string, id: string, mensagem: string): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`/api/v1/portal/${c}/ocorrencias/${id}/interacoes`, { mensagem, interna: false }); }
  assembleias(c: string): Observable<AssembleiaPortal[]> { return this.http.get<AssembleiaPortal[]>(`/api/v1/portal/${c}/assembleias`); }
  calendario(c: string): Observable<EventoCalendario[]> { return this.http.get<EventoCalendario[]>(`/api/v1/portal/${c}/calendario`); }
}
