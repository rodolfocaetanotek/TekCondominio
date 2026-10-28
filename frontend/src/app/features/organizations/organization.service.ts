import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../../core/api/api.models';

export interface Organization {
  id: string;
  name: string;
  cnpj: string;
  email?: string;
  phone?: string;
  active: boolean;
  createdAt: string;
}

export interface OrganizationUser {
  id: string;
  name: string;
  email: string;
  active: boolean;
  pendingInvite: boolean;
  lastLoginAt?: string;
  roles: string[];
}

export interface OrganizationRequest {
  name: string;
  cnpj: string;
  email: string | null;
  phone: string | null;
  admin: { name: string; email: string; password: string };
}

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);

  list(page: number, size: number): Observable<PageResponse<Organization>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'name,asc');
    return this.http.get<PageResponse<Organization>>('/api/v1/organizations', { params });
  }

  create(body: OrganizationRequest): Observable<Organization> {
    return this.http.post<Organization>('/api/v1/organizations', body);
  }

  get(id: string): Observable<Organization> {
    return this.http.get<Organization>(`/api/v1/organizations/${id}`);
  }

  users(id: string): Observable<OrganizationUser[]> {
    return this.http.get<OrganizationUser[]>(`/api/v1/organizations/${id}/users`);
  }

  inviteAdmin(id: string, name: string, email: string): Observable<OrganizationUser> {
    return this.http.post<OrganizationUser>(`/api/v1/organizations/${id}/admins`, { name, email });
  }

  setActive(id: string, active: boolean): Observable<Organization> {
    return this.http.post<Organization>(`/api/v1/organizations/${id}/${active ? 'activate' : 'deactivate'}`, null);
  }
}
