import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { RegistroAuditoria } from './audit.models';

export interface AuditQuery {
  action?: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  list(query: AuditQuery): Observable<PaginaResponse<RegistroAuditoria>> {
    let params = new HttpParams().set('pagina', query.page).set('tamanho', query.size);
    if (query.action) params = params.set('acao', query.action);
    return this.http.get<PaginaResponse<RegistroAuditoria>>('/api/v1/auditoria', { params });
  }
}
