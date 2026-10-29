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
export class PortariaApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}/portaria`;
  }

  hoje(c: string): Observable<AutorizacaoVisita[]> { return this.http.get<AutorizacaoVisita[]>(`${this.b(c)}/hoje`); }
  historico(c: string, de: string, ate: string): Observable<AutorizacaoVisita[]> { return this.http.get<AutorizacaoVisita[]>(`${this.b(c)}/historico`, { params: { de, ate } }); }
  chegada(c: string, corpo: Record<string, unknown>): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/chegadas`, corpo); }
  registrarEntradaPorCodigo(c: string, codigo: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/entrada-por-codigo`, { codigo }); }
  registrarEntrada(c: string, id: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/${id}/entrada`, null); }
  registrarSaida(c: string, id: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`${this.b(c)}/${id}/saida`, null); }

  portalListar(c: string): Observable<AutorizacaoVisita[]> { return this.http.get<AutorizacaoVisita[]>(`/api/v1/portal/${c}/visitantes`); }
  portalAutorizar(c: string, corpo: Record<string, unknown>): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`/api/v1/portal/${c}/visitantes`, corpo); }
  portalCancelar(c: string, id: string): Observable<AutorizacaoVisita> { return this.http.post<AutorizacaoVisita>(`/api/v1/portal/${c}/visitantes/${id}/cancelar`, null); }
}
