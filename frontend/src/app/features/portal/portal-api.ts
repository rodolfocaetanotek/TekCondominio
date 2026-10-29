import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { downloadFile } from '../../shared/download';
import { LinhaDebito } from '../finance/finance-api';
import { Comunicado, DetalheOcorrencia, Documento, ResumoOcorrencia } from '../ops/ops-api';

export interface PerfilPortal { nome: string; email: string; condominios: { id: string; nome: string; unidades: { idUnidade: string; rotulo: string; tipo: string; responsavelFinanceiro: boolean; veCobrancas: boolean }[] }[]; }
export interface CobrancasUnidade { idUnidade: string; rotuloUnidade: string; totalAberto: number; linhas: LinhaDebito[]; }
export interface AssembleiaPortal { id: string; titulo: string; tipo: string; primeiraConvocacaoEm: string; local: string; situacao: string; ata?: string; }
export interface EventoCalendario { data: string; tipo: string; titulo: string; }

@Injectable({ providedIn: 'root' })
export class PortalApi {
  private readonly http = inject(HttpClient);
  me(): Observable<PerfilPortal> { return this.http.get<PerfilPortal>('/api/v1/portal/perfil'); }
  charges(c: string): Observable<CobrancasUnidade[]> { return this.http.get<CobrancasUnidade[]>(`/api/v1/portal/${c}/cobrancas`); }
  announcements(c: string): Observable<Comunicado[]> { return this.http.get<Comunicado[]>(`/api/v1/portal/${c}/comunicados`); }
  read(c: string, id: string): Observable<void> { return this.http.post<void>(`/api/v1/portal/${c}/comunicados/${id}/marcar-lido`, null); }
  documents(c: string): Observable<Documento[]> { return this.http.get<Documento[]>(`/api/v1/portal/${c}/documentos`); }
  download(c: string, id: string): Observable<void> { return downloadFile(this.http, `/api/v1/portal/${c}/documentos/${id}/baixar`); }
  occurrences(c: string): Observable<ResumoOcorrencia[]> { return this.http.get<ResumoOcorrencia[]>(`/api/v1/portal/${c}/ocorrencias`); }
  occurrence(c: string, id: string): Observable<DetalheOcorrencia> { return this.http.get<DetalheOcorrencia>(`/api/v1/portal/${c}/ocorrencias/${id}`); }
  openOccurrence(c: string, body: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`/api/v1/portal/${c}/ocorrencias`, body); }
  comment(c: string, id: string, mensagem: string): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`/api/v1/portal/${c}/ocorrencias/${id}/interacoes`, { mensagem, interna: false }); }
  assemblies(c: string): Observable<AssembleiaPortal[]> { return this.http.get<AssembleiaPortal[]>(`/api/v1/portal/${c}/assembleias`); }
  calendar(c: string): Observable<EventoCalendario[]> { return this.http.get<EventoCalendario[]>(`/api/v1/portal/${c}/calendario`); }
}
