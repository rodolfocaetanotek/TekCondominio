import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { Condominio, CondominiumQuery, CondominioRequest, CondominioResumo } from './condominium.models';

const BASE = '/api/v1/condominios';

@Injectable({ providedIn: 'root' })
export class CondominiumService {
  private readonly http = inject(HttpClient);

  list(query: CondominiumQuery): Observable<PaginaResponse<CondominioResumo>> {
    let params = new HttpParams().set('pagina', query.page).set('tamanho', query.size);
    if (query.search?.trim()) params = params.set('busca', query.search.trim());
    if (query.status) params = params.set('situacao', query.status);
    if (query.sort) params = params.set('ordem', query.sort);
    return this.http.get<PaginaResponse<CondominioResumo>>(BASE, { params });
  }

  get(id: string): Observable<Condominio> {
    return this.http.get<Condominio>(`${BASE}/${id}`);
  }

  create(body: CondominioRequest): Observable<Condominio> {
    return this.http.post<Condominio>(BASE, body);
  }

  update(id: string, body: CondominioRequest): Observable<Condominio> {
    return this.http.put<Condominio>(`${BASE}/${id}`, body);
  }

  inactivate(id: string, motivo: string): Observable<Condominio> {
    return this.http.post<Condominio>(`${BASE}/${id}/inativar`, { motivo });
  }

  activate(id: string): Observable<Condominio> {
    return this.http.post<Condominio>(`${BASE}/${id}/ativar`, null);
  }
}
