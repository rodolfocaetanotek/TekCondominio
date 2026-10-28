import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { AtribuicaoRequest, PapelOpcao, UsuarioDetalhe, UserQuery, UsuarioResumo } from './user.models';

const BASE = '/api/v1/usuarios';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private roles$?: Observable<PapelOpcao[]>;

  list(query: UserQuery): Observable<PaginaResponse<UsuarioResumo>> {
    let params = new HttpParams().set('pagina', query.page).set('tamanho', query.size);
    if (query.search?.trim()) params = params.set('busca', query.search.trim());
    if (query.active) params = params.set('ativo', query.active);
    return this.http.get<PaginaResponse<UsuarioResumo>>(BASE, { params });
  }

  get(id: string): Observable<UsuarioDetalhe> {
    return this.http.get<UsuarioDetalhe>(`${BASE}/${id}`);
  }

  create(body: { nome: string; email: string; atribuicao: AtribuicaoRequest }): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(BASE, body);
  }

  rename(id: string, nome: string, versao: number): Observable<UsuarioDetalhe> {
    return this.http.put<UsuarioDetalhe>(`${BASE}/${id}`, { nome, versao });
  }

  deactivate(id: string): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(`${BASE}/${id}/desativar`, null);
  }

  activate(id: string): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(`${BASE}/${id}/ativar`, null);
  }

  addAssignment(id: string, body: AtribuicaoRequest): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(`${BASE}/${id}/atribuicoes`, body);
  }

  removeAssignment(id: string, idAtribuicao: string): Observable<UsuarioDetalhe> {
    return this.http.delete<UsuarioDetalhe>(`${BASE}/${id}/atribuicoes/${idAtribuicao}`);
  }

  resendInvite(id: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${id}/convite`, null);
  }

  /** Catálogo de papéis (estável durante a sessão). */
  roles(): Observable<PapelOpcao[]> {
    this.roles$ ??= this.http.get<PapelOpcao[]>('/api/v1/papeis').pipe(shareReplay(1));
    return this.roles$;
  }
}
