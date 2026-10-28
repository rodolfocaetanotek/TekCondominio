import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { catchError, filter, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';

interface Week { start: string; end: string; expectedIn: number; expectedOut: number; projectedBalance: number; }
interface CashProjection { asOf: string; currentBalance: number; overdueReceivables: number; overduePayables: number; lowestProjectedBalance: number; lowestOn: string; weeks: Week[]; notes: string[]; }
interface Anomaly { month: string; accountCode: string; accountName: string; value: number; historicalMean: number; ratio?: number; explanation: string; }

/** Projeção de caixa e despesas fora do padrão — calculadas apenas com dados registrados. */
@Component({
  selector: 'tc-insights-tab',
  imports: [CurrencyPipe, DecimalPipe, IsoDatePipe],
  template: `
    @if (projection(); as p) {
      <section class="tc-card">
        <h3 class="tc-section-title">Projeção de caixa — próximas {{ p.weeks.length }} semanas</h3>
        <div class="kpis">
          <div><span class="tc-muted">Saldo atual (todas as contas)</span><strong>{{ p.currentBalance | currency: 'BRL' }}</strong></div>
          <div><span class="tc-muted">Menor saldo projetado</span><strong [class.negative]="p.lowestProjectedBalance < 0">{{ p.lowestProjectedBalance | currency: 'BRL' }}</strong>
            <span class="small">semana de {{ p.lowestOn | isoDate }}</span></div>
          <div><span class="tc-muted">A receber vencido (fora da projeção)</span><strong>{{ p.overdueReceivables | currency: 'BRL' }}</strong></div>
          <div><span class="tc-muted">A pagar vencido (fora da projeção)</span><strong>{{ p.overduePayables | currency: 'BRL' }}</strong></div>
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
          @if (a.ratio) { <strong class="negative">{{ a.ratio | number: '1.1-1' }}×</strong> }</div>
      } @empty {
        <p class="tc-muted">Nenhuma despesa fora do padrão. (É preciso histórico de pelo menos 3 meses com pagamentos na conta.)</p>
      }
    </section>
  `,
  styles: `
    :host { display: grid; gap: 16px; margin-top: 16px; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 12px; }
    .kpis div { display: grid; } .kpis strong { font: var(--mat-sys-title-large); }
    .small { font: var(--mat-sys-body-small); } .scroll { overflow-x: auto; }
    .lines { width: 100%; border-collapse: collapse; }
    .lines th, .lines td { padding: 6px 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); text-align: left; }
    .lines .money { text-align: right; white-space: nowrap; }
    .row { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
  `,
})
export class InsightsTab {
  private readonly context = inject(CondominiumContext);
  private readonly http = inject(HttpClient);
  private readonly condo = toObservable(this.context.id).pipe(filter((c) => !!c));
  protected readonly projection = toSignal(this.condo.pipe(switchMap((c) =>
    this.http.get<CashProjection>(`/api/v1/condominiums/${c}/insights/cash-projection`).pipe(catchError(() => of(null))))));
  protected readonly anomalies = toSignal(this.condo.pipe(switchMap((c) =>
    this.http.get<Anomaly[]>(`/api/v1/condominiums/${c}/insights/expense-anomalies`).pipe(catchError(() => of([] as Anomaly[]))))));
}
