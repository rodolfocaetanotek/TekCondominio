import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { Condominium, CondominiumQuery, CondominiumRequest, CondominiumSummary } from './condominium.models';

const BASE = '/api/v1/condominiums';

@Injectable({ providedIn: 'root' })
export class CondominiumService {
  private readonly http = inject(HttpClient);

  list(query: CondominiumQuery): Observable<PaginaResponse<CondominiumSummary>> {
    let params = new HttpParams().set('pagina', query.page).set('tamanho', query.size);
    if (query.search?.trim()) params = params.set('search', query.search.trim());
    if (query.status) params = params.set('status', query.status);
    if (query.sort) params = params.set('ordem', query.sort);
    return this.http.get<PaginaResponse<CondominiumSummary>>(BASE, { params });
  }

  get(id: string): Observable<Condominium> {
    return this.http.get<Condominium>(`${BASE}/${id}`);
  }

  create(body: CondominiumRequest): Observable<Condominium> {
    return this.http.post<Condominium>(BASE, body);
  }

  update(id: string, body: CondominiumRequest): Observable<Condominium> {
    return this.http.put<Condominium>(`${BASE}/${id}`, body);
  }

  inactivate(id: string, reason: string): Observable<Condominium> {
    return this.http.post<Condominium>(`${BASE}/${id}/inactivate`, { reason });
  }

  activate(id: string): Observable<Condominium> {
    return this.http.post<Condominium>(`${BASE}/${id}/activate`, null);
  }
}
