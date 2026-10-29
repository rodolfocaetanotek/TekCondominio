import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface AreaComum {
  id: string; nome: string; descricao?: string; regras?: string; capacidade?: number; taxa: number; exigeAprovacao: boolean;
  bloqueiaInadimplente: boolean; horaAbertura: string; horaFechamento: string; duracaoMaximaMinutos: number; antecedenciaMinimaHoras: number;
  antecedenciaMaximaDias: number; limiteMensalPorUnidade?: number; ativa: boolean; versao: number;
}
export interface Reserva {
  id: string; idArea: string; nomeArea: string; idUnidade: string; rotuloUnidade?: string; data: string; horaInicio: string; horaFim: string;
  convidados?: number; observacoes?: string; situacao: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'; taxa: number; idLancamento?: string;
  nomeSolicitante: string; peloMorador: boolean; observacaoDecisao?: string; motivoCancelamento?: string; criadoEm: string;
}
export interface HorarioOcupado { idArea: string; data: string; horaInicio: string; horaFim: string; situacao: string; }

/** Reservas de áreas comuns: administração (/condominios) e portal do morador (/portal). */
@Injectable({ providedIn: 'root' })
export class ReservaApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}`;
  }

  areas(c: string): Observable<AreaComum[]> { return this.http.get<AreaComum[]>(`${this.b(c)}/areas-comuns`); }
  salvarArea(c: string, corpo: Record<string, unknown>, id?: string): Observable<AreaComum> {
    return id ? this.http.put<AreaComum>(`${this.b(c)}/areas-comuns/${id}`, corpo) : this.http.post<AreaComum>(`${this.b(c)}/areas-comuns`, corpo);
  }
  listar(c: string, de: string, ate: string, idArea?: string): Observable<Reserva[]> {
    return this.http.get<Reserva[]>(`${this.b(c)}/reservas`, { params: { de, ate, ...(idArea ? { idArea } : {}) } });
  }
  cadastrar(c: string, corpo: Record<string, unknown>): Observable<Reserva> { return this.http.post<Reserva>(`${this.b(c)}/reservas`, corpo); }
  aprovar(c: string, id: string, observacao: string | null): Observable<Reserva> { return this.http.post<Reserva>(`${this.b(c)}/reservas/${id}/aprovar`, { observacao }); }
  reprovar(c: string, id: string, observacao: string): Observable<Reserva> { return this.http.post<Reserva>(`${this.b(c)}/reservas/${id}/recusar`, { observacao }); }
  cancelar(c: string, id: string, motivo: string): Observable<Reserva> { return this.http.post<Reserva>(`${this.b(c)}/reservas/${id}/cancelar`, { motivo }); }

  portalAreas(c: string): Observable<AreaComum[]> { return this.http.get<AreaComum[]>(`/api/v1/portal/${c}/areas-comuns`); }
  portalOcupacao(c: string, idArea: string, de: string, ate: string): Observable<HorarioOcupado[]> {
    return this.http.get<HorarioOcupado[]>(`/api/v1/portal/${c}/areas-comuns/${idArea}/ocupacao`, { params: { de, ate } });
  }
  portalMinhas(c: string): Observable<Reserva[]> { return this.http.get<Reserva[]>(`/api/v1/portal/${c}/reservas`); }
  portalReservar(c: string, corpo: Record<string, unknown>): Observable<Reserva> { return this.http.post<Reserva>(`/api/v1/portal/${c}/reservas`, corpo); }
  portalCancelar(c: string, id: string, motivo: string): Observable<Reserva> { return this.http.post<Reserva>(`/api/v1/portal/${c}/reservas/${id}/cancelar`, { motivo }); }
}
