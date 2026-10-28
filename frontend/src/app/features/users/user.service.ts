import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { PageResponse } from '../../core/api/api.models';
import { AssignmentRequest, RoleOption, UserDetail, UserQuery, UserSummary } from './user.models';

const BASE = '/api/v1/users';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private roles$?: Observable<RoleOption[]>;

  list(query: UserQuery): Observable<PageResponse<UserSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.search?.trim()) params = params.set('search', query.search.trim());
    if (query.active) params = params.set('active', query.active);
    return this.http.get<PageResponse<UserSummary>>(BASE, { params });
  }

  get(id: string): Observable<UserDetail> {
    return this.http.get<UserDetail>(`${BASE}/${id}`);
  }

  create(body: { name: string; email: string; assignment: AssignmentRequest }): Observable<UserDetail> {
    return this.http.post<UserDetail>(BASE, body);
  }

  rename(id: string, name: string, version: number): Observable<UserDetail> {
    return this.http.put<UserDetail>(`${BASE}/${id}`, { name, version });
  }

  deactivate(id: string): Observable<UserDetail> {
    return this.http.post<UserDetail>(`${BASE}/${id}/deactivate`, null);
  }

  activate(id: string): Observable<UserDetail> {
    return this.http.post<UserDetail>(`${BASE}/${id}/activate`, null);
  }

  addAssignment(id: string, body: AssignmentRequest): Observable<UserDetail> {
    return this.http.post<UserDetail>(`${BASE}/${id}/assignments`, body);
  }

  removeAssignment(id: string, assignmentId: string): Observable<UserDetail> {
    return this.http.delete<UserDetail>(`${BASE}/${id}/assignments/${assignmentId}`);
  }

  resendInvite(id: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${id}/invite`, null);
  }

  /** Catálogo de papéis (estável durante a sessão). */
  roles(): Observable<RoleOption[]> {
    this.roles$ ??= this.http.get<RoleOption[]>('/api/v1/roles').pipe(shareReplay(1));
    return this.roles$;
  }
}
