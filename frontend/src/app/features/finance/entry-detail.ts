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
import { LancamentoDetalhe, FinanceApi, Baixa } from './finance-api';

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
        <tc-page-header [eyebrow]="(e.resumo.natureza | label: 'entryKind') + (e.resumo.rotuloParcela ? ' · parcela ' + e.resumo.rotuloParcela : '')"
          [title]="e.resumo.descricao" [subtitle]="e.resumo.codigoConta + ' ' + e.resumo.nomeConta">
          <span class="tc-status big" [class.is-active]="e.resumo.situacao === 'PAID'"
            [class.is-pending]="e.resumo.aprovacao === 'PENDING' || (!e.resumo.vencido && (e.resumo.situacao === 'OPEN' || e.resumo.situacao === 'PARTIAL'))"
            [class.is-danger]="e.resumo.aprovacao !== 'PENDING' && e.resumo.vencido"
            [class.is-inactive]="e.resumo.situacao === 'CANCELLED' || e.resumo.situacao === 'RENEGOTIATED'">
            {{ e.resumo.aprovacao === 'PENDING' ? 'Aguardando aprovação' : (e.resumo.situacao | label: 'entryStatus') }}
          </span>
        </tc-page-header>

        <section class="tc-kpis" aria-label="Valores do lançamento">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">receipt_long</mat-icon></span>
            <span class="label">Valor</span><span class="value">{{ e.resumo.valor | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-success">
            <span class="icon"><mat-icon aria-hidden="true">task_alt</mat-icon></span>
            <span class="label">{{ e.resumo.natureza === 'INCOME' ? 'Recebido' : 'Pago' }}</span><span class="value">{{ e.resumo.valorPago | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card" [class.tone-danger]="e.resumo.vencido" [class.tone-warning]="!e.resumo.vencido && e.resumo.valorAberto > 0">
            <span class="icon"><mat-icon aria-hidden="true">{{ e.resumo.vencido ? 'warning' : 'schedule' }}</mat-icon></span>
            <span class="label">Em aberto</span><span class="value">{{ e.resumo.valorAberto | currency: 'BRL' }}</span>
          </div>
        </section>

        <section class="facts tc-dl cols tc-card">
          <div><span class="k">Vencimento</span><strong [class.late]="e.resumo.vencido">{{ e.resumo.dataVencimento | isoDate }}</strong></div>
          <div><span class="k">Competência</span><strong>{{ e.resumo.competencia.slice(5, 7) }}/{{ e.resumo.competencia.slice(0, 4) }}</strong></div>
          @if (e.resumo.nomeFornecedor) { <div><span class="k">Fornecedor</span><strong>{{ e.resumo.nomeFornecedor }}</strong></div> }
          @if (e.resumo.rotuloUnidade) { <div><span class="k">Unidade</span><strong>{{ e.resumo.rotuloUnidade }}</strong></div> }
          @if (e.resumo.nomePessoa) { <div><span class="k">Pagador</span><strong>{{ e.resumo.nomePessoa }}</strong></div> }
          @if (e.resumo.numeroDocumento) { <div><span class="k">Documento</span><strong>{{ e.resumo.numeroDocumento }}</strong></div> }
          @if (e.nomeCentroCusto) { <div><span class="k">Centro de custo</span><strong>{{ e.nomeCentroCusto }}</strong></div> }
          @if (e.percentualMulta !== null && e.percentualMulta !== undefined) { <div><span class="k">Encargos</span><strong>multa {{ e.percentualMulta }}% · juros {{ e.percentualJurosMensal }}% a.m.</strong></div> }
          @if (e.observacoes) { <div class="wide"><span class="k">Observações</span><span>{{ e.observacoes }}</span></div> }
          @if (e.observacaoAprovacao) { <div class="wide"><span class="k">Parecer da aprovação</span><span>{{ e.observacaoAprovacao }}</span></div> }
          @if (e.motivoCancelamento) { <div class="wide"><span class="k">Motivo</span><span>{{ e.motivoCancelamento }}</span></div> }
        </section>

        <div class="tc-actions actions">
          @if (e.resumo.aprovacao === 'PENDING' && context.can('FINANCE_APPROVE')) {
            <button mat-stroked-button (click)="decide(e, false)">Reprovar</button>
            <button mat-flat-button (click)="decide(e, true)"><mat-icon>verified</mat-icon> Aprovar pagamento</button>
          }
          @if (open(e) && context.can('FINANCE_EDIT') && e.resumo.aprovacao !== 'PENDING' && e.resumo.aprovacao !== 'REJECTED') {
            <button mat-flat-button (click)="pay(e)"><mat-icon>payments</mat-icon> Registrar {{ e.resumo.natureza === 'INCOME' ? 'recebimento' : 'pagamento' }}</button>
          }
          @if (open(e) && e.resumo.valorPago === 0 && context.can('FINANCE_EDIT')) {
            <button mat-stroked-button (click)="edit(e)"><mat-icon>edit</mat-icon> Editar</button>
          }
          @if (open(e) && e.resumo.valorPago === 0 && context.can('FINANCE_CANCEL')) {
            <button mat-button class="danger-text" (click)="cancel(e)">Cancelar lançamento</button>
          }
        </div>

        <div class="layout">
          <section class="tc-table-card">
            <h3 class="tc-section-title tc-card-head">Baixas</h3>
            @for (p of e.baixas; track p.id) {
              <div class="pay" [class.reversed]="p.estornada">
                <div class="who">
                  <strong>{{ p.total | currency: 'BRL' }}</strong>
                  <span class="tc-muted">{{ p.dataPagamento | isoDate }} · {{ p.formaPagamento | label: 'paymentMethod' }} · {{ p.nomeContaBancaria }}
                    @if (p.multa + p.juros > 0) { · encargos {{ p.multa + p.juros | currency: 'BRL' }} }
                    @if (p.desconto > 0) { · desconto {{ p.desconto | currency: 'BRL' }} }
                  </span>
                  @if (p.estornada) { <span class="tc-muted">Estornada: {{ p.motivoEstorno }}</span> }
                </div>
                @if (p.conciliada) { <span class="tc-chip-soft">Conciliada</span> }
                @if (!p.estornada && context.can('FINANCE_CANCEL')) {
                  <button mat-button (click)="reverse(p)">Estornar</button>
                }
              </div>
            } @empty {
              <tc-empty-state icon="payments" title="Nenhuma baixa registrada" />
            }
          </section>
          <tc-attachments [condominiumId]="context.id()!" entityType="FinancialEntry" [entityId]="e.resumo.id"
            [category]="e.resumo.natureza === 'EXPENSE' ? 'INVOICE' : 'RECEIPT'" [canEdit]="context.can('FINANCE_EDIT')" />
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

  open(e: LancamentoDetalhe): boolean {
    return e.resumo.situacao === 'OPEN' || e.resumo.situacao === 'PARTIAL';
  }

  decide(e: LancamentoDetalhe, approve: boolean): void {
    openForm(this.dialog, {
      title: approve ? 'Aprovar pagamento' : 'Reprovar pagamento',
      intro: `${e.resumo.descricao} — R$ ${e.resumo.valor.toFixed(2).replace('.', ',')}`,
      fields: [{ key: 'observacao', label: approve ? 'Parecer (opcional)' : 'Motivo da reprovação', type: 'textarea', required: !approve, maxLength: 255 }],
      submitLabel: approve ? 'Aprovar' : 'Reprovar',
      submit: (v) => this.api.approve(this.c(), e.resumo.id, approve, (v['observacao'] as string) ?? null),
    }, '460px').pipe(filter((r) => !!r)).subscribe(() => this.done(approve ? 'Pagamento aprovado.' : 'Pagamento reprovado.'));
  }

  pay(e: LancamentoDetalhe): void {
    const today = todayIso();
    this.api.bankAccounts(this.c()).pipe(
      switchMap((accounts) => this.api.quote(this.c(), e.resumo.id, today).pipe(switchMap((q) => {
        const income = e.resumo.natureza === 'INCOME';
        return openForm(this.dialog, {
          title: income ? 'Registrar recebimento' : 'Registrar pagamento',
          intro: q.diasAtraso > 0
            ? `Em atraso há ${q.diasAtraso} dia(s). Encargos calculados para hoje: multa R$ ${q.multa.toFixed(2)} e juros R$ ${q.juros.toFixed(2)} (ajuste se a data for outra).`
            : 'Baixa parcial é permitida: informe o valor principal pago.',
          fields: [
            { key: 'idContaBancaria', label: 'Conta', type: 'select', required: true, span: 6, value: accounts.find((a) => a.ativo)?.id,
              options: accounts.filter((a) => a.ativo).map((a) => ({ value: a.id, label: a.nome })) },
            { key: 'dataPagamento', label: 'Data', type: 'date', required: true, value: today, span: 6 },
            { key: 'principal', label: 'Valor principal (R$)', type: 'number', required: true, value: e.resumo.valorAberto, span: 6, min: 0.01 },
            { key: 'formaPagamento', label: 'Forma', type: 'select', required: true, value: 'PIX', span: 6, options: options('paymentMethod') },
            { key: 'multa', label: 'Multa (R$)', type: 'number', value: income ? q.multa : 0, span: 4, min: 0 },
            { key: 'juros', label: 'Juros (R$)', type: 'number', value: income ? q.juros : 0, span: 4, min: 0 },
            { key: 'desconto', label: 'Desconto (R$)', type: 'number', value: 0, span: 4, min: 0 },
            { key: 'observacoes', label: 'Observação', maxLength: 255 },
          ],
          submitLabel: 'Registrar baixa',
          submit: (v) => this.api.pay(this.c(), e.resumo.id, v),
        }, '620px') as Observable<unknown>;
      }))),
      filter((r) => !!r),
    ).subscribe({ next: () => this.done('Baixa registrada.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  edit(e: LancamentoDetalhe): void {
    openEntryForm(this.dialog, this.api, this.structure, this.c(), e.resumo.natureza, e).pipe(filter((r) => !!r))
      .subscribe(() => this.done('Lançamento atualizado.'));
  }

  cancel(e: LancamentoDetalhe): void {
    askReason(this.dialog, 'Cancelar lançamento', `"${e.resumo.descricao}" será cancelado. O registro é mantido no histórico.`, 'Cancelar lançamento')
      .pipe(switchMap((reason) => this.api.cancelEntry(this.c(), e.resumo.id, reason)))
      .subscribe({ next: () => this.done('Lançamento cancelado.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  reverse(p: Baixa): void {
    askReason(this.dialog, 'Estornar baixa', `Estornar a baixa de R$ ${p.total.toFixed(2).replace('.', ',')} em ${p.dataPagamento}?`, 'Estornar')
      .pipe(switchMap((reason) => this.api.reversePayment(this.c(), p.id, reason)))
      .subscribe({ next: () => this.done('Baixa estornada.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  private done(message: string): void {
    this.notify.success(message);
    this.tick.update((t) => t + 1);
  }
}
