import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { Condominio, ConsultaCondominio, CondominioRequest, CondominioResumo } from './condominio.models';

const BASE = '/api/v1/condominios';

@Injectable({ providedIn: 'root' })
export class CondominioService {
  private readonly http = inject(HttpClient);

  listar(consulta: ConsultaCondominio): Observable<PaginaResponse<CondominioResumo>> {
    let parametros = new HttpParams().set('pagina', consulta.pagina).set('tamanho', consulta.tamanho);
    if (consulta.busca?.trim()) parametros = parametros.set('busca', consulta.busca.trim());
    if (consulta.situacao) parametros = parametros.set('situacao', consulta.situacao);
    if (consulta.ordem) parametros = parametros.set('ordem', consulta.ordem);
    return this.http.get<PaginaResponse<CondominioResumo>>(BASE, { params: parametros });
  }

  obter(id: string): Observable<Condominio> {
    return this.http.get<Condominio>(`${BASE}/${id}`);
  }

  cadastrar(corpo: CondominioRequest): Observable<Condominio> {
    return this.http.post<Condominio>(BASE, corpo);
  }

  atualizar(id: string, corpo: CondominioRequest): Observable<Condominio> {
    return this.http.put<Condominio>(`${BASE}/${id}`, corpo);
  }

  inativar(id: string, motivo: string): Observable<Condominio> {
    return this.http.post<Condominio>(`${BASE}/${id}/inativar`, { motivo });
  }

  ativar(id: string): Observable<Condominio> {
    return this.http.post<Condominio>(`${BASE}/${id}/ativar`, null);
  }
}
