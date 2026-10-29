import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { RegistroAuditoria } from './auditoria.models';

export interface ConsultaAuditoria {
  acao?: string;
  pagina: number;
  tamanho: number;
}

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);

  listar(consulta: ConsultaAuditoria): Observable<PaginaResponse<RegistroAuditoria>> {
    let parametros = new HttpParams().set('pagina', consulta.pagina).set('tamanho', consulta.tamanho);
    if (consulta.acao) parametros = parametros.set('acao', consulta.acao);
    return this.http.get<PaginaResponse<RegistroAuditoria>>('/api/v1/auditoria', { params: parametros });
  }
}
