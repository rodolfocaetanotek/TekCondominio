import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { downloadFile } from '../../shared/download';

export interface Doc { id: string; category: string; title: string; description?: string; fileName: string; contentType: string; sizeBytes: number; visibility: string; entityType?: string; entityId?: string; unitId?: string; archived: boolean; createdAt: string; createdByName?: string; }
export interface OccurrenceSummary { id: string; number: number; title: string; category: string; priority: string; status: string; unitId?: string; unitLabel?: string; reporterName: string; assigneeName?: string; dueDate?: string; overdue: boolean; createdAt: string; resolvedAt?: string; }
export interface Interaction { id: string; authorName: string; message: string; statusChange?: string; internal: boolean; createdAt: string; }
export interface OccurrenceDetail { summary: OccurrenceSummary; description: string; resolution?: string; version: number; interactions: Interaction[]; }
export interface OccurrenceStats { byStatus: Record<string, number>; open: number; overdue: number; averageResolutionHours?: number; }
export interface Announcement { id: string; title: string; body: string; audience: string; buildingName?: string; unitLabel?: string; pinned: boolean; sendEmail: boolean; expiresOn?: string; publishedAt: string; archived: boolean; createdByName?: string; readCount?: number; read?: boolean; }
export interface AssemblySummary { id: string; title: string; kind: string; scheduledAt: string; location: string; status: string; }
export interface AgendaItem { id: string; position: number; title: string; description?: string; voting: boolean; deliberation?: string; responsible?: string; deadline?: string; tally: { yes: number; no: number; abstain: number; votes: number }; }
export interface Attendance { id: string; unitId: string; unitLabel: string; attendeeName: string; byProxy: boolean; proxyName?: string; registeredAt: string; }
export interface AssemblyDetail { id: string; title: string; kind: string; scheduledAt: string; secondCallAt?: string; location: string; voteWeight: string; status: string; calledAt?: string; closedAt?: string; cancelReason?: string; minutes?: string; version: number; agenda: AgendaItem[]; attendance: Attendance[]; quorum: { unitsPresent: number; totalUnits: number; fractionPresent: number }; }
export interface Contract { id: string; supplierId: string; supplierName: string; title: string; description?: string; amount: number; periodicity: string; startDate: string; endDate?: string; readjustmentIndex?: string; readjustmentMonth?: number; noticeDays: number; status: string; expiring: boolean; expired: boolean; terminatedOn?: string; terminationReason?: string; version: number; history: { change: string; createdAt: string; createdByName?: string }[]; }
export interface CaixaEntrada { naoLidas: number; itens: { id: string; titulo: string; texto?: string; link?: string; lida: boolean; criadoEm: string }[]; }
export interface ReportTable { title: string; subtitle?: string; columns: { key: string; label: string; type: string }[]; rows: unknown[][]; totals?: unknown[]; notes?: string[]; }
export interface Dashboard {
  from: string; to: string; condominiums: number; units: number; residents: number; finance: boolean; income: number; expense: number;
  balance: number; receivablesOpen: number; payablesOpen: number; overdueAmount: number; overdueCharges: number; chargesIssued: number;
  delinquencyRate: number; series: { month: string; income: number; expense: number }[];
  payablesDueSoon: { id: string; condominiumId: string; condominiumName: string; description: string; dueDate: string; amount: number; approval: string }[];
  recentOccurrences: { id: string; condominiumId: string; condominiumName: string; title: string; status: string; when: string }[];
  recentAnnouncements: { id: string; condominiumId: string; condominiumName: string; title: string; when: string }[];
}

@Injectable({ providedIn: 'root' })
export class OpsApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}`;
  }

  // documentos
  documents(c: string, q: Record<string, string> = {}): Observable<PaginaResponse<Doc>> { return this.http.get<PaginaResponse<Doc>>(`${this.b(c)}/documents`, { params: { tamanho: '100', ...q } }); }
  upload(c: string, file: File, meta: Record<string, string>): Observable<Doc> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Doc>(`${this.b(c)}/documents`, form, { params: meta });
  }
  download(c: string, id: string): Observable<void> { return downloadFile(this.http, `${this.b(c)}/documents/${id}/download`); }
  archiveDocument(c: string, id: string): Observable<Doc> { return this.http.post<Doc>(`${this.b(c)}/documents/${id}/archive`, null); }

  // ocorrências
  occurrences(c: string, q: Record<string, string | string[]>): Observable<PaginaResponse<OccurrenceSummary>> { return this.http.get<PaginaResponse<OccurrenceSummary>>(`${this.b(c)}/occurrences`, { params: q }); }
  occurrenceStats(c: string): Observable<OccurrenceStats> { return this.http.get<OccurrenceStats>(`${this.b(c)}/occurrences/stats`); }
  occurrence(c: string, id: string): Observable<OccurrenceDetail> { return this.http.get<OccurrenceDetail>(`${this.b(c)}/occurrences/${id}`); }
  createOccurrence(c: string, body: Record<string, unknown>): Observable<OccurrenceDetail> { return this.http.post<OccurrenceDetail>(`${this.b(c)}/occurrences`, body); }
  updateOccurrence(c: string, id: string, body: Record<string, unknown>): Observable<OccurrenceDetail> { return this.http.put<OccurrenceDetail>(`${this.b(c)}/occurrences/${id}`, body); }
  occurrenceStatus(c: string, id: string, body: Record<string, unknown>): Observable<OccurrenceDetail> { return this.http.post<OccurrenceDetail>(`${this.b(c)}/occurrences/${id}/status`, body); }
  comment(c: string, id: string, message: string, internal: boolean): Observable<OccurrenceDetail> { return this.http.post<OccurrenceDetail>(`${this.b(c)}/occurrences/${id}/comments`, { message, internal }); }

  // comunicados
  announcements(c: string, archived = false): Observable<Announcement[]> { return this.http.get<Announcement[]>(`${this.b(c)}/announcements`, { params: { archived } }); }
  publish(c: string, body: Record<string, unknown>): Observable<Announcement> { return this.http.post<Announcement>(`${this.b(c)}/announcements`, body); }
  archiveAnnouncement(c: string, id: string): Observable<Announcement> { return this.http.post<Announcement>(`${this.b(c)}/announcements/${id}/archive`, null); }
  readAnnouncement(c: string, id: string): Observable<void> { return this.http.post<void>(`${this.b(c)}/announcements/${id}/read`, null); }

  // assembleias
  assemblies(c: string): Observable<AssemblySummary[]> { return this.http.get<AssemblySummary[]>(`${this.b(c)}/assemblies`); }
  assembly(c: string, id: string): Observable<AssemblyDetail> { return this.http.get<AssemblyDetail>(`${this.b(c)}/assemblies/${id}`); }
  saveAssembly(c: string, body: Record<string, unknown>, id?: string): Observable<AssemblyDetail> {
    return id ? this.http.put<AssemblyDetail>(`${this.b(c)}/assemblies/${id}`, body) : this.http.post<AssemblyDetail>(`${this.b(c)}/assemblies`, body);
  }
  assemblyAction(c: string, id: string, action: 'call' | 'start' | 'close', ): Observable<AssemblyDetail> { return this.http.post<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/${action}`, null); }
  cancelAssembly(c: string, id: string, reason: string): Observable<AssemblyDetail> { return this.http.post<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/cancel`, { reason }); }
  attend(c: string, id: string, body: Record<string, unknown>): Observable<AssemblyDetail> { return this.http.post<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/attendance`, body); }
  removeAttendance(c: string, id: string, attendanceId: string): Observable<AssemblyDetail> { return this.http.delete<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/attendance/${attendanceId}`); }
  vote(c: string, id: string, body: Record<string, unknown>): Observable<AssemblyDetail> { return this.http.post<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/votes`, body); }
  deliberate(c: string, id: string, itemId: string, body: Record<string, unknown>): Observable<AssemblyDetail> { return this.http.put<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/agenda/${itemId}/deliberation`, body); }
  saveMinutes(c: string, id: string, minutes: string): Observable<AssemblyDetail> { return this.http.put<AssemblyDetail>(`${this.b(c)}/assemblies/${id}/minutes`, { minutes }); }
  minutesDraft(c: string, id: string): Observable<{ minutes: string }> { return this.http.get<{ minutes: string }>(`${this.b(c)}/assemblies/${id}/minutes-draft`); }

  // contratos
  contracts(c: string): Observable<Contract[]> { return this.http.get<Contract[]>(`${this.b(c)}/contracts`); }
  contract(c: string, id: string): Observable<Contract> { return this.http.get<Contract>(`${this.b(c)}/contracts/${id}`); }
  saveContract(c: string, body: Record<string, unknown>, id?: string): Observable<Contract> {
    return id ? this.http.put<Contract>(`${this.b(c)}/contracts/${id}`, body) : this.http.post<Contract>(`${this.b(c)}/contracts`, body);
  }
  terminateContract(c: string, id: string, body: Record<string, unknown>): Observable<Contract> { return this.http.post<Contract>(`${this.b(c)}/contracts/${id}/terminate`, body); }

  // notificações, painel, relatórios
  inbox(): Observable<CaixaEntrada> { return this.http.get<CaixaEntrada>('/api/v1/notificacoes'); }
  readNotification(id: string): Observable<void> { return this.http.post<void>(`/api/v1/notificacoes/${id}/marcar-lida`, null); }
  readAllNotifications(): Observable<void> { return this.http.post<void>('/api/v1/notificacoes/marcar-todas-lidas', null); }
  dashboard(q: Record<string, string>): Observable<Dashboard> { return this.http.get<Dashboard>('/api/v1/dashboard', { params: q }); }
  report(c: string, kind: string, q: Record<string, string>): Observable<ReportTable> { return this.http.get<ReportTable>(`${this.b(c)}/reports/${kind}`, { params: q }); }
  exportReport(c: string, kind: string, q: Record<string, string>): Observable<void> { return downloadFile(this.http, `${this.b(c)}/reports/${kind}/export`, q); }
}
