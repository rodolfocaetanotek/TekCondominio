import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { askReason } from '../../shared/ui/confirm-dialog';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Agreement, Allocation, Delinquency, FinanceApi } from '../finance/finance-api';
import { AllocationDialog } from './allocation-dialog';
import { BankReturnTab } from './bank-return-tab';
import { DebtImportDialog } from './debt-import-dialog';

@Component({
  selector: 'tc-billing-page',
  imports: [CurrencyPipe, DecimalPipe, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, PageHeader, CondominiumRequired,
    EmptyState, IsoDatePipe, LabelPipe, BankReturnTab],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Cobrança" title="Cobrança condominial" [subtitle]="context.selected()?.name">
        @if (canEdit()) {
          <button mat-stroked-button (click)="importDebts()"><mat-icon>upload_file</mat-icon> Importar débitos</button>
          <button mat-flat-button (click)="generate()"><mat-icon>request_quote</mat-icon> Gerar cobranças</button>
        }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
          <mat-tab label="Rateios / competências">
            <section class="tc-table-card">
              @for (a of allocations() ?? []; track a.id) {
                <div class="row" [class.off]="a.status === 'CANCELLED'">
                  <div class="who">
                    <strong>{{ a.description }}</strong>
                    <span class="tc-muted">{{ a.competence.slice(5, 7) }}/{{ a.competence.slice(0, 4) }} · {{ a.method | label: 'allocationMethod' }} ·
                      {{ a.unitsCount }} unidades · vence {{ a.dueDate | isoDate }}{{ a.cancelReason ? ' · cancelado: ' + a.cancelReason : '' }}</span>
                  </div>
                  <div class="nums">
                    <span class="money">{{ a.generatedAmount | currency: 'BRL' }}</span>
                    <small class="tc-muted">recebido {{ a.receivedAmount | currency: 'BRL' }} · {{ a.paidCount }}/{{ a.unitsCount }} pagas</small>
                  </div>
                  @if (a.status === 'GENERATED' && canEdit() && context.can('FINANCE_CANCEL') && a.receivedAmount === 0) {
                    <button mat-button (click)="cancelAllocation(a)">Cancelar</button>
                  }
                </div>
              } @empty {
                <tc-empty-state icon="request_quote" title="Nenhuma cobrança gerada"
                  description="Gere as cobranças do mês: o valor é rateado entre as unidades e cada uma recebe seu título a receber." />
              }
            </section>
          </mat-tab>
          <mat-tab label="Inadimplência">
            <ng-template matTabContent>
              @if (delinquency(); as d) {
                <section class="tc-kpis" aria-label="Indicadores de inadimplência">
                  <div class="tc-kpi tc-card tone-danger">
                    <span class="icon"><mat-icon aria-hidden="true">money_off</mat-icon></span>
                    <span class="label">Principal vencido</span><span class="value">{{ d.totalPrincipal | currency: 'BRL' }}</span>
                  </div>
                  <div class="tc-kpi tc-card tone-warning">
                    <span class="icon"><mat-icon aria-hidden="true">percent</mat-icon></span>
                    <span class="label">Multa + juros até hoje</span><span class="value">{{ d.totalCharges | currency: 'BRL' }}</span>
                  </div>
                  <div class="tc-kpi tc-card tone-info">
                    <span class="icon"><mat-icon aria-hidden="true">monitoring</mat-icon></span>
                    <span class="label">Índice de inadimplência</span><span class="value">{{ d.delinquencyRate | number: '1.2-2' }}%</span>
                    <span class="sub">vencido ÷ emitido em 12 meses</span>
                  </div>
                  <div class="tc-kpi tc-card">
                    <span class="icon"><mat-icon aria-hidden="true">door_front</mat-icon></span>
                    <span class="label">Unidades em débito</span><span class="value">{{ d.unitsInDebt }} de {{ d.activeUnits }}</span>
                  </div>
                </section>
                <section class="tc-table-card">
                  @for (r of d.rows; track r.unitId) {
                    <a class="row link" [routerLink]="['/cobranca/unidades', r.unitId]">
                      <div class="who">
                        <strong>{{ r.unitLabel }}</strong>
                        <span class="tc-muted">{{ r.responsibleName ?? 'sem responsável' }} · {{ r.overdueCount }} título(s) · {{ r.maxDaysLate }} dias de atraso
                          {{ r.lastContactDate ? ' · último contato ' + (r.lastContactDate | isoDate) : '' }}</span>
                      </div>
                      <div class="nums"><span class="money negative">{{ r.total | currency: 'BRL' }}</span>
                        <small class="tc-muted">principal {{ r.principal | currency: 'BRL' }}</small></div>
                      <mat-icon class="chev" aria-hidden="true">chevron_right</mat-icon>
                    </a>
                  } @empty {
                    <tc-empty-state icon="sentiment_satisfied" title="Nenhuma unidade inadimplente" />
                  }
                </section>
              }
            </ng-template>
          </mat-tab>
          <mat-tab label="Acordos">
            <ng-template matTabContent>
              <section class="tc-table-card">
                @for (a of agreements() ?? []; track a.id) {
                  <div class="row" [class.off]="a.status === 'CANCELLED'">
                    <div class="who">
                      <a [routerLink]="['/cobranca/unidades', a.unitId]"><strong>{{ a.unitLabel }}</strong></a>
                      <span class="tc-muted">{{ a.installments }}x a partir de {{ a.firstDueDate | isoDate }} · {{ a.paidInstallments }}/{{ a.installments }} pagas
                        · original {{ a.originalAmount | currency: 'BRL' }} + encargos {{ a.chargesAmount | currency: 'BRL' }} − desconto {{ a.discount | currency: 'BRL' }}</span>
                    </div>
                    <div class="nums"><span class="money">{{ a.totalAmount | currency: 'BRL' }}</span>
                      <small class="tc-status" [class.is-active]="a.status === 'ACTIVE'" [class.is-success]="a.status === 'COMPLETED'" [class.is-inactive]="a.status === 'CANCELLED'">{{ a.status | label: 'agreementStatus' }}</small></div>
                    @if (a.status === 'ACTIVE' && a.paidInstallments === 0 && canEdit()) {
                      <button mat-button (click)="cancelAgreement(a)">Cancelar</button>
                    }
                  </div>
                } @empty {
                  <tc-empty-state icon="handshake" title="Nenhum acordo" description="Acordos são criados no demonstrativo de débitos da unidade (aba Inadimplência)." />
                }
              </section>
            </ng-template>
          </mat-tab>
          <mat-tab label="Retorno bancário"><ng-template matTabContent><tc-bank-return-tab /></ng-template></mat-tab>
        </mat-tab-group>
      }
    </div>
  `,
  styles: `
    .row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; padding: 14px 20px; border-top: 1px solid var(--tc-border); color: inherit; text-decoration: none; }
    .row:first-child { border-top: 0; }
    .row.link { transition: background-color 0.12s; }
    .row.link:hover, .row.link:focus-visible { background: color-mix(in srgb, var(--mat-sys-primary) 5%, var(--tc-surface)); }
    .row.link:hover strong { color: var(--mat-sys-primary); }
    .row.off { opacity: .55; }
    .who { flex: 1 1 260px; display: grid; gap: 2px; min-width: 0; }
    .who .tc-muted { font: var(--mat-sys-body-small); }
    .nums { display: grid; justify-items: end; gap: 2px; text-align: right; }
    .nums .money { font-weight: 650; font-size: 15px; }
    .chev { color: var(--tc-text-muted); }
    @media (max-width: 600px) {
      .row { padding: 12px 16px; }
      .nums { flex: 1 1 auto; }
    }
  `,
})
export class BillingPage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('BILLING_EDIT') && this.context.selected()?.status === 'ACTIVE');
  private readonly source = combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(filter(([c]) => !!c));

  protected readonly allocations = toSignal(this.source.pipe(switchMap(([c]) => this.api.allocations(c!).pipe(catchError(() => of([] as Allocation[]))))));
  protected readonly delinquency = toSignal(this.source.pipe(switchMap(([c]) => this.api.delinquency(c!).pipe(catchError(() => of(null as Delinquency | null))))));
  protected readonly agreements = toSignal(this.source.pipe(switchMap(([c]) => this.api.agreements(c!).pipe(catchError(() => of([] as Agreement[]))))));

  generate(): void {
    this.dialog.open(AllocationDialog, { data: { condominiumId: this.context.id()! }, width: '760px', maxWidth: '95vw' })
      .afterClosed().pipe(filter((r) => !!r)).subscribe(() => {
        this.notify.success('Cobranças geradas.');
        this.tick.update((t) => t + 1);
      });
  }

  importDebts(): void {
    this.dialog.open(DebtImportDialog, { data: { condominiumId: this.context.id()! }, width: '640px', maxWidth: '95vw' })
      .afterClosed().pipe(filter((r) => !!r)).subscribe(() => this.tick.update((t) => t + 1));
  }

  cancelAllocation(a: Allocation): void {
    askReason(this.dialog, 'Cancelar cobranças', `Cancelar "${a.description}" e as ${a.unitsCount} cobranças geradas?`, 'Cancelar cobranças')
      .pipe(switchMap((reason) => this.api.cancelAllocation(this.context.id()!, a.id, reason)))
      .subscribe({ next: () => { this.notify.success('Cobranças canceladas.'); this.tick.update((t) => t + 1); },
        error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  cancelAgreement(a: Agreement): void {
    askReason(this.dialog, 'Cancelar acordo', 'As parcelas serão canceladas e os débitos originais voltarão a ficar em aberto.', 'Cancelar acordo')
      .pipe(switchMap((reason) => this.api.cancelAgreement(this.context.id()!, a.id, reason)))
      .subscribe({ next: () => { this.notify.success('Acordo cancelado.'); this.tick.update((t) => t + 1); },
        error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }
}
