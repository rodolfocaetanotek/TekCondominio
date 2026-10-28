import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';

export interface ChartAccount { id: string; code: string; name: string; nature: 'INCOME' | 'EXPENSE'; analytic: boolean; system: boolean; active: boolean; }
export interface CostCenter { id: string; name: string; active: boolean; }
export interface BankAccount { id: string; name: string; kind: string; bankCode?: string; agency?: string; accountNumber?: string; openingBalance: number; openingDate: string; active: boolean; balance: number; }
export interface Settings { finePercent: number; interestMonthlyPercent: number; billingDueDay: number; requirePayableApproval: boolean; }
export interface Supplier { id: string; name: string; document?: string; category?: string; email?: string; phone?: string; contactName?: string; notes?: string; active: boolean; }

export interface EntrySummary {
  id: string; kind: 'INCOME' | 'EXPENSE'; description: string; accountCode: string; accountName: string; supplierName?: string;
  unitLabel?: string; personName?: string; amount: number; paidAmount: number; remaining: number; dueDate: string; competence: string;
  status: string; approval: string; overdue: boolean; installmentLabel?: string; documentNumber?: string; collectionNumber?: number;
}
export interface Payment {
  id: string; bankAccountId: string; bankAccountName: string; paidOn: string; principal: number; fine: number; interest: number;
  discount: number; total: number; method: string; notes?: string; reconciled: boolean; reversed: boolean; reversalReason?: string;
}
export interface EntryDetail {
  summary: EntrySummary; chartAccountId: string; costCenterId?: string; costCenterName?: string; supplierId?: string; unitId?: string;
  personId?: string; notes?: string; origin: string; approvalNote?: string; approvedAt?: string; cancelReason?: string;
  finePercent?: number; interestMonthlyPercent?: number; version: number; payments: Payment[];
}
export interface EntryRequest {
  kind: 'INCOME' | 'EXPENSE'; description: string; chartAccountId: string; costCenterId: string | null; supplierId: string | null;
  unitId: string | null; personId: string | null; amount: number; dueDate: string; competence: string | null;
  documentNumber: string | null; notes: string | null; repeat: number | null; version?: number;
}
export interface Quote { principal: number; daysLate: number; fine: number; interest: number; total: number; }
export interface Transfer { id: string; fromAccountId: string; fromAccountName: string; toAccountId: string; toAccountName: string; amount: number; date: string; description?: string; reconciled: boolean; reversed: boolean; reversalReason?: string; }
export interface StatementLine { type: string; id: string; date: string; description: string; amount: number; balance: number; reconciled: boolean; }
export interface Statement { bankAccountId: string; bankAccountName: string; from: string; to: string; openingBalance: number; closingBalance: number; reconciledBalance: number; lines: StatementLine[]; }
export interface BudgetRow { chartAccountId: string; code: string; name: string; nature: string; budgeted: number[]; realized: number[]; totalBudgeted: number; totalRealized: number; }

export interface Allocation { id: string; competence: string; description: string; accountCode?: string; accountName?: string; method: string; baseAmount: number; dueDate: string; unitsCount: number; generatedAmount: number; status: string; paidCount: number; receivedAmount: number; cancelReason?: string; }
export interface AllocationRequest { competence: string; description: string; chartAccountId: string; method: string; amount: number | null; dueDate: string | null; consumption?: Record<string, number>; }
export interface AllocationPreview { competence: string; dueDate: string; total: number; units: number; unitsWithoutResponsible: number; lines: { unitId: string; unitLabel: string; idealFraction: number; responsibleName?: string; amount: number }[]; }
export interface DelinquencyRow { unitId: string; unitLabel: string; responsibleName?: string; responsibleEmail?: string; responsiblePhone?: string; overdueCount: number; principal: number; charges: number; total: number; oldestDueDate: string; maxDaysLate: number; lastContactDate?: string; }
export interface Delinquency { asOf: string; totalPrincipal: number; totalCharges: number; total: number; issuedLast12Months: number; delinquencyRate: number; unitsInDebt: number; activeUnits: number; rows: DelinquencyRow[]; }
export interface DebtLine { entryId: string; description: string; competence: string; dueDate: string; amount: number; paid: number; remaining: number; status: string; daysLate: number; fine: number; interest: number; updatedTotal: number; }
export interface Note { id: string; contactType: string; contactDate: string; note: string; createdByName?: string; createdAt: string; }
export interface Agreement { id: string; unitId: string; unitLabel?: string; originalAmount: number; chargesAmount: number; discount: number; totalAmount: number; installments: number; firstDueDate: string; status: string; paidInstallments: number; notes?: string; cancelReason?: string; createdAt: string; installmentEntries: EntrySummary[]; }
export interface UnitDebt { unitId: string; unitLabel: string; responsibleName?: string; asOf: string; openPrincipal: number; openCharges: number; openTotal: number; lines: DebtLine[]; notes: Note[]; agreements: Agreement[]; }

export interface ClosingHistory { id: string; action: 'CLOSE' | 'REOPEN'; closedUntil?: string; previousUntil?: string; notes?: string; snapshot?: { income: string; expense: string; unreconciledMovements: number; balances: { account: string; balance: string; reconciled: string }[] }; createdAt: string; createdByName?: string; }
export interface ClosingStatus { closedUntil?: string; suggestedUntil?: string; history: ClosingHistory[]; }
export interface ClosingCheck { from: string; until: string; unreconciledMovements: number; pendingApprovals: number; income: number; expense: number; balances: { id: string; name: string; balance: number; reconciledBalance: number }[]; }
export interface ImportResult { importId: string; fileName: string; periodStart?: string; periodEnd?: string; linesInFile: number; newLines: number; duplicates: number; autoMatched: number; pending: number; }
export interface StatementSuggestion { type: string; id: string; date: string; amount: number; description: string; }
export interface StatementLineView { id: string; fitId: string; postedOn: string; amount: number; trnType?: string; memo?: string; status: 'UNMATCHED' | 'MATCHED' | 'IGNORED'; matchedType?: string; matchedId?: string; note?: string; suggestions: StatementSuggestion[]; }
export interface ReturnFile { id: string; bankAccountId: string; fileName: string; bankCode: string; generatedOn?: string; itemsTotal: number; settled: number; pending: number; createdAt: string; createdByName?: string; }
export interface ReturnItem { id: string; fileId: string; sequence: number; movementCode: string; movementLabel: string; ourNumber?: string; yourNumber?: string; dueDate?: string; faceValue: number; fees: number; discount: number; paidAmount: number; bankFee: number; occurrenceDate?: string; creditDate?: string; status: 'SETTLED' | 'PENDING' | 'INFO' | 'IGNORED'; entryId?: string; entryDescription?: string; unitLabel?: string; message?: string; }

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}`;
  }

  chartAccounts(): Observable<ChartAccount[]> { return this.http.get<ChartAccount[]>('/api/v1/finance/chart-accounts'); }
  createChartAccount(body: { code: string; name: string; nature: string }): Observable<ChartAccount> { return this.http.post<ChartAccount>('/api/v1/finance/chart-accounts', body); }
  settings(c: string): Observable<Settings> { return this.http.get<Settings>(`${this.b(c)}/finance/settings`); }
  saveSettings(c: string, s: Settings): Observable<Settings> { return this.http.put<Settings>(`${this.b(c)}/finance/settings`, s); }
  costCenters(c: string): Observable<CostCenter[]> { return this.http.get<CostCenter[]>(`${this.b(c)}/finance/cost-centers`); }
  saveCostCenter(c: string, name: string, id?: string, active?: boolean): Observable<CostCenter> {
    return id
      ? this.http.put<CostCenter>(`${this.b(c)}/finance/cost-centers/${id}`, { name }, { params: active === undefined ? {} : { active } })
      : this.http.post<CostCenter>(`${this.b(c)}/finance/cost-centers`, { name });
  }
  bankAccounts(c: string): Observable<BankAccount[]> { return this.http.get<BankAccount[]>(`${this.b(c)}/finance/bank-accounts`); }
  createBankAccount(c: string, body: Record<string, unknown>): Observable<BankAccount> { return this.http.post<BankAccount>(`${this.b(c)}/finance/bank-accounts`, body); }
  updateBankAccount(c: string, id: string, body: Record<string, unknown>, active?: boolean): Observable<BankAccount> {
    return this.http.put<BankAccount>(`${this.b(c)}/finance/bank-accounts/${id}`, body, { params: active === undefined ? {} : { active } });
  }
  statement(c: string, id: string, from: string, to: string): Observable<Statement> {
    return this.http.get<Statement>(`${this.b(c)}/finance/bank-accounts/${id}/statement`, { params: { from, to } });
  }
  reconcile(c: string, items: { type: string; id: string }[], reconciled: boolean): Observable<unknown> {
    return this.http.post(`${this.b(c)}/finance/reconciliation`, { items, reconciled });
  }

  entries(c: string, q: Record<string, string | number | string[] | undefined>): Observable<PaginaResponse<EntrySummary>> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(q)) {
      if (v === undefined || v === '' || v === null) continue;
      params = Array.isArray(v) ? v.reduce((p, x) => p.append(k, x), params) : params.set(k, String(v));
    }
    return this.http.get<PaginaResponse<EntrySummary>>(`${this.b(c)}/finance/entries`, { params });
  }
  entry(c: string, id: string): Observable<EntryDetail> { return this.http.get<EntryDetail>(`${this.b(c)}/finance/entries/${id}`); }
  createEntry(c: string, body: EntryRequest): Observable<EntryDetail[]> { return this.http.post<EntryDetail[]>(`${this.b(c)}/finance/entries`, body); }
  updateEntry(c: string, id: string, body: EntryRequest): Observable<EntryDetail> { return this.http.put<EntryDetail>(`${this.b(c)}/finance/entries/${id}`, body); }
  approve(c: string, id: string, approve: boolean, note: string | null): Observable<EntryDetail> {
    return this.http.post<EntryDetail>(`${this.b(c)}/finance/entries/${id}/${approve ? 'approve' : 'reject'}`, { note });
  }
  cancelEntry(c: string, id: string, reason: string): Observable<EntryDetail> { return this.http.post<EntryDetail>(`${this.b(c)}/finance/entries/${id}/cancel`, { reason }); }
  quote(c: string, id: string, date: string): Observable<Quote> { return this.http.get<Quote>(`${this.b(c)}/finance/entries/${id}/quote`, { params: { date } }); }
  pay(c: string, id: string, body: Record<string, unknown>): Observable<EntryDetail> { return this.http.post<EntryDetail>(`${this.b(c)}/finance/entries/${id}/payments`, body); }
  reversePayment(c: string, paymentId: string, reason: string): Observable<EntryDetail> {
    return this.http.post<EntryDetail>(`${this.b(c)}/finance/payments/${paymentId}/reverse`, { reason });
  }
  // fechamento mensal
  closing(c: string): Observable<ClosingStatus> { return this.http.get<ClosingStatus>(`${this.b(c)}/finance/closing`); }
  closingCheck(c: string, until: string): Observable<ClosingCheck> { return this.http.get<ClosingCheck>(`${this.b(c)}/finance/closing/check`, { params: { until } }); }
  close(c: string, until: string, notes: string | null): Observable<ClosingStatus> { return this.http.post<ClosingStatus>(`${this.b(c)}/finance/closing`, { until, notes }); }
  reopen(c: string, until: string | null, reason: string): Observable<ClosingStatus> { return this.http.post<ClosingStatus>(`${this.b(c)}/finance/closing/reopen`, { until, reason }); }

  // extrato OFX e conciliação
  importStatement(c: string, accountId: string, file: File, ignoreAccountCheck = false): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.b(c)}/finance/bank-accounts/${accountId}/statement-imports`, form, { params: { ignoreAccountCheck } });
  }
  statementLines(c: string, accountId: string, status?: string): Observable<StatementLineView[]> {
    return this.http.get<StatementLineView[]>(`${this.b(c)}/finance/bank-accounts/${accountId}/statement-lines`, { params: status ? { status } : {} });
  }
  matchLine(c: string, id: string, type: string, movementId: string): Observable<StatementLineView> { return this.http.post<StatementLineView>(`${this.b(c)}/finance/statement-lines/${id}/match`, { type, movementId }); }
  unmatchLine(c: string, id: string): Observable<StatementLineView> { return this.http.post<StatementLineView>(`${this.b(c)}/finance/statement-lines/${id}/unmatch`, null); }
  ignoreLine(c: string, id: string, note: string): Observable<StatementLineView> { return this.http.post<StatementLineView>(`${this.b(c)}/finance/statement-lines/${id}/ignore`, { note }); }
  registerLine(c: string, id: string, body: Record<string, unknown>): Observable<StatementLineView> { return this.http.post<StatementLineView>(`${this.b(c)}/finance/statement-lines/${id}/register`, body); }

  // retorno bancário (CNAB 240)
  processReturn(c: string, bankAccountId: string, file: File): Observable<ReturnFile> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ReturnFile>(`${this.b(c)}/billing/bank-returns`, form, { params: { bankAccountId } });
  }
  returnFiles(c: string): Observable<ReturnFile[]> { return this.http.get<ReturnFile[]>(`${this.b(c)}/billing/bank-returns`); }
  returnItems(c: string, fileId: string): Observable<ReturnItem[]> { return this.http.get<ReturnItem[]>(`${this.b(c)}/billing/bank-returns/${fileId}/items`); }
  pendingReturnItems(c: string): Observable<ReturnItem[]> { return this.http.get<ReturnItem[]>(`${this.b(c)}/billing/bank-return-items/pending`); }
  settleReturnItem(c: string, id: string, entryId: string): Observable<ReturnItem> { return this.http.post<ReturnItem>(`${this.b(c)}/billing/bank-return-items/${id}/settle`, { entryId }); }
  ignoreReturnItem(c: string, id: string, note: string): Observable<ReturnItem> { return this.http.post<ReturnItem>(`${this.b(c)}/billing/bank-return-items/${id}/ignore`, { note }); }

  transfers(c: string): Observable<Transfer[]> { return this.http.get<Transfer[]>(`${this.b(c)}/finance/transfers`); }
  transfer(c: string, body: Record<string, unknown>): Observable<Transfer> { return this.http.post<Transfer>(`${this.b(c)}/finance/transfers`, body); }
  reverseTransfer(c: string, id: string, reason: string): Observable<Transfer> { return this.http.post<Transfer>(`${this.b(c)}/finance/transfers/${id}/reverse`, { reason }); }
  budget(c: string, year: number): Observable<{ year: number; rows: BudgetRow[] }> { return this.http.get<{ year: number; rows: BudgetRow[] }>(`${this.b(c)}/finance/budget/${year}`); }
  saveBudget(c: string, year: number, cells: { chartAccountId: string; month: number; amount: number }[]): Observable<{ year: number; rows: BudgetRow[] }> {
    return this.http.put<{ year: number; rows: BudgetRow[] }>(`${this.b(c)}/finance/budget/${year}`, { cells });
  }

  suppliers(search: string, page = 0, size = 20): Observable<PaginaResponse<Supplier>> {
    return this.http.get<PaginaResponse<Supplier>>('/api/v1/suppliers', { params: { search, pagina: page, tamanho: size } });
  }
  saveSupplier(body: Record<string, unknown>, id?: string, active?: boolean): Observable<Supplier> {
    return id
      ? this.http.put<Supplier>(`/api/v1/suppliers/${id}`, body, { params: active === undefined ? {} : { active } })
      : this.http.post<Supplier>('/api/v1/suppliers', body);
  }

  allocations(c: string): Observable<Allocation[]> { return this.http.get<Allocation[]>(`${this.b(c)}/billing/allocations`); }
  previewAllocation(c: string, body: AllocationRequest): Observable<AllocationPreview> { return this.http.post<AllocationPreview>(`${this.b(c)}/billing/allocations/preview`, body); }
  generateAllocation(c: string, body: AllocationRequest): Observable<Allocation> { return this.http.post<Allocation>(`${this.b(c)}/billing/allocations`, body); }
  cancelAllocation(c: string, id: string, reason: string): Observable<Allocation> { return this.http.post<Allocation>(`${this.b(c)}/billing/allocations/${id}/cancel`, { reason }); }
  delinquency(c: string, asOf?: string): Observable<Delinquency> { return this.http.get<Delinquency>(`${this.b(c)}/billing/delinquency`, { params: asOf ? { asOf } : {} }); }
  unitDebt(c: string, unitId: string): Observable<UnitDebt> { return this.http.get<UnitDebt>(`${this.b(c)}/billing/units/${unitId}/debt`); }
  addNote(c: string, unitId: string, body: Record<string, unknown>): Observable<Note> { return this.http.post<Note>(`${this.b(c)}/billing/units/${unitId}/notes`, body); }
  agreements(c: string): Observable<Agreement[]> { return this.http.get<Agreement[]>(`${this.b(c)}/billing/agreements`); }
  createAgreement(c: string, body: Record<string, unknown>): Observable<Agreement> { return this.http.post<Agreement>(`${this.b(c)}/billing/agreements`, body); }
  cancelAgreement(c: string, id: string, reason: string): Observable<Agreement> { return this.http.post<Agreement>(`${this.b(c)}/billing/agreements/${id}/cancel`, { reason }); }
}
