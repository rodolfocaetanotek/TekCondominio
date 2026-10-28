import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface VisitorPass {
  id: string; unitId: string; unitLabel: string; kind: 'VISITOR' | 'SERVICE_PROVIDER' | 'DELIVERY'; name: string; document?: string;
  company?: string; vehiclePlate?: string; validFrom: string; validUntil: string; accessCode?: string;
  status: 'EXPECTED' | 'INSIDE' | 'LEFT' | 'CANCELLED'; byResident: boolean; authorizedByName: string; enteredAt?: string;
  leftAt?: string; notes?: string;
}

/** Portaria (administração/porteiro) e autorizações do morador (portal). */
@Injectable({ providedIn: 'root' })
export class VisitorApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}/visitors`;
  }

  today(c: string): Observable<VisitorPass[]> { return this.http.get<VisitorPass[]>(`${this.b(c)}/today`); }
  history(c: string, from: string, to: string): Observable<VisitorPass[]> { return this.http.get<VisitorPass[]>(this.b(c), { params: { from, to } }); }
  arrival(c: string, body: Record<string, unknown>): Observable<VisitorPass> { return this.http.post<VisitorPass>(`${this.b(c)}/arrivals`, body); }
  checkInByCode(c: string, code: string): Observable<VisitorPass> { return this.http.post<VisitorPass>(`${this.b(c)}/check-in-code`, { code }); }
  checkIn(c: string, id: string): Observable<VisitorPass> { return this.http.post<VisitorPass>(`${this.b(c)}/${id}/check-in`, null); }
  checkOut(c: string, id: string): Observable<VisitorPass> { return this.http.post<VisitorPass>(`${this.b(c)}/${id}/check-out`, null); }

  portalList(c: string): Observable<VisitorPass[]> { return this.http.get<VisitorPass[]>(`/api/v1/portal/${c}/visitors`); }
  portalAuthorize(c: string, body: Record<string, unknown>): Observable<VisitorPass> { return this.http.post<VisitorPass>(`/api/v1/portal/${c}/visitors`, body); }
  portalCancel(c: string, id: string): Observable<VisitorPass> { return this.http.post<VisitorPass>(`/api/v1/portal/${c}/visitors/${id}/cancel`, null); }
}
