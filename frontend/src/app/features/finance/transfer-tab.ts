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
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { askReason } from '../../shared/ui/confirm-dialog';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { FinanceApi, Transfer } from './finance-api';

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
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Data</th><td mat-cell *matCellDef="let t">{{ t.date | isoDate }}</td></ng-container>
            <ng-container matColumnDef="route"><th mat-header-cell *matHeaderCellDef>De → Para</th>
              <td mat-cell *matCellDef="let t">{{ t.fromAccountName }} → {{ t.toAccountName }}<br /><span class="tc-muted">{{ t.description }}</span></td></ng-container>
            <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="money">Valor</th><td mat-cell *matCellDef="let t" class="money">{{ t.amount | currency: 'BRL' }}</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
              <td mat-cell *matCellDef="let t">
                @if (t.reversed) { <span class="tc-status is-inactive" [title]="t.reversalReason ?? ''">Estornada</span> }
                @else { <span class="tc-status" [class.is-success]="t.reconciled" [class.is-active]="!t.reconciled">{{ t.reconciled ? 'Conciliada' : 'Efetivada' }}</span> }
              </td></ng-container>
            <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let t">
                @if (!t.reversed && canCancel()) { <button mat-button (click)="reverse(t)">Estornar</button> }
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
      switchMap(([c]) => this.api.transfers(c!).pipe(catchError(() => of([] as Transfer[])))),
    ),
  );

  create(): void {
    const c = this.context.id()!;
    this.api.bankAccounts(c).subscribe((accounts) => {
      const opts = accounts.filter((a) => a.active).map((a) => ({ value: a.id, label: a.name }));
      openForm(this.dialog, {
        title: 'Nova transferência',
        fields: [
          { key: 'fromAccountId', label: 'De', type: 'select', required: true, options: opts, span: 6 },
          { key: 'toAccountId', label: 'Para', type: 'select', required: true, options: opts, span: 6 },
          { key: 'amount', label: 'Valor (R$)', type: 'number', required: true, span: 6, min: 0.01 },
          { key: 'date', label: 'Data', type: 'date', required: true, value: todayIso(), span: 6 },
          { key: 'description', label: 'Descrição', maxLength: 200 },
        ],
        submit: (v) => this.api.transfer(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notify.success('Transferência registrada.');
        this.tick.update((t) => t + 1);
      });
    });
  }

  reverse(t: Transfer): void {
    askReason(this.dialog, 'Estornar transferência', `Estornar a transferência de ${t.fromAccountName} para ${t.toAccountName}?`, 'Estornar')
      .pipe(switchMap((reason) => this.api.reverseTransfer(this.context.id()!, t.id, reason)))
      .subscribe({
        next: () => { this.notify.success('Transferência estornada.'); this.tick.update((x) => x + 1); },
        error: (err: unknown) => this.notify.error(toProblem(err).detail),
      });
  }
}
