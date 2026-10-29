import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { openForm } from '../../comum/ui/formulario-dialog';
import { IsoDatePipe } from '../../comum/ui/data-iso.pipe';
import { ContaBancaria, FinanceApi, ItemRetorno } from '../financeiro/financeiro-api';

/**
 * Retorno de cobrança (CNAB 240): o arquivo baixado do banco dá baixa automática nas cobranças liquidadas,
 * identificadas pelo número de cobrança informado no boleto. O que não puder ser baixado fica pendente.
 */
@Component({
  selector: 'tc-bank-return-tab',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule,
    MatTooltipModule, IsoDatePipe],
  template: `
    <section class="tc-card">
      <div class="filters">
        <mat-form-field class="search"><mat-label>Conta de cobrança</mat-label>
          <mat-select [formControl]="account">
            @for (a of accounts() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}{{ a.codigoBanco ? ' · banco ' + a.codigoBanco : '' }}</mat-option> }
          </mat-select></mat-form-field>
        @if (canEdit() && account.value) {
          <label class="tc-file-button"><input type="file" accept=".ret,.RET,.txt,.TXT,.cnab" (change)="upload($event)" [disabled]="busy()" />
            <mat-icon>upload_file</mat-icon> {{ busy() ? 'Processando…' : 'Processar retorno CNAB 240' }}</label>
        }
      </div>
      <p class="tc-muted small hint">Layout FEBRABAN 240 posições (segmentos T e U). A cobrança é localizada pelo "seu número" ou nosso número
        igual ao <strong>número de cobrança</strong> do título. Cada banco exige homologação do layout antes do uso em produção.</p>
    </section>

    <section class="tc-card">
      <h3 class="tc-section-title">Pendências de baixa ({{ pending()?.length ?? 0 }})</h3>
      <div class="scroll">
      <table class="lines tc-cards-mobile">
        <thead><tr><th>Pago em</th><th>Seu nº / nosso nº</th><th class="money">Valor pago</th><th>Motivo</th><th></th></tr></thead>
        <tbody>
          @for (i of pending() ?? []; track i.id) {
            <tr>
              <td>{{ i.dataOcorrencia | isoDate }}</td>
              <td>{{ i.seuNumero || '—' }} / {{ i.nossoNumero || '—' }}</td>
              <td class="money">{{ i.valorPago | currency: 'BRL' }}</td>
              <td>{{ i.mensagem }}{{ i.descricaoLancamento ? ' · ' + i.descricaoLancamento : '' }}</td>
              <td class="acts">
                @if (canEdit()) {
                  <button mat-icon-button (click)="settle(i)" matTooltip="Baixar em uma cobrança" aria-label="Baixar"><mat-icon>task_alt</mat-icon></button>
                  <button mat-icon-button (click)="ignore(i)" matTooltip="Descartar com motivo" aria-label="Descartar"><mat-icon>block</mat-icon></button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="tc-muted">Nenhuma pendência.</td></tr>
          }
        </tbody>
      </table>
      </div>
    </section>

    <section class="tc-card">
      <h3 class="tc-section-title">Arquivos processados</h3>
      <div class="scroll">
      <table class="lines tc-cards-mobile">
        <thead><tr><th>Processado em</th><th>Arquivo</th><th>Banco</th><th>Títulos</th><th>Baixados</th><th>Pendentes</th><th>Por</th></tr></thead>
        <tbody>
          @for (f of files() ?? []; track f.id) {
            <tr><td>{{ f.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td><td>{{ f.nomeArquivo }}</td><td>{{ f.codigoBanco }}</td>
              <td>{{ f.totalItens }}</td><td>{{ f.baixados }}</td><td [class.warn]="f.pendentes > 0">{{ f.pendentes }}</td><td>{{ f.nomeCriadoPor }}</td></tr>
          } @empty {
            <tr><td colspan="7" class="tc-muted">Nenhum retorno processado.</td></tr>
          }
        </tbody>
      </table>
      </div>
    </section>
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .small { font: var(--mat-sys-body-small); }
    .hint { margin: 0; }
    .tc-section-title { margin-top: 0; }
    .scroll { overflow-x: auto; }
    .warn { color: var(--tc-danger); font-weight: 600; }
    .acts { white-space: nowrap; }
  `,
})
export class BankReturnTab {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly busy = signal(false);
  protected readonly account = new FormControl<string | null>(null);

  protected readonly canEdit = computed(() => this.context.can('BILLING_EDIT') && this.context.can('FINANCE_EDIT')
    && this.context.selected()?.situacao === 'ACTIVE');
  private readonly source = combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(filter(([c]) => !!c));
  protected readonly accounts = toSignal(toObservable(this.context.id).pipe(filter((c) => !!c),
    switchMap((c) => this.api.bankAccounts(c!).pipe(map((list) => list.filter((a) => a.tipo !== 'CASH' && a.ativo)),
      catchError(() => of([] as ContaBancaria[]))))));
  protected readonly files = toSignal(this.source.pipe(switchMap(([c]) => this.api.returnFiles(c!).pipe(catchError(() => of([]))))));
  protected readonly pending = toSignal(this.source.pipe(switchMap(([c]) => this.api.pendingReturnItems(c!).pipe(catchError(() => of([] as ItemRetorno[]))))));

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.busy.set(true);
    this.api.processReturn(this.context.id()!, this.account.value!, file).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.notify.success(`Retorno processado: ${r.baixados} cobrança(s) baixada(s), ${r.pendentes} pendente(s).`);
        this.tick.update((t) => t + 1);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.notify.error(toProblem(err).detail);
      },
    });
  }

  settle(item: ItemRetorno): void {
    const c = this.context.id()!;
    this.api.entries(c, { natureza: 'INCOME', situacao: ['OPEN', 'PARTIAL'], tamanho: 200, ordem: 'dataVencimento,asc' }).subscribe((page) =>
      openForm(this.dialog, {
        title: 'Baixar pagamento do retorno',
        intro: `Valor pago: R$ ${item.valorPago.toFixed(2).replace('.', ',')}. Escolha a cobrança correspondente.`,
        fields: [{ key: 'idLancamento', label: 'Cobrança', type: 'select', required: true,
          options: page.conteudo.map((e) => ({ value: e.id, label: `${e.rotuloUnidade ?? '—'} · ${e.descricao} · venc. ${e.dataVencimento.split('-').reverse().join('/')} · R$ ${e.valorAberto.toFixed(2).replace('.', ',')}` })) }],
        submit: (v) => this.api.settleReturnItem(c, item.id, v['idLancamento'] as string),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notify.success('Pagamento baixado.');
        this.tick.update((t) => t + 1);
      }));
  }

  ignore(item: ItemRetorno): void {
    openForm(this.dialog, {
      title: 'Descartar pendência do retorno',
      fields: [{ key: 'observacao', label: 'Motivo', required: true, maxLength: 255 }],
      submit: (v) => this.api.ignoreReturnItem(this.context.id()!, item.id, v['observacao'] as string),
    }).pipe(filter((r) => !!r)).subscribe(() => this.tick.update((t) => t + 1));
  }
}
