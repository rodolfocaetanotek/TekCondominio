import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { catchError, filter, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { MatIconModule } from '@angular/material/icon';
import { EmptyState } from '../../shared/ui/empty-state';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';

interface Week { start: string; end: string; expectedIn: number; expectedOut: number; projectedBalance: number; }
interface CashProjection { asOf: string; currentBalance: number; overdueReceivables: number; overduePayables: number; lowestProjectedBalance: number; lowestOn: string; weeks: Week[]; notes: string[]; }
interface Anomaly { month: string; accountCode: string; accountName: string; value: number; historicalMean: number; ratio?: number; explanation: string; }

/** Projeção de caixa e despesas fora do padrão — calculadas apenas com dados registrados. */
@Component({
  selector: 'tc-insights-tab',
  imports: [CurrencyPipe, DecimalPipe, MatIconModule, EmptyState, IsoDatePipe],
  template: `
    @if (projection(); as p) {
      <section class="tc-card">
        <h3 class="tc-section-title">Projeção de caixa — próximas {{ p.weeks.length }} semanas</h3>
        <div class="tc-kpis inset">
          <div class="tc-kpi tc-card tone-info">
            <span class="icon"><mat-icon aria-hidden="true">account_balance_wallet</mat-icon></span>
            <span class="label">Saldo atual (todas as contas)</span><span class="value">{{ p.currentBalance | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card" [class.tone-danger]="p.lowestProjectedBalance < 0">
            <span class="icon"><mat-icon aria-hidden="true">{{ p.lowestProjectedBalance < 0 ? 'warning' : 'show_chart' }}</mat-icon></span>
            <span class="label">Menor saldo projetado</span><span class="value">{{ p.lowestProjectedBalance | currency: 'BRL' }}</span>
            <span class="sub">semana de {{ p.lowestOn | isoDate }}</span>
          </div>
          <div class="tc-kpi tc-card tone-success">
            <span class="icon"><mat-icon aria-hidden="true">call_received</mat-icon></span>
            <span class="label">A receber vencido (fora da projeção)</span><span class="value">{{ p.overdueReceivables | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-warning">
            <span class="icon"><mat-icon aria-hidden="true">call_made</mat-icon></span>
            <span class="label">A pagar vencido (fora da projeção)</span><span class="value">{{ p.overduePayables | currency: 'BRL' }}</span>
          </div>
        </div>
        <div class="scroll">
          <table class="lines">
            <thead><tr><th>Semana</th><th class="money">Entradas previstas</th><th class="money">Saídas previstas</th><th class="money">Saldo projetado</th></tr></thead>
            <tbody>
              @for (w of p.weeks; track w.start) {
                <tr><td>{{ w.start | isoDate }} – {{ w.end | isoDate }}</td>
                  <td class="money positive">{{ w.expectedIn | currency: 'BRL' }}</td>
                  <td class="money negative">{{ w.expectedOut | currency: 'BRL' }}</td>
                  <td class="money" [class.negative]="w.projectedBalance < 0"><strong>{{ w.projectedBalance | currency: 'BRL' }}</strong></td></tr>
              }
            </tbody>
          </table>
        </div>
        @for (n of p.notes; track n) { <p class="small tc-muted">{{ n }}</p> }
      </section>
    }
    <section class="tc-card">
      <h3 class="tc-section-title">Despesas fora do padrão (últimos 3 meses encerrados)</h3>
      @for (a of anomalies() ?? []; track a.month + a.accountCode) {
        <div class="row"><div><strong>{{ a.accountCode }} {{ a.accountName }}</strong> · {{ a.month.slice(5) }}/{{ a.month.slice(0, 4) }}<br />
          <span class="small">{{ a.explanation }}</span></div>
          @if (a.ratio) { <strong class="money negative">{{ a.ratio | number: '1.1-1' }}×</strong> }</div>
      } @empty {
        <tc-empty-state icon="insights" title="Nenhuma despesa fora do padrão"
          description="(É preciso histórico de pelo menos 3 meses com pagamentos na conta.)" />
      }
    </section>
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .tc-section-title { margin-top: 0; }
    .small { font: var(--mat-sys-body-small); }
    .scroll { overflow-x: auto; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--tc-border); }
    .row:last-child { border-bottom: 0; }
    .row > div { min-width: 0; }
  `,
})
export class InsightsTab {
  private readonly context = inject(CondominiumContext);
  private readonly http = inject(HttpClient);
  private readonly condo = toObservable(this.context.id).pipe(filter((c) => !!c));
  protected readonly projection = toSignal(this.condo.pipe(switchMap((c) =>
    this.http.get<CashProjection>(`/api/v1/condominios/${c}/insights/cash-projection`).pipe(catchError(() => of(null))))));
  protected readonly anomalies = toSignal(this.condo.pipe(switchMap((c) =>
    this.http.get<Anomaly[]>(`/api/v1/condominios/${c}/insights/expense-anomalies`).pipe(catchError(() => of([] as Anomaly[]))))));
}
