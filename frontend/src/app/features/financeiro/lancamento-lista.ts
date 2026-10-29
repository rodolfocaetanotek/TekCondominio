import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, filter, finalize, of, switchMap, tap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { LabelPipe } from '../../comum/rotulo.pipe';
import { EmptyState } from '../../comum/ui/estado-vazio';
import { IsoDatePipe } from '../../comum/ui/data-iso.pipe';
import { StructureService } from '../estrutura/estrutura.service';
import { openEntryForm } from './lancamento-form';
import { LancamentoResumo, FinanceApi } from './financeiro-api';

/** Contas a pagar (EXPENSE) ou a receber (INCOME) do condomínio selecionado. */
@Component({
  selector: 'tc-entry-list',
  imports: [CurrencyPipe, ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule, EmptyState, IsoDatePipe, LabelPipe],
  template: `
    <div class="filters">
      <mat-form-field class="search">
        <mat-label>Buscar</mat-label>
        <mat-icon matPrefix>search</mat-icon>
        <input matInput [formControl]="search" placeholder="Descrição ou nº do documento" autocomplete="off" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Situação</mat-label>
        <mat-select [formControl]="status">
          <mat-option value="open">Em aberto</mat-option>
          <mat-option value="overdue">Vencidos</mat-option>
          <mat-option value="pending">Aguardando aprovação</mat-option>
          <mat-option value="paid">Pagos</mat-option>
          <mat-option value="all">Todos</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field><mat-label>Vencimento de</mat-label><input matInput type="date" [formControl]="from" /></mat-form-field>
      <mat-form-field><mat-label>até</mat-label><input matInput type="date" [formControl]="to" /></mat-form-field>
      @if (canEdit()) {
        <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon>{{ kind() === 'EXPENSE' ? 'Nova despesa' : 'Nova receita' }}</button>
      }
    </div>
    <section class="tc-table-card" aria-live="polite" [attr.aria-busy]="loading()">
      <mat-progress-bar [mode]="loading() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!loading()" />
      @if (error(); as message) {
        <tc-empty-state icon="cloud_off" title="Não foi possível carregar" [description]="message" />
      } @else if (page(); as p) {
        @if (p.totalElementos === 0) {
          <tc-empty-state icon="receipt_long" title="Nenhum lançamento encontrado" description="Ajuste os filtros ou cadastre um novo lançamento." />
        } @else {
          <div class="table-scroll">
            <table mat-table [dataSource]="p.conteudo">
              <caption class="sr-only">Lançamentos</caption>
              <ng-container matColumnDef="due">
                <th mat-header-cell *matHeaderCellDef>Vencimento</th>
                <td mat-cell *matCellDef="let e" [class.late]="e.vencido">{{ e.dataVencimento | isoDate }}</td>
              </ng-container>
              <ng-container matColumnDef="description">
                <th mat-header-cell *matHeaderCellDef>Descrição</th>
                <td mat-cell *matCellDef="let e">
                  <strong class="block">{{ e.descricao }}</strong>
                  <span class="tc-muted small">{{ e.codigoConta }} {{ e.nomeConta }}{{ e.nomeFornecedor ? ' · ' + e.nomeFornecedor : '' }}{{ e.rotuloUnidade ? ' · Unid. ' + e.rotuloUnidade : '' }}</span>
                </td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef class="money">Valor</th>
                <td mat-cell *matCellDef="let e" class="money">{{ e.valor | currency: 'BRL' }}</td>
              </ng-container>
              <ng-container matColumnDef="remaining">
                <th mat-header-cell *matHeaderCellDef class="money">Em aberto</th>
                <td mat-cell *matCellDef="let e" class="money">{{ e.valorAberto | currency: 'BRL' }}</td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Situação</th>
                <td mat-cell *matCellDef="let e">
                  <span class="tc-status" [class.is-active]="e.situacao === 'PAID'"
                    [class.is-pending]="e.aprovacao === 'PENDING' || (!e.vencido && (e.situacao === 'OPEN' || e.situacao === 'PARTIAL'))"
                    [class.is-danger]="e.aprovacao !== 'PENDING' && e.vencido"
                    [class.is-inactive]="e.situacao === 'CANCELLED' || e.situacao === 'RENEGOTIATED'">
                    {{ e.aprovacao === 'PENDING' ? 'Aguardando aprovação' : e.vencido ? 'Vencido' : (e.situacao | label: 'entryStatus') }}
                  </span>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" role="link"
                [attr.aria-label]="'Abrir ' + row.descricao" (click)="open(row)" (keydown.enter)="open(row)"></tr>
            </table>
          </div>
          <mat-paginator [length]="p.totalElementos" [pageIndex]="p.pagina" [pageSize]="p.tamanho" [pageSizeOptions]="[25, 50, 100]"
            (page)="onPage($event)" aria-label="Paginação" />
        }
      }
    </section>
  `,
  styles: `
    .block { display: block; }
    .small { font: var(--mat-sys-body-small); }
    .late { color: var(--tc-danger); font-weight: 600; }
  `,
})
export class EntryList {
  readonly kind = input.required<'INCOME' | 'EXPENSE'>();
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly columns = ['due', 'description', 'amount', 'remaining', 'status'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl('open', { nonNullable: true });
  protected readonly from = new FormControl('', { nonNullable: true });
  protected readonly to = new FormControl('', { nonNullable: true });
  private readonly params = signal({ page: 0, size: 50, search: '', status: 'open', from: '', to: '', tick: 0 });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canEdit = computed(() => this.context.can('FINANCE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  private readonly query = computed(() => ({ c: this.context.id(), kind: this.kind(), ...this.params() }));

  protected readonly page = toSignal(
    toObservable(this.query).pipe(
      filter((q) => !!q.c),
      tap(() => { this.loading.set(true); this.error.set(null); }),
      switchMap((q) => {
        const statusFilter: Record<string, Record<string, string | string[]>> = {
          open: { situacao: ['OPEN', 'PARTIAL'] }, overdue: { vencido: 'true' }, pending: { aprovacao: 'PENDING', situacao: ['OPEN'] },
          paid: { situacao: ['PAID'] }, all: {},
        };
        return this.api.entries(q.c!, { natureza: q.kind, busca: q.search, vencimentoDe: q.from, vencimentoAte: q.to, pagina: q.page, tamanho: q.size, ...statusFilter[q.status] })
          .pipe(catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); }), finalize(() => this.loading.set(false)));
      }),
    ),
    { initialValue: null },
  );

  constructor() {
    this.search.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((search) => this.params.update((p) => ({ ...p, search, page: 0 })));
    this.status.valueChanges.pipe(takeUntilDestroyed()).subscribe((status) => this.params.update((p) => ({ ...p, status, page: 0 })));
    this.from.valueChanges.pipe(takeUntilDestroyed()).subscribe((from) => this.params.update((p) => ({ ...p, from, page: 0 })));
    this.to.valueChanges.pipe(takeUntilDestroyed()).subscribe((to) => this.params.update((p) => ({ ...p, to, page: 0 })));
  }

  onPage(e: PageEvent): void {
    this.params.update((p) => ({ ...p, page: e.pageIndex, size: e.pageSize }));
  }

  open(e: LancamentoResumo): void {
    void this.router.navigate(['/financeiro/lancamentos', e.id]);
  }

  create(): void {
    openEntryForm(this.dialog, this.api, this.structure, this.context.id()!, this.kind()).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Lançamento registrado.');
      this.params.update((p) => ({ ...p, tick: p.tick + 1 }));
    });
  }
}
