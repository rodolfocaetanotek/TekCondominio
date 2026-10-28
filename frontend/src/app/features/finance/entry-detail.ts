import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { askReason } from '../../shared/ui/confirm-dialog';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Attachments } from '../ops/attachments';
import { StructureService } from '../structure/structure.service';
import { openEntryForm } from './entry-form';
import { EntryDetail, FinanceApi, Payment } from './finance-api';

@Component({
  selector: 'tc-entry-detail',
  imports: [CurrencyPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, PageHeader, EmptyState, IsoDatePipe,
    LabelPipe, Attachments],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/financeiro" class="back"><mat-icon>arrow_back</mat-icon> Financeiro</a>
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível abrir o lançamento" [description]="message" /></section>
      } @else if (entry(); as e) {
        <tc-page-header [eyebrow]="(e.summary.kind | label: 'entryKind') + (e.summary.installmentLabel ? ' · parcela ' + e.summary.installmentLabel : '')"
          [title]="e.summary.description" [subtitle]="e.summary.accountCode + ' ' + e.summary.accountName">
          <span class="tc-status big" [class.is-active]="e.summary.status === 'PAID'"
            [class.is-pending]="e.summary.approval === 'PENDING' || (!e.summary.overdue && (e.summary.status === 'OPEN' || e.summary.status === 'PARTIAL'))"
            [class.is-danger]="e.summary.approval !== 'PENDING' && e.summary.overdue"
            [class.is-inactive]="e.summary.status === 'CANCELLED' || e.summary.status === 'RENEGOTIATED'">
            {{ e.summary.approval === 'PENDING' ? 'Aguardando aprovação' : (e.summary.status | label: 'entryStatus') }}
          </span>
        </tc-page-header>

        <section class="tc-kpis" aria-label="Valores do lançamento">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">receipt_long</mat-icon></span>
            <span class="label">Valor</span><span class="value">{{ e.summary.amount | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-success">
            <span class="icon"><mat-icon aria-hidden="true">task_alt</mat-icon></span>
            <span class="label">{{ e.summary.kind === 'INCOME' ? 'Recebido' : 'Pago' }}</span><span class="value">{{ e.summary.paidAmount | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card" [class.tone-danger]="e.summary.overdue" [class.tone-warning]="!e.summary.overdue && e.summary.remaining > 0">
            <span class="icon"><mat-icon aria-hidden="true">{{ e.summary.overdue ? 'warning' : 'schedule' }}</mat-icon></span>
            <span class="label">Em aberto</span><span class="value">{{ e.summary.remaining | currency: 'BRL' }}</span>
          </div>
        </section>

        <section class="facts tc-dl cols tc-card">
          <div><span class="k">Vencimento</span><strong [class.late]="e.summary.overdue">{{ e.summary.dueDate | isoDate }}</strong></div>
          <div><span class="k">Competência</span><strong>{{ e.summary.competence.slice(5, 7) }}/{{ e.summary.competence.slice(0, 4) }}</strong></div>
          @if (e.summary.supplierName) { <div><span class="k">Fornecedor</span><strong>{{ e.summary.supplierName }}</strong></div> }
          @if (e.summary.unitLabel) { <div><span class="k">Unidade</span><strong>{{ e.summary.unitLabel }}</strong></div> }
          @if (e.summary.personName) { <div><span class="k">Pagador</span><strong>{{ e.summary.personName }}</strong></div> }
          @if (e.summary.documentNumber) { <div><span class="k">Documento</span><strong>{{ e.summary.documentNumber }}</strong></div> }
          @if (e.costCenterName) { <div><span class="k">Centro de custo</span><strong>{{ e.costCenterName }}</strong></div> }
          @if (e.finePercent !== null && e.finePercent !== undefined) { <div><span class="k">Encargos</span><strong>multa {{ e.finePercent }}% · juros {{ e.interestMonthlyPercent }}% a.m.</strong></div> }
          @if (e.notes) { <div class="wide"><span class="k">Observações</span><span>{{ e.notes }}</span></div> }
          @if (e.approvalNote) { <div class="wide"><span class="k">Parecer da aprovação</span><span>{{ e.approvalNote }}</span></div> }
          @if (e.cancelReason) { <div class="wide"><span class="k">Motivo</span><span>{{ e.cancelReason }}</span></div> }
        </section>

        <div class="tc-actions actions">
          @if (e.summary.approval === 'PENDING' && context.can('FINANCE_APPROVE')) {
            <button mat-stroked-button (click)="decide(e, false)">Reprovar</button>
            <button mat-flat-button (click)="decide(e, true)"><mat-icon>verified</mat-icon> Aprovar pagamento</button>
          }
          @if (open(e) && context.can('FINANCE_EDIT') && e.summary.approval !== 'PENDING' && e.summary.approval !== 'REJECTED') {
            <button mat-flat-button (click)="pay(e)"><mat-icon>payments</mat-icon> Registrar {{ e.summary.kind === 'INCOME' ? 'recebimento' : 'pagamento' }}</button>
          }
          @if (open(e) && e.summary.paidAmount === 0 && context.can('FINANCE_EDIT')) {
            <button mat-stroked-button (click)="edit(e)"><mat-icon>edit</mat-icon> Editar</button>
          }
          @if (open(e) && e.summary.paidAmount === 0 && context.can('FINANCE_CANCEL')) {
            <button mat-button class="danger-text" (click)="cancel(e)">Cancelar lançamento</button>
          }
        </div>

        <div class="layout">
          <section class="tc-table-card">
            <h3 class="tc-section-title tc-card-head">Baixas</h3>
            @for (p of e.payments; track p.id) {
              <div class="pay" [class.reversed]="p.reversed">
                <div class="who">
                  <strong>{{ p.total | currency: 'BRL' }}</strong>
                  <span class="tc-muted">{{ p.paidOn | isoDate }} · {{ p.method | label: 'paymentMethod' }} · {{ p.bankAccountName }}
                    @if (p.fine + p.interest > 0) { · encargos {{ p.fine + p.interest | currency: 'BRL' }} }
                    @if (p.discount > 0) { · desconto {{ p.discount | currency: 'BRL' }} }
                  </span>
                  @if (p.reversed) { <span class="tc-muted">Estornada: {{ p.reversalReason }}</span> }
                </div>
                @if (p.reconciled) { <span class="tc-chip-soft">Conciliada</span> }
                @if (!p.reversed && context.can('FINANCE_CANCEL')) {
                  <button mat-button (click)="reverse(p)">Estornar</button>
                }
              </div>
            } @empty {
              <tc-empty-state icon="payments" title="Nenhuma baixa registrada" />
            }
          </section>
          <tc-attachments [condominiumId]="context.id()!" entityType="FinancialEntry" [entityId]="e.summary.id"
            [category]="e.summary.kind === 'EXPENSE' ? 'INVOICE' : 'RECEIPT'" [canEdit]="context.can('FINANCE_EDIT')" />
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .facts { gap: 16px; }
    .late { color: var(--tc-danger); }
    .actions { margin: 16px 0; justify-content: flex-start; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .pay { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; padding: 12px 20px; border-top: 1px solid var(--tc-border); }
    .pay.reversed { opacity: .6; text-decoration: line-through; }
    .who { flex: 1 1 220px; display: grid; gap: 2px; min-width: 0; }
    .who strong { font-variant-numeric: tabular-nums; }
    .danger-text { --mat-button-text-label-text-color: var(--tc-danger); }
  `,
})
export class EntryDetailPage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  readonly id = input.required<string>();
  private readonly tick = signal(0);
  protected readonly error = signal<string | null>(null);

  protected readonly entry = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.id), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, id]) => this.api.entry(c!, id).pipe(catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); }))),
    ),
  );
  protected readonly c = computed(() => this.context.id()!);

  open(e: EntryDetail): boolean {
    return e.summary.status === 'OPEN' || e.summary.status === 'PARTIAL';
  }

  decide(e: EntryDetail, approve: boolean): void {
    openForm(this.dialog, {
      title: approve ? 'Aprovar pagamento' : 'Reprovar pagamento',
      intro: `${e.summary.description} — R$ ${e.summary.amount.toFixed(2).replace('.', ',')}`,
      fields: [{ key: 'note', label: approve ? 'Parecer (opcional)' : 'Motivo da reprovação', type: 'textarea', required: !approve, maxLength: 255 }],
      submitLabel: approve ? 'Aprovar' : 'Reprovar',
      submit: (v) => this.api.approve(this.c(), e.summary.id, approve, (v['note'] as string) ?? null),
    }, '460px').pipe(filter((r) => !!r)).subscribe(() => this.done(approve ? 'Pagamento aprovado.' : 'Pagamento reprovado.'));
  }

  pay(e: EntryDetail): void {
    const today = todayIso();
    this.api.bankAccounts(this.c()).pipe(
      switchMap((accounts) => this.api.quote(this.c(), e.summary.id, today).pipe(switchMap((q) => {
        const income = e.summary.kind === 'INCOME';
        return openForm(this.dialog, {
          title: income ? 'Registrar recebimento' : 'Registrar pagamento',
          intro: q.daysLate > 0
            ? `Em atraso há ${q.daysLate} dia(s). Encargos calculados para hoje: multa R$ ${q.fine.toFixed(2)} e juros R$ ${q.interest.toFixed(2)} (ajuste se a data for outra).`
            : 'Baixa parcial é permitida: informe o valor principal pago.',
          fields: [
            { key: 'bankAccountId', label: 'Conta', type: 'select', required: true, span: 6, value: accounts.find((a) => a.active)?.id,
              options: accounts.filter((a) => a.active).map((a) => ({ value: a.id, label: a.name })) },
            { key: 'paidOn', label: 'Data', type: 'date', required: true, value: today, span: 6 },
            { key: 'principal', label: 'Valor principal (R$)', type: 'number', required: true, value: e.summary.remaining, span: 6, min: 0.01 },
            { key: 'method', label: 'Forma', type: 'select', required: true, value: 'PIX', span: 6, options: options('paymentMethod') },
            { key: 'fine', label: 'Multa (R$)', type: 'number', value: income ? q.fine : 0, span: 4, min: 0 },
            { key: 'interest', label: 'Juros (R$)', type: 'number', value: income ? q.interest : 0, span: 4, min: 0 },
            { key: 'discount', label: 'Desconto (R$)', type: 'number', value: 0, span: 4, min: 0 },
            { key: 'notes', label: 'Observação', maxLength: 255 },
          ],
          submitLabel: 'Registrar baixa',
          submit: (v) => this.api.pay(this.c(), e.summary.id, v),
        }, '620px') as Observable<unknown>;
      }))),
      filter((r) => !!r),
    ).subscribe({ next: () => this.done('Baixa registrada.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  edit(e: EntryDetail): void {
    openEntryForm(this.dialog, this.api, this.structure, this.c(), e.summary.kind, e).pipe(filter((r) => !!r))
      .subscribe(() => this.done('Lançamento atualizado.'));
  }

  cancel(e: EntryDetail): void {
    askReason(this.dialog, 'Cancelar lançamento', `"${e.summary.description}" será cancelado. O registro é mantido no histórico.`, 'Cancelar lançamento')
      .pipe(switchMap((reason) => this.api.cancelEntry(this.c(), e.summary.id, reason)))
      .subscribe({ next: () => this.done('Lançamento cancelado.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  reverse(p: Payment): void {
    askReason(this.dialog, 'Estornar baixa', `Estornar a baixa de R$ ${p.total.toFixed(2).replace('.', ',')} em ${p.paidOn}?`, 'Estornar')
      .pipe(switchMap((reason) => this.api.reversePayment(this.c(), p.id, reason)))
      .subscribe({ next: () => this.done('Baixa estornada.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  private done(message: string): void {
    this.notify.success(message);
    this.tick.update((t) => t + 1);
  }
}
