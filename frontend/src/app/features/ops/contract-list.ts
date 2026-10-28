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
import { Contract, OpsApi } from './ops-api';

@Component({
  selector: 'tc-contract-list',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, MatExpansionModule, PageHeader, EmptyState, CondominiumRequired,
    IsoDatePipe, LabelPipe, Attachments],
  template: `
    <div class="tc-page narrow">
      <tc-page-header eyebrow="Fornecedores" title="Contratos" [subtitle]="context.selected()?.name">
        @if (canEdit()) { <button mat-flat-button (click)="edit()"><mat-icon>contract</mat-icon> Novo contrato</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <mat-accordion>
          @for (c of list() ?? []; track c.id) {
            <mat-expansion-panel (opened)="openHistory(c)">
              <mat-expansion-panel-header>
                <mat-panel-title>{{ c.title }}</mat-panel-title>
                <mat-panel-description>
                  {{ c.supplierName }} · {{ c.amount | currency: 'BRL' }} {{ c.periodicity | label: 'periodicity' }}
                  <span class="tc-status" [class.is-active]="c.status === 'ACTIVE' && !c.expiring && !c.expired" [class.is-pending]="c.expiring || c.expired"
                    [class.is-inactive]="c.status === 'TERMINATED'">{{ c.status === 'TERMINATED' ? 'Encerrado' : c.expired ? 'Vencido' : c.expiring ? 'A vencer' : 'Vigente' }}</span>
                </mat-panel-description>
              </mat-expansion-panel-header>
              <p>Vigência: {{ c.startDate | isoDate }} a {{ c.endDate ? (c.endDate | isoDate) : 'indeterminado' }} · aviso prévio {{ c.noticeDays }} dias
                {{ c.readjustmentIndex ? ' · reajuste ' + c.readjustmentIndex + (c.readjustmentMonth ? ' em ' + c.readjustmentMonth + '/ano' : '') : '' }}</p>
              @if (c.description) { <p class="pre">{{ c.description }}</p> }
              @if (c.terminationReason) { <p class="tc-muted">Encerrado em {{ c.terminatedOn | isoDate }}: {{ c.terminationReason }}</p> }
              <h4>Histórico</h4>
              @for (h of history()[c.id] ?? []; track h.createdAt) {
                <p class="small"><strong>{{ h.createdAt | date: 'dd/MM/yy HH:mm' }}</strong> {{ h.createdByName }}: {{ h.change }}</p>
              }
              <tc-attachments [condominiumId]="context.id()!" entityType="Contract" [entityId]="c.id" category="CONTRACT" [canEdit]="canEdit()" />
              @if (canEdit() && c.status === 'ACTIVE') {
                <div class="tc-actions">
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
  styles: `.narrow { max-width: 1000px; } mat-panel-description { gap: 8px; flex-wrap: wrap; } .pre { white-space: pre-wrap; } .small { font: var(--mat-sys-body-small); margin: 2px 0; } h4 { margin: 12px 0 4px; }`,
})
export class ContractList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly finance = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly history = signal<Record<string, Contract['history']>>({});
  protected readonly canEdit = computed(() => this.context.can('SUPPLIER_EDIT') && this.context.selected()?.status === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.contracts(c!).pipe(catchError(() => of([] as Contract[]))))));

  openHistory(c: Contract): void {
    this.api.contract(this.context.id()!, c.id).subscribe((full) => this.history.update((h) => ({ ...h, [c.id]: full.history })));
  }

  edit(c?: Contract): void {
    this.finance.suppliers('', 0, 100).pipe(map((p) => p.content)).subscribe((suppliers) => openForm(this.dialog, {
      title: c ? 'Editar contrato' : 'Novo contrato',
      fields: [
        { key: 'supplierId', label: 'Fornecedor', type: 'select', required: true, value: c?.supplierId, span: 6,
          options: suppliers.filter((s) => s.active || s.id === c?.supplierId).map((s) => ({ value: s.id, label: s.name })),
          hint: 'Cadastre o fornecedor em Fornecedores, se necessário.' },
        { key: 'title', label: 'Objeto', required: true, value: c?.title, span: 6, maxLength: 200 },
        { key: 'amount', label: 'Valor (R$)', type: 'number', required: true, value: c?.amount, span: 4, min: 0 },
        { key: 'periodicity', label: 'Periodicidade', type: 'select', required: true, value: c?.periodicity ?? 'MONTHLY', span: 4, options: options('periodicity') },
        { key: 'noticeDays', label: 'Aviso prévio (dias)', type: 'number', value: c?.noticeDays ?? 30, span: 4, min: 0 },
        { key: 'startDate', label: 'Início', type: 'date', required: true, value: c?.startDate ?? todayIso(), span: 6 },
        { key: 'endDate', label: 'Término', type: 'date', value: c?.endDate, span: 6 },
        { key: 'readjustmentIndex', label: 'Índice de reajuste', value: c?.readjustmentIndex, span: 6, hint: 'Ex.: IPCA, IGP-M', maxLength: 20 },
        { key: 'readjustmentMonth', label: 'Mês do reajuste (1–12)', type: 'number', value: c?.readjustmentMonth, span: 6, min: 1 },
        { key: 'description', label: 'Descrição / cláusulas', type: 'textarea', value: c?.description, maxLength: 2000 },
      ],
      submit: (v) => this.api.saveContract(this.context.id()!, { ...v, version: c?.version }, c?.id),
    }, '680px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Contrato salvo.'); this.tick.update((t) => t + 1); }));
  }

  terminate(c: Contract): void {
    openForm(this.dialog, {
      title: 'Encerrar contrato',
      fields: [
        { key: 'terminatedOn', label: 'Data de encerramento', type: 'date', required: true, value: todayIso() },
        { key: 'reason', label: 'Motivo', required: true, maxLength: 255 },
      ],
      submitLabel: 'Encerrar',
      submit: (v) => this.api.terminateContract(this.context.id()!, c.id, v),
    }, '440px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Contrato encerrado.'); this.tick.update((t) => t + 1); });
  }
}
