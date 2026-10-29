import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { AtribuicaoRequest, PapelOpcao, UsuarioDetalhe, ConsultaUsuario, UsuarioResumo } from './usuario.models';

const BASE = '/api/v1/usuarios';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private papeis$?: Observable<PapelOpcao[]>;

  listar(consulta: ConsultaUsuario): Observable<PaginaResponse<UsuarioResumo>> {
    let parametros = new HttpParams().set('pagina', consulta.pagina).set('tamanho', consulta.tamanho);
    if (consulta.busca?.trim()) parametros = parametros.set('busca', consulta.busca.trim());
    if (consulta.ativo) parametros = parametros.set('ativo', consulta.ativo);
    return this.http.get<PaginaResponse<UsuarioResumo>>(BASE, { params: parametros });
  }

  obter(id: string): Observable<UsuarioDetalhe> {
    return this.http.get<UsuarioDetalhe>(`${BASE}/${id}`);
  }

  cadastrar(corpo: { nome: string; email: string; atribuicao: AtribuicaoRequest }): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(BASE, corpo);
  }

  renomear(id: string, nome: string, versao: number): Observable<UsuarioDetalhe> {
    return this.http.put<UsuarioDetalhe>(`${BASE}/${id}`, { nome, versao });
  }

  desativar(id: string): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(`${BASE}/${id}/desativar`, null);
  }

  ativar(id: string): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(`${BASE}/${id}/ativar`, null);
  }

  adicionarAtribuicao(id: string, corpo: AtribuicaoRequest): Observable<UsuarioDetalhe> {
    return this.http.post<UsuarioDetalhe>(`${BASE}/${id}/atribuicoes`, corpo);
  }

  removerAtribuicao(id: string, idAtribuicao: string): Observable<UsuarioDetalhe> {
    return this.http.delete<UsuarioDetalhe>(`${BASE}/${id}/atribuicoes/${idAtribuicao}`);
  }

  reenviarConvite(id: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${id}/convite`, null);
  }

  /** Catálogo de papéis (estável durante a sessão). */
  papeis(): Observable<PapelOpcao[]> {
    this.papeis$ ??= this.http.get<PapelOpcao[]>('/api/v1/papeis').pipe(shareReplay(1));
    return this.papeis$;
  }
}
