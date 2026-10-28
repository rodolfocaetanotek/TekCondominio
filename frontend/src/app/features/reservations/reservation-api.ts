import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface CommonArea {
  id: string; name: string; description?: string; rules?: string; capacity?: number; fee: number; requiresApproval: boolean;
  blockDelinquent: boolean; openTime: string; closeTime: string; maxDurationMinutes: number; minAdvanceHours: number;
  maxAdvanceDays: number; maxPerUnitMonth?: number; active: boolean; version: number;
}
export interface Reservation {
  id: string; areaId: string; areaName: string; unitId: string; unitLabel?: string; date: string; startTime: string; endTime: string;
  guests?: number; notes?: string; status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'; fee: number; entryId?: string;
  requesterName: string; byResident: boolean; decisionNote?: string; cancelReason?: string; createdAt: string;
}
export interface Slot { areaId: string; date: string; startTime: string; endTime: string; status: string; }

/** Reservas de áreas comuns: administração (/condominiums) e portal do morador (/portal). */
@Injectable({ providedIn: 'root' })
export class ReservationApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}`;
  }

  areas(c: string): Observable<CommonArea[]> { return this.http.get<CommonArea[]>(`${this.b(c)}/common-areas`); }
  saveArea(c: string, body: Record<string, unknown>, id?: string): Observable<CommonArea> {
    return id ? this.http.put<CommonArea>(`${this.b(c)}/common-areas/${id}`, body) : this.http.post<CommonArea>(`${this.b(c)}/common-areas`, body);
  }
  list(c: string, from: string, to: string, areaId?: string): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.b(c)}/reservations`, { params: { from, to, ...(areaId ? { areaId } : {}) } });
  }
  create(c: string, body: Record<string, unknown>): Observable<Reservation> { return this.http.post<Reservation>(`${this.b(c)}/reservations`, body); }
  approve(c: string, id: string, note: string | null): Observable<Reservation> { return this.http.post<Reservation>(`${this.b(c)}/reservations/${id}/approve`, { note }); }
  reject(c: string, id: string, note: string): Observable<Reservation> { return this.http.post<Reservation>(`${this.b(c)}/reservations/${id}/reject`, { note }); }
  cancel(c: string, id: string, reason: string): Observable<Reservation> { return this.http.post<Reservation>(`${this.b(c)}/reservations/${id}/cancel`, { reason }); }

  portalAreas(c: string): Observable<CommonArea[]> { return this.http.get<CommonArea[]>(`/api/v1/portal/${c}/common-areas`); }
  portalOccupancy(c: string, areaId: string, from: string, to: string): Observable<Slot[]> {
    return this.http.get<Slot[]>(`/api/v1/portal/${c}/common-areas/${areaId}/occupancy`, { params: { from, to } });
  }
  portalMine(c: string): Observable<Reservation[]> { return this.http.get<Reservation[]>(`/api/v1/portal/${c}/reservations`); }
  portalBook(c: string, body: Record<string, unknown>): Observable<Reservation> { return this.http.post<Reservation>(`/api/v1/portal/${c}/reservations`, body); }
  portalCancel(c: string, id: string, reason: string): Observable<Reservation> { return this.http.post<Reservation>(`/api/v1/portal/${c}/reservations/${id}/cancel`, { reason }); }
}
