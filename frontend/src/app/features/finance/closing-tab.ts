import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { askReason } from '../../shared/ui/confirm-dialog';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { ClosingStatus, FinanceApi } from './finance-api';

/** Fechamento mensal: congela o período conferido e guarda a fotografia dos saldos para a prestação de contas. */
@Component({
  selector: 'tc-closing-tab',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, IsoDatePipe],
  template: `
    @if (status(); as s) {
      <section class="tc-card state">
        <div>
          <span class="tc-muted">Período fechado até</span>
          <strong>{{ s.closedUntil ? (s.closedUntil | isoDate) : 'Nenhum período fechado' }}</strong>
          <p class="tc-muted small">Após o fechamento, baixas, estornos, transferências, conciliações e lançamentos com data ou
            competência no período ficam bloqueados. A reabertura exige justificativa e fica registrada.</p>
        </div>
        @if (canClose()) {
          <div class="actions">
            @if (s.closedUntil) { <button mat-stroked-button (click)="reopen()"><mat-icon>lock_open</mat-icon> Reabrir</button> }
          </div>
        }
      </section>

      @if (s.suggestedUntil) {
        <section class="tc-card">
          <h3 class="tc-section-title">Conferência para fechar até {{ s.suggestedUntil | isoDate }}</h3>
          @if (check(); as c) {
            <div class="kpis">
              <div><span class="tc-muted">Recebido no período</span><strong class="positive">{{ c.income | currency: 'BRL' }}</strong></div>
              <div><span class="tc-muted">Pago no período</span><strong class="negative">{{ c.expense | currency: 'BRL' }}</strong></div>
              <div><span class="tc-muted">Movimentações não conciliadas</span><strong [class.warn]="c.unreconciledMovements > 0">{{ c.unreconciledMovements }}</strong></div>
              <div><span class="tc-muted">Pagamentos aguardando aprovação</span><strong [class.warn]="c.pendingApprovals > 0">{{ c.pendingApprovals }}</strong></div>
            </div>
            <table class="lines">
              <thead><tr><th>Conta</th><th class="money">Saldo em {{ c.until | isoDate }}</th><th class="money">Saldo conciliado</th></tr></thead>
              <tbody>
                @for (b of c.balances; track b.id) {
                  <tr><td>{{ b.name }}</td><td class="money">{{ b.balance | currency: 'BRL' }}</td>
                    <td class="money" [class.warn]="b.balance !== b.reconciledBalance">{{ b.reconciledBalance | currency: 'BRL' }}</td></tr>
                }
              </tbody>
            </table>
            @if (c.unreconciledMovements > 0 || c.pendingApprovals > 0) {
              <p class="tc-alert" role="status">Há pendências. O fechamento é permitido, mas recomenda-se conciliar o extrato antes.</p>
            }
            @if (canClose()) {
              <button mat-flat-button (click)="close(s.suggestedUntil!)"><mat-icon>lock</mat-icon> Fechar até {{ s.suggestedUntil | isoDate }}</button>
            }
          }
        </section>
      }

      <section class="tc-card">
        <h3 class="tc-section-title">Histórico</h3>
        <table class="lines">
          <thead><tr><th>Quando</th><th>Ação</th><th>Período</th><th>Por</th><th>Observação</th></tr></thead>
          <tbody>
            @for (h of s.history; track h.id) {
              <tr>
                <td>{{ h.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ h.action === 'CLOSE' ? 'Fechamento' : 'Reabertura' }}</td>
                <td>{{ h.action === 'CLOSE' ? 'até ' + (h.closedUntil | isoDate) : 'de ' + (h.previousUntil | isoDate) + ' para ' + (h.closedUntil ? (h.closedUntil | isoDate) : 'aberto') }}</td>
                <td>{{ h.createdByName }}</td>
                <td>{{ h.notes ?? '' }}{{ h.snapshot ? ' · Recebido R$ ' + h.snapshot.income + ' · Pago R$ ' + h.snapshot.expense : '' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="tc-muted">Nenhum fechamento registrado.</td></tr>
            }
          </tbody>
        </table>
      </section>
    }
  `,
  styles: `
    :host { display: grid; gap: 16px; margin-top: 16px; }
    .state { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
    .state strong { display: block; font: var(--mat-sys-headline-small); }
    .small { font: var(--mat-sys-body-small); max-width: 70ch; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; }
    .kpis div { display: grid; } .kpis strong { font: var(--mat-sys-title-large); }
    .warn { color: var(--mat-sys-error); }
    .lines { width: 100%; border-collapse: collapse; margin-bottom: 12px; }
    .lines th, .lines td { padding: 6px 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); text-align: left; }
    .lines .money { text-align: right; }
    section { overflow-x: auto; }
  `,
})
export class ClosingTab {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);

  protected readonly canClose = computed(() => this.context.can('FINANCE_CLOSE'));
  protected readonly status = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c]) => this.api.closing(c!).pipe(catchError(() => of(null as ClosingStatus | null)))),
  ));
  protected readonly check = toSignal(toObservable(this.status).pipe(
    filter((s) => !!s?.suggestedUntil),
    switchMap((s) => this.api.closingCheck(this.context.id()!, s!.suggestedUntil!).pipe(catchError(() => of(null)))),
  ));

  close(until: string): void {
    openForm(this.dialog, {
      title: 'Fechar período',
      intro: 'Confira os saldos com os extratos bancários antes de fechar. Alterações retroativas exigirão reabertura justificada.',
      fields: [{ key: 'notes', label: 'Observação (opcional)', type: 'textarea', maxLength: 500 }],
      submitLabel: 'Fechar período',
      submit: (v) => this.api.close(this.context.id()!, until, (v['notes'] as string) ?? null),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Período fechado.');
      this.tick.update((t) => t + 1);
    });
  }

  reopen(): void {
    askReason(this.dialog, 'Reabrir período', 'Todo o período fechado será reaberto. Informe o motivo (fica na auditoria).', 'Reabrir')
      .pipe(switchMap((reason) => this.api.reopen(this.context.id()!, null, reason)))
      .subscribe({
        next: () => {
          this.notify.success('Período reaberto.');
          this.tick.update((t) => t + 1);
        },
        error: (err: unknown) => this.notify.error(toProblem(err).detail),
      });
  }
}
