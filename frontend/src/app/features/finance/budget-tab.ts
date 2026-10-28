import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { FieldDef, openForm } from '../../shared/ui/form-dialog';
import { BudgetRow, FinanceApi } from './finance-api';

const MONTHS = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

@Component({
  selector: 'tc-budget-tab',
  imports: [CurrencyPipe, DecimalPipe, MatButtonModule, MatIconModule],
  template: `
    <div class="head">
      <button mat-icon-button (click)="year.set(year() - 1)" aria-label="Ano anterior"><mat-icon>chevron_left</mat-icon></button>
      <strong class="year">{{ year() }}</strong>
      <button mat-icon-button (click)="year.set(year() + 1)" aria-label="Próximo ano"><mat-icon>chevron_right</mat-icon></button>
      <span class="tc-muted hint">Previsto × realizado (regime de caixa). {{ canEdit() ? 'Clique em uma conta para editar o previsto mês a mês.' : '' }}</span>
    </div>
    <section class="tc-table-card">
      <div class="table-scroll">
        <table class="lines tc-cards-mobile">
          <thead>
            <tr><th>Conta</th><th class="money">Previsto</th><th class="money">Realizado</th><th class="money">Diferença</th><th>Execução</th></tr>
          </thead>
          <tbody>
            @for (r of rows(); track r.chartAccountId) {
              <tr [class.is-clickable]="canEdit()" (click)="canEdit() && edit(r)" [attr.tabindex]="canEdit() ? 0 : null" (keydown.enter)="canEdit() && edit(r)">
                <td><span class="tc-muted">{{ r.code }}</span> {{ r.name }}</td>
                <td class="money">{{ r.totalBudgeted | currency: 'BRL' }}</td>
                <td class="money">{{ r.totalRealized | currency: 'BRL' }}</td>
                <td class="money" [class.negative]="over(r)">{{ r.totalRealized - r.totalBudgeted | currency: 'BRL' }}</td>
                <td>
                  @if (r.totalBudgeted > 0) {
                    <div class="exec"><div class="meter" [attr.aria-label]="pct(r) + '% executado'"><span [style.width.%]="min(pct(r), 100)" [class.over]="over(r)"></span></div>
                    <small [class.over-text]="over(r)">{{ pct(r) | number: '1.0-0' }}%{{ over(r) ? ' (acima do previsto)' : '' }}</small></div>
                  } @else { <small class="tc-muted">sem previsão</small> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .head { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; flex-wrap: wrap; }
    .year { font: var(--mat-sys-title-large); font-weight: 650; font-variant-numeric: tabular-nums; }
    .hint { flex: 1 1 260px; margin-left: 8px; }
    .exec { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .meter { width: 120px; height: 8px; border-radius: 4px; background: var(--tc-surface-muted); box-shadow: inset 0 0 0 1px var(--tc-border); overflow: hidden; }
    .meter span { display: block; height: 100%; background: var(--mat-sys-primary); border-radius: 4px; }
    .meter span.over { background: var(--tc-danger); }
    .over-text { color: var(--tc-danger); font-weight: 600; }
  `,
})
export class BudgetTab {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly year = signal(new Date().getFullYear());
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('FINANCE_EDIT') && this.context.selected()?.status === 'ACTIVE');
  private readonly data = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.year), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, y]) => this.api.budget(c!, y).pipe(catchError(() => of(null)))),
    ),
  );
  protected readonly rows = computed(() => this.data()?.rows ?? []);

  pct(r: BudgetRow): number {
    return r.totalBudgeted > 0 ? (r.totalRealized / r.totalBudgeted) * 100 : 0;
  }

  over(r: BudgetRow): boolean {
    return r.totalBudgeted > 0 && r.totalRealized > r.totalBudgeted;
  }

  min(a: number, b: number): number {
    return Math.min(a, b);
  }

  edit(r: BudgetRow): void {
    const fields: FieldDef[] = MONTHS.map((m, i) => ({ key: 'm' + (i + 1), label: m, type: 'number', value: r.budgeted[i], span: 3, min: 0 }));
    openForm(this.dialog, {
      title: `Previsto ${this.year()} — ${r.code} ${r.name}`,
      intro: 'Informe o valor previsto de cada mês.',
      fields,
      submit: (v) => this.api.saveBudget(this.context.id()!, this.year(), MONTHS.map((_, i) => ({
        chartAccountId: r.chartAccountId, month: i + 1, amount: Number(v['m' + (i + 1)] ?? 0),
      }))),
    }, '640px').pipe(filter((x) => !!x)).subscribe(() => {
      this.notify.success('Orçamento atualizado.');
      this.tick.update((t) => t + 1);
    });
  }
}
