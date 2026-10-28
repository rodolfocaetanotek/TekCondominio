import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../../core/api/api.models';
import { AuditEntry } from './audit.models';

export interface AuditQuery {
  action?: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  list(query: AuditQuery): Observable<PageResponse<AuditEntry>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.action) params = params.set('action', query.action);
    return this.http.get<PageResponse<AuditEntry>>('/api/v1/audit', { params });
  }
}
