import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { BankAccount, FinanceApi, Statement } from './finance-api';

@Component({
  selector: 'tc-bank-tab',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatCheckboxModule, MatFormFieldModule, MatInputModule,
    LabelPipe, IsoDatePipe, EmptyState],
  template: `
    <div class="head">
      <p class="tc-muted">Saldos calculados a partir do saldo inicial e de todas as movimentações registradas.</p>
      @if (canEdit()) {
        <button mat-flat-button (click)="newAccount()"><mat-icon>add</mat-icon> Nova conta</button>
      }
    </div>
    <div class="accounts">
      @for (a of accounts() ?? []; track a.id) {
        <button type="button" class="account tc-kpi tc-card" [class.tone-danger]="a.balance < 0" [class.selected]="selected() === a.id"
          [class.inactive]="!a.active" [attr.aria-pressed]="selected() === a.id" (click)="selected.set(a.id)">
          <span class="icon"><mat-icon aria-hidden="true">{{ a.kind === 'CASH' ? 'payments' : 'account_balance' }}</mat-icon></span>
          <span class="label">{{ a.name }}</span>
          <span class="value money" [class.negative]="a.balance < 0">{{ a.balance | currency: 'BRL' }}</span>
          <span class="sub">{{ a.kind | label: 'bankKind' }}{{ a.bankCode ? ' · ' + a.bankCode + ' ag. ' + a.agency + ' c/c ' + a.accountNumber : '' }}</span>
        </button>
      } @empty {
        <section class="tc-card empty">
          <tc-empty-state icon="account_balance" title="Nenhuma conta cadastrada" description="Cadastre a conta corrente e o caixa do condomínio para registrar pagamentos." />
        </section>
      }
    </div>

    @if (selected()) {
      <section class="tc-card statement">
        <div class="filters">
          <h3 class="tc-section-title">Extrato e conciliação</h3>
          <mat-form-field><mat-label>De</mat-label><input matInput type="date" [formControl]="from" /></mat-form-field>
          <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [formControl]="to" /></mat-form-field>
        </div>
        @if (statement(); as s) {
          <div class="summary">
            <div><span class="tc-section-label">Saldo anterior</span><strong class="money">{{ s.openingBalance | currency: 'BRL' }}</strong></div>
            <div><span class="tc-section-label">Saldo final</span><strong class="money">{{ s.closingBalance | currency: 'BRL' }}</strong></div>
            <div><span class="tc-section-label">Saldo conciliado</span><strong class="money">{{ s.reconciledBalance | currency: 'BRL' }}</strong></div>
          </div>
          <div class="scroll">
            <table class="lines">
              <thead><tr><th>Conciliado</th><th>Data</th><th>Histórico</th><th class="money">Valor</th><th class="money">Saldo</th></tr></thead>
              <tbody>
                @for (l of s.lines; track l.id + l.type) {
                  <tr>
                    <td><mat-checkbox [checked]="l.reconciled" [disabled]="!canEdit()" (change)="toggle(l.type, l.id, $event.checked)"
                      [attr.aria-label]="'Conciliar ' + l.description" /></td>
                    <td>{{ l.date | isoDate }}</td>
                    <td>{{ l.description }}</td>
                    <td class="money" [class.negative]="l.amount < 0" [class.positive]="l.amount > 0">{{ l.amount | currency: 'BRL' }}</td>
                    <td class="money">{{ l.balance | currency: 'BRL' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="tc-muted">Sem movimentações no período.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <p class="tc-muted small">Marque as movimentações conferidas no extrato do banco. Movimentações conciliadas não podem ser estornadas sem desfazer a conciliação.</p>
        }
      </section>
    }
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 0 16px; flex-wrap: wrap; }
    .head p { margin: 0; }
    .accounts { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(240px, 100%), 1fr)); gap: 12px; margin-bottom: 16px; }
    .accounts .empty { grid-column: 1 / -1; }
    .account { width: 100%; text-align: left; cursor: pointer; font: inherit; color: inherit; }
    .account:hover { border-color: color-mix(in srgb, var(--mat-sys-primary) 45%, var(--tc-border)); }
    .account.selected { border-color: var(--mat-sys-primary); box-shadow: 0 0 0 1px var(--mat-sys-primary); }
    .account.inactive { opacity: .6; }
    .account .value { text-align: left; }
    .account .label, .account .sub { overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); }
    .filters h3 { margin: 0; flex: 1 1 auto; }
    .summary { display: flex; gap: 12px 32px; flex-wrap: wrap; margin: 0 0 16px; padding: 12px 16px; border-radius: var(--tc-radius-sm);
      background: var(--tc-surface-muted); border: 1px solid var(--tc-border); }
    .summary div { display: grid; gap: 2px; }
    .summary .tc-section-label { margin: 0; }
    .summary strong { font: var(--mat-sys-title-medium); font-weight: 650; text-align: left; }
    .scroll { overflow-x: auto; }
  `,
})
export class BankTab {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);

  protected readonly canEdit = computed(() => this.context.can('FINANCE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly selected = signal<string | null>(null);
  private readonly tick = signal(0);
  protected readonly from = new FormControl(todayIso().slice(0, 8) + '01', { nonNullable: true });
  protected readonly to = new FormControl(todayIso(), { nonNullable: true });
  private readonly fromValue = toSignal(this.from.valueChanges, { initialValue: this.from.value });
  private readonly toValue = toSignal(this.to.valueChanges, { initialValue: this.to.value });

  protected readonly accounts = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c]) => this.api.bankAccounts(c!).pipe(catchError(() => of([] as BankAccount[])))),
    ),
  );

  protected readonly statement = toSignal(
    combineLatest([toObservable(this.selected), toObservable(this.fromValue), toObservable(this.toValue), toObservable(this.tick)]).pipe(
      filter(([id]) => !!id && !!this.context.id()),
      switchMap(([id, from, to]) => this.api.statement(this.context.id()!, id!, from, to).pipe(catchError(() => of(null as Statement | null)))),
    ),
  );

  newAccount(): void {
    openForm(this.dialog, {
      title: 'Nova conta bancária ou caixa',
      intro: 'O saldo inicial e a data de abertura não podem ser alterados depois (integridade do histórico).',
      fields: [
        { key: 'name', label: 'Nome', required: true, span: 8, maxLength: 80 },
        { key: 'kind', label: 'Tipo', type: 'select', required: true, value: 'CHECKING', span: 4, options: options('bankKind') },
        { key: 'bankCode', label: 'Banco (código)', span: 4, maxLength: 5 },
        { key: 'agency', label: 'Agência', span: 4, maxLength: 10 },
        { key: 'accountNumber', label: 'Conta', span: 4, maxLength: 20 },
        { key: 'openingBalance', label: 'Saldo inicial (R$)', type: 'number', value: 0, span: 6 },
        { key: 'openingDate', label: 'Data do saldo inicial', type: 'date', value: todayIso(), span: 6 },
      ],
      submit: (v) => this.api.createBankAccount(this.context.id()!, v),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Conta cadastrada.');
      this.tick.update((t) => t + 1);
    });
  }

  toggle(type: string, id: string, reconciled: boolean): void {
    this.api.reconcile(this.context.id()!, [{ type, id }], reconciled).subscribe({
      next: () => this.tick.update((t) => t + 1),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }
}
