import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { downloadFile } from '../../shared/download';
import { DebtLine } from '../finance/finance-api';
import { Announcement, Doc, OccurrenceDetail, OccurrenceSummary } from '../ops/ops-api';

export interface PortalMe { name: string; email: string; condominiums: { id: string; name: string; units: { unitId: string; label: string; role: string; financialResponsible: boolean; seesCharges: boolean }[] }[]; }
export interface UnitCharges { unitId: string; unitLabel: string; openTotal: number; lines: DebtLine[]; }
export interface PortalAssembly { id: string; title: string; kind: string; scheduledAt: string; location: string; status: string; minutes?: string; }
export interface CalendarEvent { date: string; type: string; title: string; }

@Injectable({ providedIn: 'root' })
export class PortalApi {
  private readonly http = inject(HttpClient);
  me(): Observable<PortalMe> { return this.http.get<PortalMe>('/api/v1/portal/me'); }
  charges(c: string): Observable<UnitCharges[]> { return this.http.get<UnitCharges[]>(`/api/v1/portal/${c}/charges`); }
  announcements(c: string): Observable<Announcement[]> { return this.http.get<Announcement[]>(`/api/v1/portal/${c}/announcements`); }
  read(c: string, id: string): Observable<void> { return this.http.post<void>(`/api/v1/portal/${c}/announcements/${id}/read`, null); }
  documents(c: string): Observable<Doc[]> { return this.http.get<Doc[]>(`/api/v1/portal/${c}/documents`); }
  download(c: string, id: string): Observable<void> { return downloadFile(this.http, `/api/v1/portal/${c}/documents/${id}/download`); }
  occurrences(c: string): Observable<OccurrenceSummary[]> { return this.http.get<OccurrenceSummary[]>(`/api/v1/portal/${c}/occurrences`); }
  occurrence(c: string, id: string): Observable<OccurrenceDetail> { return this.http.get<OccurrenceDetail>(`/api/v1/portal/${c}/occurrences/${id}`); }
  openOccurrence(c: string, body: Record<string, unknown>): Observable<OccurrenceDetail> { return this.http.post<OccurrenceDetail>(`/api/v1/portal/${c}/occurrences`, body); }
  comment(c: string, id: string, message: string): Observable<OccurrenceDetail> { return this.http.post<OccurrenceDetail>(`/api/v1/portal/${c}/occurrences/${id}/comments`, { message, internal: false }); }
  assemblies(c: string): Observable<PortalAssembly[]> { return this.http.get<PortalAssembly[]>(`/api/v1/portal/${c}/assemblies`); }
  calendar(c: string): Observable<CalendarEvent[]> { return this.http.get<CalendarEvent[]>(`/api/v1/portal/${c}/calendar`); }
}
