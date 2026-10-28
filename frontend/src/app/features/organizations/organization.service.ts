import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';

export interface Administradora {
  id: string;
  nome: string;
  cnpj: string;
  email?: string;
  telefone?: string;
  ativo: boolean;
  criadoEm: string;
}

export interface UsuarioAdministradora {
  id: string;
  nome: string;
  email: string;
  ativo: boolean;
  convitePendente: boolean;
  ultimoLoginEm?: string;
  papeis: string[];
}

export interface AdministradoraRequest {
  nome: string;
  cnpj: string;
  email: string | null;
  telefone: string | null;
  administrador: { nome: string; email: string; senha: string };
}

const BASE = '/api/v1/administradoras';

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number): Observable<PaginaResponse<Administradora>> {
    const params = new HttpParams().set('pagina', page).set('tamanho', size).set('ordem', 'nome,asc');
    return this.http.get<PaginaResponse<Administradora>>(BASE, { params });
  }

  create(body: AdministradoraRequest): Observable<Administradora> {
    return this.http.post<Administradora>(BASE, body);
  }

  get(id: string): Observable<Administradora> {
    return this.http.get<Administradora>(`${BASE}/${id}`);
  }

  users(id: string): Observable<UsuarioAdministradora[]> {
    return this.http.get<UsuarioAdministradora[]>(`${BASE}/${id}/usuarios`);
  }

  inviteAdmin(id: string, nome: string, email: string): Observable<UsuarioAdministradora> {
    return this.http.post<UsuarioAdministradora>(`${BASE}/${id}/administradores`, { nome, email });
  }

  setActive(id: string, active: boolean): Observable<Administradora> {
    return this.http.post<Administradora>(`${BASE}/${id}/${active ? 'ativar' : 'desativar'}`, null);
  }
}
