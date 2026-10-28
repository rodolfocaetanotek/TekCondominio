import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { askConfirm } from '../../shared/ui/confirm-dialog';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { ContaBancaria, FinanceApi, LinhaExtrato } from './finance-api';

/**
 * Conciliação pelo extrato do banco: importa o OFX, casa automaticamente os pares inequívocos e deixa as demais
 * linhas para decisão (aceitar sugestão, lançar tarifa/rendimento ou ignorar com motivo).
 */
@Component({
  selector: 'tc-reconciliation-tab',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, MatTooltipModule, IsoDatePipe],
  template: `
    <section class="tc-card">
      <div class="filters">
        <mat-form-field class="search"><mat-label>Conta bancária</mat-label>
          <mat-select [formControl]="account">
            @for (a of accounts() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Situação</mat-label>
          <mat-select [formControl]="status">
            <mat-option value="UNMATCHED">Pendentes</mat-option>
            <mat-option value="MATCHED">Conciliadas</mat-option>
            <mat-option value="IGNORED">Ignoradas</mat-option>
            <mat-option value="">Todas</mat-option>
          </mat-select></mat-form-field>
        @if (canEdit() && account.value) {
          <label class="tc-file-button"><input type="file" accept=".ofx,.OFX" (change)="upload($event)" [disabled]="busy()" />
            <mat-icon>upload_file</mat-icon> {{ busy() ? 'Importando…' : 'Importar extrato OFX' }}</label>
        }
      </div>
      <p class="tc-muted small hint">O sistema só concilia sozinho quando valor e data (até 3 dias de diferença) apontam para uma única
        movimentação. Os demais casos aparecem abaixo com sugestões para você decidir.</p>
    </section>

    @if (account.value) {
      <section class="tc-table-card">
        <div class="table-scroll">
        <table class="lines tc-cards-mobile">
          <thead><tr><th>Data</th><th>Histórico do banco</th><th class="money">Valor</th><th>Situação / sugestões</th><th></th></tr></thead>
          <tbody>
            @for (l of lines() ?? []; track l.id) {
              <tr>
                <td>{{ l.dataLancamento | isoDate }}</td>
                <td>{{ l.historico }}<br /><span class="tc-muted small">{{ l.fitId }}</span></td>
                <td class="money" [class.negative]="l.valor < 0" [class.positive]="l.valor > 0">{{ l.valor | currency: 'BRL' }}</td>
                <td class="sugg-cell">
                  @switch (l.situacao) {
                    @case ('MATCHED') { <span class="tc-status is-active">Conciliada</span> }
                    @case ('IGNORED') { <span class="tc-status is-inactive">Ignorada</span> <span class="tc-muted small">{{ l.observacao }}</span> }
                    @default {
                      @for (s of l.sugestoes; track s.id) {
                        <button mat-stroked-button class="sugg" [disabled]="!canEdit()" (click)="match(l, s.tipo, s.id)"
                          [matTooltip]="'Conciliar com esta movimentação'">
                          {{ s.data | isoDate }} · {{ s.descricao }}</button>
                      } @empty { <span class="tc-muted">Sem movimentação compatível no sistema</span> }
                    }
                  }
                </td>
                <td class="acts">
                  @if (canEdit()) {
                    @if (l.situacao === 'UNMATCHED') {
                      <button mat-icon-button (click)="register(l)" matTooltip="Lançar e baixar (tarifa, rendimento…)" aria-label="Lançar"><mat-icon>post_add</mat-icon></button>
                      <button mat-icon-button (click)="ignore(l)" matTooltip="Ignorar com motivo" aria-label="Ignorar"><mat-icon>block</mat-icon></button>
                    } @else {
                      <button mat-icon-button (click)="unmatch(l)" matTooltip="Desfazer" aria-label="Desfazer"><mat-icon>undo</mat-icon></button>
                    }
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="tc-muted">Nenhuma linha de extrato nesta situação.</td></tr>
            }
          </tbody>
        </table>
        </div>
      </section>
    }
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .small { font: var(--mat-sys-body-small); }
    .hint { margin: 0; }
    .lines td { vertical-align: top; }
    .sugg { margin: 0 6px 6px 0; max-width: 100%; height: auto; min-height: 32px; white-space: normal; text-align: left; }
    .acts { white-space: nowrap; }
    @media (max-width: 720px) {
      .lines td.sugg-cell { flex-wrap: wrap; justify-content: flex-end; }
    }
  `,
})
export class ReconciliationTab {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly busy = signal(false);
  protected readonly account = new FormControl<string | null>(null);
  protected readonly status = new FormControl('UNMATCHED', { nonNullable: true });
  private readonly accountValue = toSignal(this.account.valueChanges, { initialValue: null });
  private readonly statusValue = toSignal(this.status.valueChanges, { initialValue: 'UNMATCHED' });

  protected readonly canEdit = computed(() => this.context.can('FINANCE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly accounts = toSignal(toObservable(this.context.id).pipe(
    filter((c) => !!c),
    switchMap((c) => this.api.bankAccounts(c!).pipe(catchError(() => of([] as ContaBancaria[])))),
  ));
  protected readonly lines = toSignal(combineLatest([toObservable(this.accountValue), toObservable(this.statusValue), toObservable(this.tick)]).pipe(
    filter(([a]) => !!a && !!this.context.id()),
    switchMap(([a, s]) => this.api.statementLines(this.context.id()!, a!, s || undefined).pipe(catchError(() => of([] as LinhaExtrato[])))),
  ));

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.send(file, false);
  }

  private send(file: File, ignorarConferenciaConta: boolean): void {
    this.busy.set(true);
    this.api.importStatement(this.context.id()!, this.account.value!, file, ignorarConferenciaConta).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.notify.success(`${r.linhasNovas} linha(s) importada(s), ${r.duplicadas} repetida(s); ${r.conciliadasAutomaticamente} conciliada(s) automaticamente; ${r.pendentes} pendente(s).`);
        this.tick.update((t) => t + 1);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const p = toProblem(err);
        if (p.codigo === 'STATEMENT_ACCOUNT_MISMATCH') {
          askConfirm(this.dialog, 'Conta diferente', p.detail + ' Deseja importar mesmo assim?', 'Importar assim mesmo', true)
            .subscribe(() => this.send(file, true));
        } else {
          this.notify.error(p.detail);
        }
      },
    });
  }

  match(l: LinhaExtrato, type: string, idMovimentacao: string): void {
    this.run(this.api.matchLine(this.context.id()!, l.id, type, idMovimentacao), 'Linha conciliada.');
  }

  unmatch(l: LinhaExtrato): void {
    this.run(this.api.unmatchLine(this.context.id()!, l.id), 'Conciliação desfeita.');
  }

  ignore(l: LinhaExtrato): void {
    openForm(this.dialog, {
      title: 'Ignorar linha do extrato',
      fields: [{ key: 'observacao', label: 'Motivo', required: true, maxLength: 255 }],
      submit: (v) => this.api.ignoreLine(this.context.id()!, l.id, v['observacao'] as string),
    }).pipe(filter((r) => !!r)).subscribe(() => this.tick.update((t) => t + 1));
  }

  register(l: LinhaExtrato): void {
    const nature = l.valor > 0 ? 'INCOME' : 'EXPENSE';
    this.api.chartAccounts().subscribe((accounts) => openForm(this.dialog, {
      title: l.valor > 0 ? 'Lançar receita do extrato' : 'Lançar despesa do extrato',
      intro: 'O lançamento é criado já baixado na data do extrato e conciliado com esta linha.',
      fields: [
        { key: 'descricao', label: 'Descrição', required: true, maxLength: 200, value: l.historico },
        { key: 'idContaPlano', label: 'Conta do plano de contas', type: 'select', required: true,
          options: accounts.filter((a) => a.natureza === nature && a.analitica && a.ativo).map((a) => ({ value: a.id, label: `${a.codigo} ${a.nome}` })) },
      ],
      submit: (v) => this.api.registerLine(this.context.id()!, l.id, v),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Lançamento criado e conciliado.');
      this.tick.update((t) => t + 1);
    }));
  }

  private run(call: ReturnType<FinanceApi['matchLine']>, message: string): void {
    call.subscribe({
      next: () => {
        this.notify.success(message);
        this.tick.update((t) => t + 1);
      },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }
}
