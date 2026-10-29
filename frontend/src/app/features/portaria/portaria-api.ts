import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface AutorizacaoVisita {
  id: string; idUnidade: string; rotuloUnidade: string; tipo: 'VISITOR' | 'SERVICE_PROVIDER' | 'DELIVERY'; nome: string; documento?: string;
  empresa?: string; placaVeiculo?: string; validaDe: string; validaAte: string; codigoAcesso?: string;
  situacao: 'EXPECTED' | 'INSIDE' | 'LEFT' | 'CANCELLED'; peloMorador: boolean; nomeAutorizadoPor: string; entradaEm?: string;
  saidaEm?: string; observacoes?: string;
}

/** Portaria (administração/porteiro) e autorizações do morador (portal). */
@Injectable({ providedIn: 'root' })
export class VisitorApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}/portaria`;
  }

  today(c: string): Observable<AutorizacaoVisita[]> { return this.http.get<AutorizacaoVisita[]>(`${this.b(c)}/hoje`); }
  history(c: string, de: string, ate: string): Observable<AutorizacaoVisita[]> { return this.http.get<AutorizacaoVisita[]>(`${this.b(c)}/historico`, { params: { de, ate } }); }
  arrival(c: string, body: Record<string, unknown>): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/chegadas`, body); }
  checkInByCode(c: string, codigo: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/entrada-por-codigo`, { codigo }); }
  checkIn(c: string, id: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/${id}/entrada`, null); }
  checkOut(c: string, id: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/${id}/saida`, null); }

  portalList(c: string): Observable<AutorizacaoVisita[]> { return this.http.get<AutorizacaoVisita[]>(`/api/v1/portal/${c}/visitantes`); }
  portalAuthorize(c: string, body: Record<string, unknown>): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`/api/v1/portal/${c}/visitantes`, body); }
  portalCancel(c: string, id: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`/api/v1/portal/${c}/visitantes/${id}/cancelar`, null); }
}
