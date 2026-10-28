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
import { SituacaoFechamento, FinanceApi } from './finance-api';

/** Fechamento mensal: congela o período conferido e guarda a fotografia dos saldos para a prestação de contas. */
@Component({
  selector: 'tc-closing-tab',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, IsoDatePipe],
  template: `
    @if (status(); as s) {
      <section class="tc-card state">
        <div>
          <span class="tc-section-label">Período fechado até</span>
          <strong>{{ s.fechadoAte ? (s.fechadoAte | isoDate) : 'Nenhum período fechado' }}</strong>
          <p class="tc-muted small">Após o fechamento, baixas, estornos, transferências, conciliações e lançamentos com data ou
            competência no período ficam bloqueados. A reabertura exige justificativa e fica registrada.</p>
        </div>
        @if (canClose()) {
          <div class="actions">
            @if (s.fechadoAte) { <button mat-stroked-button (click)="reopen()"><mat-icon>lock_open</mat-icon> Reabrir</button> }
          </div>
        }
      </section>

      @if (s.sugestaoAte) {
        <section class="tc-card">
          <h3 class="tc-section-title">Conferência para fechar até {{ s.sugestaoAte | isoDate }}</h3>
          @if (check(); as c) {
            <div class="tc-kpis inset">
              <div class="tc-kpi tc-card tone-success">
                <span class="icon"><mat-icon aria-hidden="true">trending_up</mat-icon></span>
                <span class="label">Recebido no período</span><span class="value">{{ c.receitas | currency: 'BRL' }}</span>
              </div>
              <div class="tc-kpi tc-card tone-warning">
                <span class="icon"><mat-icon aria-hidden="true">trending_down</mat-icon></span>
                <span class="label">Pago no período</span><span class="value">{{ c.despesas | currency: 'BRL' }}</span>
              </div>
              <div class="tc-kpi tc-card" [class.tone-danger]="c.movimentacoesNaoConciliadas > 0" [class.tone-success]="c.movimentacoesNaoConciliadas === 0">
                <span class="icon"><mat-icon aria-hidden="true">{{ c.movimentacoesNaoConciliadas > 0 ? 'rule' : 'fact_check' }}</mat-icon></span>
                <span class="label">Movimentações não conciliadas</span><span class="value">{{ c.movimentacoesNaoConciliadas }}</span>
              </div>
              <div class="tc-kpi tc-card" [class.tone-danger]="c.aprovacoesPendentes > 0" [class.tone-success]="c.aprovacoesPendentes === 0">
                <span class="icon"><mat-icon aria-hidden="true">pending_actions</mat-icon></span>
                <span class="label">Pagamentos aguardando aprovação</span><span class="value">{{ c.aprovacoesPendentes }}</span>
              </div>
            </div>
            <table class="lines tc-cards-mobile">
              <thead><tr><th>Conta</th><th class="money">Saldo em {{ c.ate | isoDate }}</th><th class="money">Saldo conciliado</th></tr></thead>
              <tbody>
                @for (b of c.saldos; track b.id) {
                  <tr><td>{{ b.nome }}</td><td class="money">{{ b.saldo | currency: 'BRL' }}</td>
                    <td class="money" [class.warn]="b.saldo !== b.saldoConciliado">{{ b.saldoConciliado | currency: 'BRL' }}</td></tr>
                }
              </tbody>
            </table>
            @if (c.movimentacoesNaoConciliadas > 0 || c.aprovacoesPendentes > 0) {
              <p class="tc-alert" role="status">Há pendências. O fechamento é permitido, mas recomenda-se conciliar o extrato antes.</p>
            }
            @if (canClose()) {
              <div class="tc-actions">
                <button mat-flat-button (click)="close(s.sugestaoAte!)"><mat-icon>lock</mat-icon> Fechar até {{ s.sugestaoAte | isoDate }}</button>
              </div>
            }
          }
        </section>
      }

      <section class="tc-card">
        <h3 class="tc-section-title">Histórico</h3>
        <table class="lines tc-cards-mobile">
          <thead><tr><th>Quando</th><th>Ação</th><th>Período</th><th>Por</th><th>Observação</th></tr></thead>
          <tbody>
            @for (h of s.historico; track h.id) {
              <tr>
                <td>{{ h.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ h.acao === 'CLOSE' ? 'Fechamento' : 'Reabertura' }}</td>
                <td>{{ h.acao === 'CLOSE' ? 'até ' + (h.fechadoAte | isoDate) : 'de ' + (h.fechadoAteAnterior | isoDate) + ' para ' + (h.fechadoAte ? (h.fechadoAte | isoDate) : 'aberto') }}</td>
                <td>{{ h.nomeCriadoPor }}</td>
                <td>{{ h.observacoes ?? '' }}{{ h.fotografia ? ' · Recebido R$ ' + h.fotografia.income + ' · Pago R$ ' + h.fotografia.expense : '' }}</td>
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
    :host { display: grid; gap: 16px; }
    .state { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
    .state .tc-section-label { display: block; margin-bottom: 4px; }
    .state strong { display: block; font: var(--mat-sys-headline-small); font-weight: 650; }
    .small { font: var(--mat-sys-body-small); max-width: 70ch; }
    .tc-section-title { margin-top: 0; }
    .warn { color: var(--tc-danger); font-weight: 600; }
    .lines { margin-bottom: 12px; }
    .tc-alert { margin: 0 0 12px; }
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
    switchMap(([c]) => this.api.closing(c!).pipe(catchError(() => of(null as SituacaoFechamento | null)))),
  ));
  protected readonly check = toSignal(toObservable(this.status).pipe(
    filter((s) => !!s?.sugestaoAte),
    switchMap((s) => this.api.closingCheck(this.context.id()!, s!.sugestaoAte!).pipe(catchError(() => of(null)))),
  ));

  close(until: string): void {
    openForm(this.dialog, {
      title: 'Fechar período',
      intro: 'Confira os saldos com os extratos bancários antes de fechar. Alterações retroativas exigirão reabertura justificada.',
      fields: [{ key: 'observacoes', label: 'Observação (opcional)', type: 'textarea', maxLength: 500 }],
      submitLabel: 'Fechar período',
      submit: (v) => this.api.close(this.context.id()!, until, (v['observacoes'] as string) ?? null),
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
