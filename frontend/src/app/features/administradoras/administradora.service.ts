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
export class AdministradoraService {
  private readonly http = inject(HttpClient);

  listar(pagina: number, tamanho: number): Observable<PaginaResponse<Administradora>> {
    const parametros = new HttpParams().set('pagina', pagina).set('tamanho', tamanho).set('ordem', 'nome,asc');
    return this.http.get<PaginaResponse<Administradora>>(BASE, { params: parametros });
  }

  cadastrar(corpo: AdministradoraRequest): Observable<Administradora> {
    return this.http.post<Administradora>(BASE, corpo);
  }

  obter(id: string): Observable<Administradora> {
    return this.http.get<Administradora>(`${BASE}/${id}`);
  }

  usuarios(id: string): Observable<UsuarioAdministradora[]> {
    return this.http.get<UsuarioAdministradora[]>(`${BASE}/${id}/usuarios`);
  }

  convidarAdministrador(id: string, nome: string, email: string): Observable<UsuarioAdministradora> {
    return this.http.post<UsuarioAdministradora>(`${BASE}/${id}/administradores`, { nome, email });
  }

  alterarAtivo(id: string, ativo: boolean): Observable<Administradora> {
    return this.http.post<Administradora>(`${BASE}/${id}/${ativo ? 'ativar' : 'desativar'}`, null);
  }
}
