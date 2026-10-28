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
import { DebitosUnidade, FinanceApi } from '../finance/finance-api';

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
        <tc-page-header eyebrow="Demonstrativo de débitos" [title]="'Unidade ' + d.rotuloUnidade"
          [subtitle]="(d.nomeResponsavel ?? 'sem responsável financeiro') + ' · posição em ' + (d.dataBase | isoDate)" />
        <section class="tc-kpis" aria-label="Resumo do débito">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">account_balance_wallet</mat-icon></span>
            <span class="label">Principal em aberto</span><span class="value">{{ d.principalAberto | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-warning">
            <span class="icon"><mat-icon aria-hidden="true">percent</mat-icon></span>
            <span class="label">Multa + juros</span><span class="value">{{ d.encargosAbertos | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-danger">
            <span class="icon"><mat-icon aria-hidden="true">priority_high</mat-icon></span>
            <span class="label">Total atualizado</span><span class="value">{{ d.totalAberto | currency: 'BRL' }}</span>
          </div>
        </section>

        <section class="tc-table-card">
          <div class="tc-card-head">
            <h3 class="tc-section-title">Cobranças</h3>
            @if (canEdit() && selected().length) {
              <button mat-flat-button (click)="agreement(d)"><mat-icon>handshake</mat-icon> Negociar {{ selected().length }} débito(s)</button>
            }
          </div>
          <div class="table-scroll">
            <table class="lines tc-cards-mobile">
              <thead><tr><th></th><th>Vencimento</th><th>Descrição</th><th class="money">Valor</th><th class="money">Pago</th><th class="money">Atualizado</th><th>Situação</th></tr></thead>
              <tbody>
                @for (l of d.linhas; track l.idLancamento) {
                  <tr>
                    <td>@if (canEdit() && (l.situacao === 'OPEN' || l.situacao === 'PARTIAL')) {
                      <mat-checkbox [checked]="selected().includes(l.idLancamento)" (change)="toggle(l.idLancamento)" [attr.aria-label]="'Selecionar ' + l.descricao" /> }</td>
                    <td [class.late]="l.diasAtraso > 0">{{ l.dataVencimento | isoDate }}</td>
                    <td><a [routerLink]="['/financeiro/lancamentos', l.idLancamento]">{{ l.descricao }}</a>
                      @if (l.diasAtraso > 0) { <small class="tc-muted"> · {{ l.diasAtraso }} dias</small> }</td>
                    <td class="money">{{ l.valor | currency: 'BRL' }}</td>
                    <td class="money">{{ l.valorPago | currency: 'BRL' }}</td>
                    <td class="money">{{ l.valorAberto > 0 ? (l.totalAtualizado | currency: 'BRL') : '—' }}</td>
                    <td><span class="tc-status" [class.is-active]="l.situacao === 'PAID'"
                      [class.is-pending]="(l.situacao === 'OPEN' || l.situacao === 'PARTIAL') && l.diasAtraso <= 0"
                      [class.is-danger]="(l.situacao === 'OPEN' || l.situacao === 'PARTIAL') && l.diasAtraso > 0"
                      [class.is-inactive]="l.situacao === 'CANCELLED' || l.situacao === 'RENEGOTIATED'">{{ l.situacao | label: 'entryStatus' }}</span></td>
                  </tr>
                } @empty { <tr><td colspan="7" class="tc-muted">Nenhuma cobrança.</td></tr> }
              </tbody>
            </table>
          </div>
        </section>

        <div class="cols">
          <section class="tc-card">
            <div class="tc-card-head"><h3 class="tc-section-title">Histórico de cobrança</h3>
              @if (canEdit()) { <button mat-button (click)="note()"><mat-icon>add_comment</mat-icon> Registrar contato</button> }</div>
            @for (n of d.notas; track n.id) {
              <div class="note"><strong>{{ n.dataContato | isoDate }} · {{ n.tipoContato | label: 'contactType' }}</strong>
                <p>{{ n.observacao }}</p><small class="tc-muted">{{ n.nomeCriadoPor }}</small></div>
            } @empty { <p class="tc-muted">Nenhum contato registrado.</p> }
          </section>
          <section class="tc-card">
            <h3 class="tc-section-title">Acordos</h3>
            @for (a of d.acordos; track a.id) {
              <div class="note"><strong>{{ a.valorTotal | currency: 'BRL' }} em {{ a.totalParcelas }}x — {{ a.situacao | label: 'agreementStatus' }}</strong>
                <p class="tc-muted">{{ a.parcelasPagas }}/{{ a.totalParcelas }} parcelas pagas · 1ª em {{ a.dataPrimeiroVencimento | isoDate }}</p></div>
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
    .tc-section-title { margin-top: 0; }
    .lines { margin-top: 8px; }
    .late { color: var(--tc-danger); font-weight: 600; }
    .cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(300px, 100%), 1fr)); gap: 16px; margin-top: 16px; align-items: start; }
    .note { border-bottom: 1px solid var(--tc-border); padding: 10px 0; }
    .note:last-child { border-bottom: 0; }
    .note p { margin: 4px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
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
  protected readonly canEdit = computed(() => this.context.can('BILLING_EDIT') && this.context.selected()?.situacao === 'ACTIVE');

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
        { key: 'tipoContato', label: 'Tipo', type: 'select', required: true, value: 'PHONE', span: 6, options: options('contactType') },
        { key: 'dataContato', label: 'Data', type: 'date', required: true, value: todayIso(), span: 6 },
        { key: 'observacao', label: 'Descrição do contato', type: 'textarea', required: true, maxLength: 1000 },
      ],
      submit: (v) => this.api.addNote(this.context.id()!, this.unitId(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Contato registrado.'); this.tick.update((t) => t + 1); });
  }

  agreement(d: DebitosUnidade): void {
    const total = d.linhas.filter((l) => this.selected().includes(l.idLancamento)).reduce((s, l) => s + l.totalAtualizado, 0);
    openForm(this.dialog, {
      title: 'Acordo de parcelamento',
      intro: `${this.selected().length} débito(s), total atualizado R$ ${total.toFixed(2).replace('.', ',')}. Os débitos serão substituídos pelas parcelas do acordo.`,
      fields: [
        { key: 'totalParcelas', label: 'Nº de parcelas', type: 'number', required: true, value: 3, span: 4, min: 1 },
        { key: 'dataPrimeiroVencimento', label: '1º vencimento', type: 'date', required: true, value: todayIso(), span: 4 },
        { key: 'desconto', label: 'Desconto (R$)', type: 'number', value: 0, span: 4, min: 0 },
        { key: 'incluirEncargos', label: 'Incluir multa e juros calculados até hoje', type: 'checkbox', value: true },
        { key: 'observacoes', label: 'Observações', type: 'textarea', maxLength: 500 },
      ],
      submitLabel: 'Criar acordo',
      submit: (v) => this.api.createAgreement(this.context.id()!, { ...v, idUnidade: this.unitId(), idsLancamentos: this.selected() }),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Acordo criado.');
      this.selected.set([]);
      this.tick.update((t) => t + 1);
    });
  }
}
