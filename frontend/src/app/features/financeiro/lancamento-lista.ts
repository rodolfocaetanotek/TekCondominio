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
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { EstruturaService } from '../estrutura/estrutura.service';
import { abrirFormLancamento } from './lancamento-form';
import { LancamentoResumo, FinanceiroApi } from './financeiro-api';

/** Contas a pagar (EXPENSE) ou a receber (INCOME) do condomínio selecionado. */
@Component({
  selector: 'tc-lancamento-lista',
  imports: [CurrencyPipe, ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule, EstadoVazio, DataIsoPipe, RotuloPipe],
  template: `
    <div class="filters">
      <mat-form-field class="search">
        <mat-label>Buscar</mat-label>
        <mat-icon matPrefix>search</mat-icon>
        <input matInput [formControl]="busca" placeholder="Descrição ou nº do documento" autocomplete="off" />
      </mat-form-field>
      <mat-form-field>
        <mat-label>Situação</mat-label>
        <mat-select [formControl]="situacao">
          <mat-option value="open">Em aberto</mat-option>
          <mat-option value="overdue">Vencidos</mat-option>
          <mat-option value="pending">Aguardando aprovação</mat-option>
          <mat-option value="paid">Pagos</mat-option>
          <mat-option value="all">Todos</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field><mat-label>Vencimento de</mat-label><input matInput type="date" [formControl]="de" /></mat-form-field>
      <mat-form-field><mat-label>até</mat-label><input matInput type="date" [formControl]="ate" /></mat-form-field>
      @if (podeEditar()) {
        <button mat-flat-button (click)="cadastrar()"><mat-icon>add</mat-icon>{{ natureza() === 'EXPENSE' ? 'Nova despesa' : 'Nova receita' }}</button>
      }
    </div>
    <section class="tc-table-card" aria-live="polite" [attr.aria-busy]="carregando()">
      <mat-progress-bar [mode]="carregando() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!carregando()" />
      @if (erro(); as mensagem) {
        <tc-estado-vazio icone="cloud_off" titulo="Não foi possível carregar" [descricao]="mensagem" />
      } @else if (pagina(); as p) {
        @if (p.totalElementos === 0) {
          <tc-estado-vazio icone="receipt_long" titulo="Nenhum lançamento encontrado" descricao="Ajuste os filtros ou cadastre um novo lançamento." />
        } @else {
          <div class="table-scroll">
            <table mat-table [dataSource]="p.conteudo">
              <caption class="sr-only">Lançamentos</caption>
              <ng-container matColumnDef="due">
                <th mat-header-cell *matHeaderCellDef>Vencimento</th>
                <td mat-cell *matCellDef="let e" [class.late]="e.vencido">{{ e.dataVencimento | dataIso }}</td>
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
                    {{ e.aprovacao === 'PENDING' ? 'Aguardando aprovação' : e.vencido ? 'Vencido' : (e.situacao | rotulo: 'entryStatus') }}
                  </span>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="colunas"></tr>
              <tr mat-row *matRowDef="let linha; colunas: colunas" class="is-clickable" tabindex="0" role="link"
                [attr.aria-label]="'Abrir ' + linha.descricao" (click)="abrir(linha)" (keydown.enter)="abrir(linha)"></tr>
            </table>
          </div>
          <mat-paginator [length]="p.totalElementos" [pageIndex]="p.pagina" [pageSize]="p.tamanho" [pageSizeOptions]="[25, 50, 100]"
            (page)="aoPaginar($event)" aria-label="Paginação" />
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
export class LancamentoLista {
  readonly natureza = input.required<'INCOME' | 'EXPENSE'>();
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);

  protected readonly colunas = ['due', 'description', 'amount', 'remaining', 'status'];
  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly situacao = new FormControl('open', { nonNullable: true });
  protected readonly de = new FormControl('', { nonNullable: true });
  protected readonly ate = new FormControl('', { nonNullable: true });
  private readonly parametros = signal({ pagina: 0, tamanho: 50, busca: '', situacao: 'open', de: '', ate: '', recarga: 0 });
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeEditar = computed(() => this.contexto.pode('FINANCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  private readonly consulta = computed(() => ({ c: this.contexto.id(), tipo: this.natureza(), ...this.parametros() }));

  protected readonly pagina = toSignal(
    toObservable(this.consulta).pipe(
      filter((q) => !!q.c),
      tap(() => { this.carregando.set(true); this.erro.set(null); }),
      switchMap((q) => {
        const filtroSituacao: Record<string, Record<string, string | string[]>> = {
          open: { situacao: ['OPEN', 'PARTIAL'] }, overdue: { vencido: 'true' }, pending: { aprovacao: 'PENDING', situacao: ['OPEN'] },
          paid: { situacao: ['PAID'] }, all: {},
        };
        return this.api.lancamentos(q.c!, { natureza: q.tipo, busca: q.busca, vencimentoDe: q.de, vencimentoAte: q.ate, pagina: q.pagina, tamanho: q.tamanho, ...filtroSituacao[q.situacao] })
          .pipe(catchError((err: unknown) => { this.erro.set(paraProblema(err).detail); return of(null); }), finalize(() => this.carregando.set(false)));
      }),
    ),
    { initialValue: null },
  );

  constructor() {
    this.busca.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((busca) => this.parametros.update((p) => ({ ...p, busca, pagina: 0 })));
    this.situacao.valueChanges.pipe(takeUntilDestroyed()).subscribe((situacao) => this.parametros.update((p) => ({ ...p, situacao, pagina: 0 })));
    this.de.valueChanges.pipe(takeUntilDestroyed()).subscribe((de) => this.parametros.update((p) => ({ ...p, de, pagina: 0 })));
    this.ate.valueChanges.pipe(takeUntilDestroyed()).subscribe((ate) => this.parametros.update((p) => ({ ...p, ate, pagina: 0 })));
  }

  aoPaginar(e: PageEvent): void {
    this.parametros.update((p) => ({ ...p, pagina: e.pageIndex, tamanho: e.pageSize }));
  }

  abrir(e: LancamentoResumo): void {
    void this.router.navigate(['/financeiro/lancamentos', e.id]);
  }

  cadastrar(): void {
    abrirFormLancamento(this.dialog, this.api, this.estrutura, this.contexto.id()!, this.natureza()).pipe(filter((r) => !!r)).subscribe(() => {
      this.notificacao.sucesso('Lançamento registrado.');
      this.parametros.update((p) => ({ ...p, recarga: p.recarga + 1 }));
    });
  }
}
