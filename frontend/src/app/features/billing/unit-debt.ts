import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
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
import { PageHeader } from '../../shared/ui/page-header';
import { FinanceApi, UnitDebt } from '../finance/finance-api';

@Component({
  selector: 'tc-unit-debt',
  imports: [CurrencyPipe, RouterLink, MatButtonModule, MatIconModule, MatCheckboxModule, MatProgressBarModule, PageHeader, EmptyState,
    IsoDatePipe, LabelPipe],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/cobranca" class="back"><mat-icon>arrow_back</mat-icon> Cobrança</a>
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível carregar" [description]="message" /></section>
      } @else if (debt(); as d) {
        <tc-page-header eyebrow="Demonstrativo de débitos" [title]="'Unidade ' + d.unitLabel"
          [subtitle]="(d.responsibleName ?? 'sem responsável financeiro') + ' · posição em ' + (d.asOf | isoDate)" />
        <section class="kpis">
          <div class="tc-card kpi"><span class="tc-muted">Principal em aberto</span><strong>{{ d.openPrincipal | currency: 'BRL' }}</strong></div>
          <div class="tc-card kpi"><span class="tc-muted">Multa + juros</span><strong>{{ d.openCharges | currency: 'BRL' }}</strong></div>
          <div class="tc-card kpi"><span class="tc-muted">Total atualizado</span><strong class="negative">{{ d.openTotal | currency: 'BRL' }}</strong></div>
        </section>

        <section class="tc-table-card">
          <div class="head">
            <h3 class="tc-section-title">Cobranças</h3>
            @if (canEdit() && selected().length) {
              <button mat-flat-button (click)="agreement(d)"><mat-icon>handshake</mat-icon> Negociar {{ selected().length }} débito(s)</button>
            }
          </div>
          <div class="table-scroll">
            <table class="lines">
              <thead><tr><th></th><th>Vencimento</th><th>Descrição</th><th class="money">Valor</th><th class="money">Pago</th><th class="money">Atualizado</th><th>Situação</th></tr></thead>
              <tbody>
                @for (l of d.lines; track l.entryId) {
                  <tr>
                    <td>@if (canEdit() && (l.status === 'OPEN' || l.status === 'PARTIAL')) {
                      <mat-checkbox [checked]="selected().includes(l.entryId)" (change)="toggle(l.entryId)" [attr.aria-label]="'Selecionar ' + l.description" /> }</td>
                    <td [class.late]="l.daysLate > 0">{{ l.dueDate | isoDate }}</td>
                    <td><a [routerLink]="['/financeiro/lancamentos', l.entryId]">{{ l.description }}</a>
                      @if (l.daysLate > 0) { <small class="tc-muted"> · {{ l.daysLate }} dias</small> }</td>
                    <td class="money">{{ l.amount | currency: 'BRL' }}</td>
                    <td class="money">{{ l.paid | currency: 'BRL' }}</td>
                    <td class="money">{{ l.remaining > 0 ? (l.updatedTotal | currency: 'BRL') : '—' }}</td>
                    <td>{{ l.status | label: 'entryStatus' }}</td>
                  </tr>
                } @empty { <tr><td colspan="7" class="tc-muted">Nenhuma cobrança.</td></tr> }
              </tbody>
            </table>
          </div>
        </section>

        <div class="cols">
          <section class="tc-card">
            <div class="head"><h3 class="tc-section-title">Histórico de cobrança</h3>
              @if (canEdit()) { <button mat-button (click)="note()"><mat-icon>add_comment</mat-icon> Registrar contato</button> }</div>
            @for (n of d.notes; track n.id) {
              <div class="note"><strong>{{ n.contactDate | isoDate }} · {{ n.contactType | label: 'contactType' }}</strong>
                <p>{{ n.note }}</p><small class="tc-muted">{{ n.createdByName }}</small></div>
            } @empty { <p class="tc-muted">Nenhum contato registrado.</p> }
          </section>
          <section class="tc-card">
            <h3 class="tc-section-title">Acordos</h3>
            @for (a of d.agreements; track a.id) {
              <div class="note"><strong>{{ a.totalAmount | currency: 'BRL' }} em {{ a.installments }}x — {{ a.status | label: 'agreementStatus' }}</strong>
                <p class="tc-muted">{{ a.paidInstallments }}/{{ a.installments }} parcelas pagas · 1ª em {{ a.firstDueDate | isoDate }}</p></div>
            } @empty { <p class="tc-muted">Nenhum acordo.</p> }
          </section>
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .kpi { display: grid; }
    .kpi strong { font: var(--mat-sys-headline-small); }
    .head { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px 0; }
    .head h3 { margin: 0; }
    .tc-card .head { padding: 0 0 8px; }
    .lines { width: 100%; border-collapse: collapse; margin-top: 8px; }
    .lines th, .lines td { padding: 8px 12px; border-bottom: 1px solid var(--mat-sys-outline-variant); text-align: left; }
    .lines .money { text-align: right; }
    .late { color: var(--mat-sys-error); font-weight: 600; }
    .cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; margin-top: 16px; }
    .note { border-bottom: 1px solid var(--mat-sys-outline-variant); padding: 8px 0; }
    .note p { margin: 4px 0; white-space: pre-wrap; }
  `,
})
export class UnitDebtPage {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  readonly unitId = input.required<string>();
  private readonly tick = signal(0);
  protected readonly selected = signal<string[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly canEdit = computed(() => this.context.can('BILLING_EDIT') && this.context.selected()?.status === 'ACTIVE');

  protected readonly debt = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.unitId), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, u]) => this.api.unitDebt(c!, u).pipe(catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); }))),
    ),
  );

  toggle(id: string): void {
    this.selected.update((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }

  note(): void {
    openForm(this.dialog, {
      title: 'Registrar contato de cobrança',
      fields: [
        { key: 'contactType', label: 'Tipo', type: 'select', required: true, value: 'PHONE', span: 6, options: options('contactType') },
        { key: 'contactDate', label: 'Data', type: 'date', required: true, value: todayIso(), span: 6 },
        { key: 'note', label: 'Descrição do contato', type: 'textarea', required: true, maxLength: 1000 },
      ],
      submit: (v) => this.api.addNote(this.context.id()!, this.unitId(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Contato registrado.'); this.tick.update((t) => t + 1); });
  }

  agreement(d: UnitDebt): void {
    const total = d.lines.filter((l) => this.selected().includes(l.entryId)).reduce((s, l) => s + l.updatedTotal, 0);
    openForm(this.dialog, {
      title: 'Acordo de parcelamento',
      intro: `${this.selected().length} débito(s), total atualizado R$ ${total.toFixed(2).replace('.', ',')}. Os débitos serão substituídos pelas parcelas do acordo.`,
      fields: [
        { key: 'installments', label: 'Nº de parcelas', type: 'number', required: true, value: 3, span: 4, min: 1 },
        { key: 'firstDueDate', label: '1º vencimento', type: 'date', required: true, value: todayIso(), span: 4 },
        { key: 'discount', label: 'Desconto (R$)', type: 'number', value: 0, span: 4, min: 0 },
        { key: 'includeCharges', label: 'Incluir multa e juros calculados até hoje', type: 'checkbox', value: true },
        { key: 'notes', label: 'Observações', type: 'textarea', maxLength: 500 },
      ],
      submitLabel: 'Criar acordo',
      submit: (v) => this.api.createAgreement(this.context.id()!, { ...v, unitId: this.unitId(), entryIds: this.selected() }),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Acordo criado.');
      this.selected.set([]);
      this.tick.update((t) => t + 1);
    });
  }
}
