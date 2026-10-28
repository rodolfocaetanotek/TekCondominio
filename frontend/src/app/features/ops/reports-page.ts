import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, filter, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { FinanceApi } from '../finance/finance-api';
import { OpsApi, ReportTable } from './ops-api';

const REPORTS = [
  { kind: 'INCOME_EXPENSE', label: 'Receitas e despesas', group: 'Financeiro' },
  { kind: 'BALANCE_SHEET', label: 'Balancete financeiro', group: 'Financeiro' },
  { kind: 'CASH_FLOW', label: 'Fluxo de caixa', group: 'Financeiro' },
  { kind: 'PAYMENTS', label: 'Pagamentos realizados', group: 'Financeiro' },
  { kind: 'PAYABLES', label: 'Contas a pagar', group: 'Financeiro' },
  { kind: 'RECEIVABLES', label: 'Contas a receber', group: 'Financeiro' },
  { kind: 'BANK_MOVEMENT', label: 'Movimentação por conta bancária', group: 'Financeiro' },
  { kind: 'BUDGET', label: 'Orçamento previsto × realizado', group: 'Financeiro' },
  { kind: 'EXPENSES_BY_SUPPLIER', label: 'Despesas por fornecedor', group: 'Financeiro' },
  { kind: 'DELINQUENCY', label: 'Inadimplência', group: 'Cobrança' },
  { kind: 'ALLOCATIONS', label: 'Rateios', group: 'Cobrança' },
  { kind: 'OCCURRENCES', label: 'Histórico de ocorrências', group: 'Administração' },
  { kind: 'CONTRACTS', label: 'Contratos (vigentes, a vencer, vencidos)', group: 'Administração' },
];

@Component({
  selector: 'tc-reports-page',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    PageHeader, CondominiumRequired, EmptyState],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Análise" title="Relatórios" [subtitle]="context.selected()?.name" />
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <section class="tc-card filters">
          <mat-form-field class="search kind"><mat-label>Relatório</mat-label>
            <mat-select [formControl]="kind">
              @for (r of reports; track r.kind) { <mat-option [value]="r.kind">{{ r.group }} · {{ r.label }}</mat-option> }
            </mat-select></mat-form-field>
          @if (kind.value === 'BUDGET') {
            <mat-form-field><mat-label>Ano</mat-label><input matInput type="number" [formControl]="year" /></mat-form-field>
          } @else {
            <mat-form-field><mat-label>{{ kind.value === 'DELINQUENCY' ? 'Posição em' : 'De' }}</mat-label>
              <input matInput type="date" [formControl]="kind.value === 'DELINQUENCY' ? to : from" /></mat-form-field>
            @if (kind.value !== 'DELINQUENCY' && kind.value !== 'CONTRACTS') {
              <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [formControl]="to" /></mat-form-field>
            }
          }
          @if (kind.value === 'BANK_MOVEMENT') {
            <mat-form-field><mat-label>Conta</mat-label><mat-select [formControl]="bank">
              @for (b of banks(); track b.id) { <mat-option [value]="b.id">{{ b.name }}</mat-option> }</mat-select></mat-form-field>
          }
          <button mat-flat-button class="run" (click)="run()"><mat-icon>play_arrow</mat-icon> Gerar</button>
          @if (canExport() && table()) {
            <span class="exports" role="group" aria-label="Exportar relatório">
              <button mat-stroked-button (click)="export('PDF')"><mat-icon>picture_as_pdf</mat-icon> PDF</button>
              <button mat-stroked-button (click)="export('XLSX')"><mat-icon>table_view</mat-icon> Excel</button>
              <button mat-stroked-button (click)="export('CSV')"><mat-icon>description</mat-icon> CSV</button>
            </span>
          }
        </section>
        @if (loading()) { <mat-progress-bar class="loading" mode="indeterminate" /> }
        @if (error(); as message) {
          <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível gerar" [description]="message" /></section>
        } @else if (table(); as t) {
          <section class="tc-card report">
            <header class="report-head">
              <h2 class="tc-section-title">{{ t.title }}</h2>
              @if (t.subtitle) { <p class="tc-muted">{{ t.subtitle }}</p> }
            </header>
            <div class="table-scroll" tabindex="0" role="region" [attr.aria-label]="t.title">
              <table class="lines">
                <thead><tr>@for (c of t.columns; track c.key) { <th [class.num]="isNum(c.type)">{{ c.label }}</th> }</tr></thead>
                <tbody>
                  @for (r of t.rows; track $index) {
                    <tr>@for (c of t.columns; track c.key; let i = $index) { <td [class.num]="isNum(c.type)">{{ fmt(r[i], c.type) }}</td> }</tr>
                  } @empty { <tr><td [attr.colspan]="t.columns.length" class="tc-muted empty">Nenhum registro no período.</td></tr> }
                </tbody>
                @if (t.totals) {
                  <tfoot><tr>@for (c of t.columns; track c.key; let i = $index) { <td [class.num]="isNum(c.type)">{{ fmt(t.totals[i], c.type) }}</td> }</tr></tfoot>
                }
              </table>
            </div>
            @for (n of t.notes ?? []; track n) { <p class="tc-muted small">{{ n }}</p> }
          </section>
        }
      }
    </div>
  `,
  styles: `
    .filters { padding-block: 16px 4px; }
    .kind { flex-basis: 320px; }
    .exports { display: flex; gap: 8px; flex-wrap: wrap; margin-left: auto; margin-bottom: 18px; }
    .run { margin-bottom: 18px; }
    .loading { margin-bottom: 12px; border-radius: 4px; }
    .report { min-width: 0; padding: 0; overflow: hidden; }
    .report-head { padding: 18px 20px 14px; border-bottom: 1px solid var(--tc-border); }
    .report-head h2 { margin: 0; }
    .report-head p { margin: 4px 0 0; }
    .table-scroll { overflow-x: auto; max-width: 100%; }
    .table-scroll:focus-visible { outline-offset: -2px; }
    table.lines th:first-child, table.lines td:first-child { padding-left: 20px; }
    table.lines th:last-child, table.lines td:last-child { padding-right: 20px; }
    .num { text-align: right !important; font-variant-numeric: tabular-nums; white-space: nowrap; }
    tfoot td { font-weight: 700; padding: 10px 12px; background: var(--tc-surface-muted); border-top: 2px solid var(--tc-border-strong); }
    .empty { text-align: center !important; padding-block: 24px !important; }
    .small { font: var(--mat-sys-body-small); margin: 0; padding: 10px 20px 0; }
    .small:last-child { padding-bottom: 16px; }
    @media (max-width: 600px) {
      .run, .exports { flex: 1 1 100%; margin-left: 0; }
      .exports > button { flex: 1 1 0; }
      .report-head { padding: 14px 16px 12px; }
    }
  `,
})
export class ReportsPage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly notify = inject(NotificationService);
  protected readonly reports = REPORTS;
  protected readonly kind = new FormControl('INCOME_EXPENSE', { nonNullable: true });
  protected readonly from = new FormControl(todayIso().slice(0, 8) + '01', { nonNullable: true });
  protected readonly to = new FormControl(todayIso(), { nonNullable: true });
  protected readonly year = new FormControl(new Date().getFullYear(), { nonNullable: true });
  protected readonly bank = new FormControl('', { nonNullable: true });
  protected readonly table = signal<ReportTable | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canExport = computed(() => this.context.can('REPORT_EXPORT'));
  private readonly finance = inject(FinanceApi);
  protected readonly banks = toSignal(toObservable(this.context.id).pipe(filter((c) => !!c),
    switchMap((c) => this.finance.bankAccounts(c!).pipe(catchError(() => of([]))))), { initialValue: [] });

  private params(): Record<string, string> {
    const k = this.kind.value;
    if (k === 'BUDGET') return { year: String(this.year.value) };
    const p: Record<string, string> = { from: this.from.value, to: this.to.value };
    if (k === 'BANK_MOVEMENT' && this.bank.value) p['bankAccountId'] = this.bank.value;
    return p;
  }

  run(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.report(this.context.id()!, this.kind.value, this.params()).pipe(map((t) => t)).subscribe({
      next: (t) => { this.loading.set(false); this.table.set(t); },
      error: (err: unknown) => { this.loading.set(false); this.table.set(null); this.error.set(toProblem(err).detail); },
    });
  }

  export(format: 'PDF' | 'XLSX' | 'CSV'): void {
    this.api.exportReport(this.context.id()!, this.kind.value, { ...this.params(), format })
      .subscribe({ error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  isNum(type: string): boolean {
    return type === 'MONEY' || type === 'NUMBER' || type === 'PERCENT';
  }

  fmt(v: unknown, type: string): string {
    if (v === null || v === undefined || v === '') return '';
    if (type === 'MONEY' && typeof v === 'number') return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    if (type === 'PERCENT' && typeof v === 'number') return v.toLocaleString('pt-BR', { maximumFractionDigits: 2 }) + '%';
    if (type === 'NUMBER' && typeof v === 'number') return v.toLocaleString('pt-BR');
    if (type === 'DATE' && typeof v === 'string') return v.slice(8, 10) + '/' + v.slice(5, 7) + '/' + v.slice(0, 4);
    return String(v);
  }
}
