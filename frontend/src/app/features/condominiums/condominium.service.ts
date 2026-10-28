import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../../core/api/api.models';
import { Condominium, CondominiumQuery, CondominiumRequest, CondominiumSummary } from './condominium.models';

const BASE = '/api/v1/condominiums';

@Injectable({ providedIn: 'root' })
export class CondominiumService {
  private readonly http = inject(HttpClient);

  list(query: CondominiumQuery): Observable<PageResponse<CondominiumSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.search?.trim()) params = params.set('search', query.search.trim());
    if (query.status) params = params.set('status', query.status);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<PageResponse<CondominiumSummary>>(BASE, { params });
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
