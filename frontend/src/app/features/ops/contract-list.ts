import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { FinanceApi } from '../finance/finance-api';
import { Attachments } from './attachments';
import { Contrato, OpsApi } from './ops-api';

@Component({
  selector: 'tc-contract-list',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, MatExpansionModule, PageHeader, EmptyState, CondominiumRequired,
    IsoDatePipe, LabelPipe, Attachments],
  template: `
    <div class="tc-page narrow">
      <tc-page-header eyebrow="Fornecedores" title="Contratos" [subtitle]="context.selected()?.nome">
        @if (canEdit()) { <button mat-flat-button (click)="edit()"><mat-icon>contract</mat-icon> Novo contrato</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <mat-accordion class="tc-accordion-cards">
          @for (c of list() ?? []; track c.id) {
            <mat-expansion-panel (opened)="openHistory(c)">
              <mat-expansion-panel-header>
                <mat-panel-title>{{ c.titulo }}</mat-panel-title>
                <mat-panel-description>
                  <span class="meta">{{ c.nomeFornecedor }} · <span class="money">{{ c.valor | currency: 'BRL' }}</span> {{ c.periodicidade | label: 'periodicity' }}</span>
                  <span class="tc-status" [class.is-active]="c.situacao === 'ACTIVE' && !c.aVencer && !c.vencido"
                    [class.is-pending]="c.situacao !== 'TERMINATED' && !c.vencido && c.aVencer" [class.is-danger]="c.situacao !== 'TERMINATED' && c.vencido"
                    [class.is-inactive]="c.situacao === 'TERMINATED'">{{ c.situacao === 'TERMINATED' ? 'Encerrado' : c.vencido ? 'Vencido' : c.aVencer ? 'A vencer' : 'Vigente' }}</span>
                </mat-panel-description>
              </mat-expansion-panel-header>
              <p class="terms">Vigência: {{ c.dataInicio | isoDate }} a {{ c.dataFim ? (c.dataFim | isoDate) : 'indeterminado' }} · aviso prévio {{ c.diasAvisoPrevio }} dias
                {{ c.indiceReajuste ? ' · reajuste ' + c.indiceReajuste + (c.mesReajuste ? ' em ' + c.mesReajuste + '/ano' : '') : '' }}</p>
              @if (c.descricao) { <p class="pre">{{ c.descricao }}</p> }
              @if (c.motivoEncerramento) { <p class="tc-muted">Encerrado em {{ c.dataEncerramento | isoDate }}: {{ c.motivoEncerramento }}</p> }
              <h4 class="tc-section-label">Histórico</h4>
              @for (h of history()[c.id] ?? []; track h.criadoEm) {
                <p class="small hist"><strong>{{ h.criadoEm | date: 'dd/MM/yy HH:mm' }}</strong> {{ h.nomeCriadoPor }}: {{ h.alteracao }}</p>
              }
              <tc-attachments [condominiumId]="context.id()!" entityType="Contract" [entityId]="c.id" category="CONTRACT" [canEdit]="canEdit()" />
              @if (canEdit() && c.situacao === 'ACTIVE') {
                <div class="tc-actions foot">
                  <button mat-button (click)="terminate(c)">Encerrar contrato</button>
                  <button mat-stroked-button (click)="edit(c)">Editar</button>
                </div>
              }
            </mat-expansion-panel>
          } @empty {
            <section class="tc-card"><tc-empty-state icon="contract" title="Nenhum contrato cadastrado" /></section>
          }
        </mat-accordion>
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1000px; }
    .meta { flex: 1 1 auto; min-width: 0; }
    .terms { margin-top: 4px; }
    .pre { white-space: pre-wrap; overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); margin: 2px 0; }
    .hist { padding: 4px 0 4px 10px; border-left: 2px solid var(--tc-border); }
    h4.tc-section-label { margin: 16px 0 6px; }
    tc-attachments { display: block; margin-top: 16px; }
    .foot { margin-top: 12px; }
  `,
})
export class ContractList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly finance = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly history = signal<Record<string, Contrato['historico']>>({});
  protected readonly canEdit = computed(() => this.context.can('SUPPLIER_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.contracts(c!).pipe(catchError(() => of([] as Contrato[]))))));

  openHistory(c: Contrato): void {
    this.api.contract(this.context.id()!, c.id).subscribe((full) => this.history.update((h) => ({ ...h, [c.id]: full.historico })));
  }

  edit(c?: Contrato): void {
    this.finance.suppliers('', 0, 100).pipe(map((p) => p.conteudo)).subscribe((suppliers) => openForm(this.dialog, {
      title: c ? 'Editar contrato' : 'Novo contrato',
      fields: [
        { key: 'idFornecedor', label: 'Fornecedor', type: 'select', required: true, value: c?.idFornecedor, span: 6,
          options: suppliers.filter((s) => s.ativo || s.id === c?.idFornecedor).map((s) => ({ value: s.id, label: s.nome })),
          hint: 'Cadastre o fornecedor em Fornecedores, se necessário.' },
        { key: 'titulo', label: 'Objeto', required: true, value: c?.titulo, span: 6, maxLength: 200 },
        { key: 'valor', label: 'Valor (R$)', type: 'number', required: true, value: c?.valor, span: 4, min: 0 },
        { key: 'periodicidade', label: 'Periodicidade', type: 'select', required: true, value: c?.periodicidade ?? 'MONTHLY', span: 4, options: options('periodicity') },
        { key: 'diasAvisoPrevio', label: 'Aviso prévio (dias)', type: 'number', value: c?.diasAvisoPrevio ?? 30, span: 4, min: 0 },
        { key: 'dataInicio', label: 'Início', type: 'date', required: true, value: c?.dataInicio ?? todayIso(), span: 6 },
        { key: 'dataFim', label: 'Término', type: 'date', value: c?.dataFim, span: 6 },
        { key: 'indiceReajuste', label: 'Índice de reajuste', value: c?.indiceReajuste, span: 6, hint: 'Ex.: IPCA, IGP-M', maxLength: 20 },
        { key: 'mesReajuste', label: 'Mês do reajuste (1–12)', type: 'number', value: c?.mesReajuste, span: 6, min: 1 },
        { key: 'descricao', label: 'Descrição / cláusulas', type: 'textarea', value: c?.descricao, maxLength: 2000 },
      ],
      submit: (v) => this.api.saveContract(this.context.id()!, { ...v, versao: c?.versao }, c?.id),
    }, '680px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Contrato salvo.'); this.tick.update((t) => t + 1); }));
  }

  terminate(c: Contrato): void {
    openForm(this.dialog, {
      title: 'Encerrar contrato',
      fields: [
        { key: 'dataEncerramento', label: 'Data de encerramento', type: 'date', required: true, value: todayIso() },
        { key: 'motivo', label: 'Motivo', required: true, maxLength: 255 },
      ],
      submitLabel: 'Encerrar',
      submit: (v) => this.api.terminateContract(this.context.id()!, c.id, v),
    }, '440px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Contrato encerrado.'); this.tick.update((t) => t + 1); });
  }
}
