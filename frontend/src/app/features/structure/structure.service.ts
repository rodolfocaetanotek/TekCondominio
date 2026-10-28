import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import {
  Building,
  FractionSummary,
  ImportResult,
  Occupancy,
  OccupancyRole,
  PersonDetail,
  PersonRequest,
  PersonSummary,
  Unit,
  UnitRequest,
} from './structure.models';

@Injectable({ providedIn: 'root' })
export class StructureService {
  private readonly http = inject(HttpClient);

  private base(condominiumId: string): string {
    return `/api/v1/condominiums/${condominiumId}`;
  }

  buildings(c: string): Observable<Building[]> {
    return this.http.get<Building[]>(`${this.base(c)}/buildings`);
  }

  saveBuilding(c: string, body: { name: string; description: string | null }, id?: string): Observable<Building> {
    return id
      ? this.http.put<Building>(`${this.base(c)}/buildings/${id}`, body)
      : this.http.post<Building>(`${this.base(c)}/buildings`, body);
  }

  setBuildingActive(c: string, id: string, active: boolean): Observable<Building> {
    return this.http.post<Building>(`${this.base(c)}/buildings/${id}/${active ? 'activate' : 'deactivate'}`, null);
  }

  units(c: string, q: { search?: string; buildingId?: string; active?: string; page: number; size: number }): Observable<PaginaResponse<Unit>> {
    let params = new HttpParams().set('pagina', q.page).set('tamanho', q.size);
    if (q.search) params = params.set('search', q.search);
    if (q.buildingId) params = params.set('buildingId', q.buildingId);
    if (q.active) params = params.set('active', q.active);
    return this.http.get<PaginaResponse<Unit>>(`${this.base(c)}/units`, { params });
  }

  unit(c: string, id: string): Observable<Unit> {
    return this.http.get<Unit>(`${this.base(c)}/units/${id}`);
  }

  saveUnit(c: string, body: UnitRequest, id?: string): Observable<Unit> {
    return id ? this.http.put<Unit>(`${this.base(c)}/units/${id}`, body) : this.http.post<Unit>(`${this.base(c)}/units`, body);
  }

  setUnitActive(c: string, id: string, active: boolean): Observable<Unit> {
    return this.http.post<Unit>(`${this.base(c)}/units/${id}/${active ? 'activate' : 'deactivate'}`, null);
  }

  fractionSummary(c: string): Observable<FractionSummary> {
    return this.http.get<FractionSummary>(`${this.base(c)}/units/fraction-summary`);
  }

  importUnits(c: string, file: File, dryRun: boolean): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.base(c)}/units/import`, form, { params: { dryRun } });
  }

  occupancies(c: string, unitId: string): Observable<Occupancy[]> {
    return this.http.get<Occupancy[]>(`${this.base(c)}/units/${unitId}/occupancies`);
  }

  addOccupancy(c: string, body: { unitId: string; personId: string; role: OccupancyRole; financialResponsible: boolean; startDate: string }): Observable<Occupancy> {
    return this.http.post<Occupancy>(`${this.base(c)}/occupancies`, body);
  }

  endOccupancy(c: string, id: string, endDate: string, reason: string | null): Observable<Occupancy> {
    return this.http.post<Occupancy>(`${this.base(c)}/occupancies/${id}/end`, { endDate, reason });
  }

  makeFinancialResponsible(c: string, id: string): Observable<Occupancy> {
    return this.http.post<Occupancy>(`${this.base(c)}/occupancies/${id}/financial-responsible`, null);
  }

  // ---------- pessoas
  people(q: { search?: string; active?: string; condominiumId?: string; page: number; size: number }): Observable<PaginaResponse<PersonSummary>> {
    let params = new HttpParams().set('pagina', q.page).set('tamanho', q.size);
    if (q.search) params = params.set('search', q.search);
    if (q.active) params = params.set('active', q.active);
    if (q.condominiumId) params = params.set('condominiumId', q.condominiumId);
    return this.http.get<PaginaResponse<PersonSummary>>('/api/v1/people', { params });
  }

  person(id: string): Observable<PersonDetail> {
    return this.http.get<PersonDetail>(`/api/v1/people/${id}`);
  }

  savePerson(body: PersonRequest, id?: string): Observable<PersonDetail> {
    return id ? this.http.put<PersonDetail>(`/api/v1/people/${id}`, body) : this.http.post<PersonDetail>('/api/v1/people', body);
  }

  setPersonActive(id: string, active: boolean): Observable<PersonDetail> {
    return this.http.post<PersonDetail>(`/api/v1/people/${id}/${active ? 'activate' : 'deactivate'}`, null);
  }

  grantPortalAccess(id: string): Observable<PersonDetail> {
    return this.http.post<PersonDetail>(`/api/v1/people/${id}/portal-access`, null);
  }
}
