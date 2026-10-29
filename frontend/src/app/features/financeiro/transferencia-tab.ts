import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { askReason } from '../../comum/ui/confirmacao-dialog';
import { todayIso } from '../../comum/ui/datas';
import { EmptyState } from '../../comum/ui/estado-vazio';
import { openForm } from '../../comum/ui/formulario-dialog';
import { IsoDatePipe } from '../../comum/ui/data-iso.pipe';
import { FinanceApi, Transferencia } from './financeiro-api';

@Component({
  selector: 'tc-transfer-tab',
  imports: [CurrencyPipe, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule, IsoDatePipe, EmptyState],
  template: `
    <div class="head">
      <p class="tc-muted">Movimentações entre as contas do próprio condomínio (ex.: suprimento de caixa, aplicação).</p>
      @if (canEdit()) {
        <button mat-flat-button (click)="create()"><mat-icon>swap_horiz</mat-icon> Nova transferência</button>
      }
    </div>
    <section class="tc-table-card" aria-live="polite">
      @if (list() === undefined) {
        <mat-progress-bar mode="indeterminate" />
      } @else if ((list() ?? []).length === 0) {
        <tc-empty-state icon="swap_horiz" title="Nenhuma transferência" />
      } @else {
        <div class="table-scroll">
          <table mat-table [dataSource]="list() ?? []">
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Data</th><td mat-cell *matCellDef="let t">{{ t.data | isoDate }}</td></ng-container>
            <ng-container matColumnDef="route"><th mat-header-cell *matHeaderCellDef>De → Para</th>
              <td mat-cell *matCellDef="let t">{{ t.nomeContaOrigem }} → {{ t.nomeContaDestino }}<br /><span class="tc-muted">{{ t.descricao }}</span></td></ng-container>
            <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="money">Valor</th><td mat-cell *matCellDef="let t" class="money">{{ t.valor | currency: 'BRL' }}</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
              <td mat-cell *matCellDef="let t">
                @if (t.estornada) { <span class="tc-status is-inactive" [title]="t.motivoEstorno ?? ''">Estornada</span> }
                @else { <span class="tc-status" [class.is-success]="t.conciliada" [class.is-active]="!t.conciliada">{{ t.conciliada ? 'Conciliada' : 'Efetivada' }}</span> }
              </td></ng-container>
            <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let t">
                @if (!t.estornada && canCancel()) { <button mat-button (click)="reverse(t)">Estornar</button> }
              </td></ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
      }
    </section>
  `,
  styles: `.head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 0 16px; flex-wrap: wrap; } .head p { margin: 0; }`,
})
export class TransferTab {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly columns = ['date', 'route', 'amount', 'status', 'actions'];
  protected readonly canEdit = computed(() => this.context.can('FINANCE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly canCancel = computed(() => this.context.can('FINANCE_CANCEL'));
  private readonly tick = signal(0);
  protected readonly list = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c]) => this.api.transfers(c!).pipe(catchError(() => of([] as Transferencia[])))),
    ),
  );

  create(): void {
    const c = this.context.id()!;
    this.api.bankAccounts(c).subscribe((accounts) => {
      const opts = accounts.filter((a) => a.ativo).map((a) => ({ value: a.id, label: a.nome }));
      openForm(this.dialog, {
        title: 'Nova transferência',
        fields: [
          { key: 'idContaOrigem', label: 'De', type: 'select', required: true, options: opts, span: 6 },
          { key: 'idContaDestino', label: 'Para', type: 'select', required: true, options: opts, span: 6 },
          { key: 'valor', label: 'Valor (R$)', type: 'number', required: true, span: 6, min: 0.01 },
          { key: 'data', label: 'Data', type: 'date', required: true, value: todayIso(), span: 6 },
          { key: 'descricao', label: 'Descrição', maxLength: 200 },
        ],
        submit: (v) => this.api.transfer(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notify.success('Transferenciaência registrada.');
        this.tick.update((t) => t + 1);
      });
    });
  }

  reverse(t: Transferencia): void {
    askReason(this.dialog, 'Estornar transferência', `Estornar a transferência de ${t.nomeContaOrigem} para ${t.nomeContaDestino}?`, 'Estornar')
      .pipe(switchMap((reason) => this.api.reverseTransfer(this.context.id()!, t.id, reason)))
      .subscribe({
        next: () => { this.notify.success('Transferenciaência estornada.'); this.tick.update((x) => x + 1); },
        error: (err: unknown) => this.notify.error(toProblem(err).detail),
      });
  }
}
