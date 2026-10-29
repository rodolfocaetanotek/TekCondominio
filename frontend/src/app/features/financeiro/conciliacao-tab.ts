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
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { pedirConfirmacao } from '../../comum/ui/confirmacao-dialog';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { ContaBancaria, FinanceiroApi, LinhaExtrato } from './financeiro-api';

/**
 * Conciliação pelo extrato do banco: importa o OFX, casa automaticamente os pares inequívocos e deixa as demais
 * linhas para decisão (aceitar sugestão, lançar tarifa/rendimento ou ignorar com motivo).
 */
@Component({
  selector: 'tc-conciliacao-tab',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, MatTooltipModule, DataIsoPipe],
  template: `
    <section class="tc-card">
      <div class="filters">
        <mat-form-field class="search"><mat-label>Conta bancária</mat-label>
          <mat-select [formControl]="conta">
            @for (a of contas() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field><mat-label>Situação</mat-label>
          <mat-select [formControl]="situacao">
            <mat-option value="UNMATCHED">Pendentes</mat-option>
            <mat-option value="MATCHED">Conciliadas</mat-option>
            <mat-option value="IGNORED">Ignoradas</mat-option>
            <mat-option value="">Todas</mat-option>
          </mat-select></mat-form-field>
        @if (podeEditar() && conta.value) {
          <label class="tc-file-button"><input type="file" accept=".ofx,.OFX" (change)="enviarArquivo($event)" [disabled]="ocupado()" />
            <mat-icon>upload_file</mat-icon> {{ ocupado() ? 'Importando…' : 'Importar extrato OFX' }}</label>
        }
      </div>
      <p class="tc-muted small hint">O sistema só concilia sozinho quando valor e data (até 3 dias de diferença) apontam para uma única
        movimentação. Os demais casos aparecem abaixo com sugestões para você decidir.</p>
    </section>

    @if (conta.value) {
      <section class="tc-table-card">
        <div class="table-scroll">
        <table class="lines tc-cards-mobile">
          <thead><tr><th>Data</th><th>Histórico do banco</th><th class="money">Valor</th><th>Situação / sugestões</th><th></th></tr></thead>
          <tbody>
            @for (l of linhas() ?? []; track l.id) {
              <tr>
                <td>{{ l.dataLancamento | dataIso }}</td>
                <td>{{ l.historico }}<br /><span class="tc-muted small">{{ l.fitId }}</span></td>
                <td class="money" [class.negative]="l.valor < 0" [class.positive]="l.valor > 0">{{ l.valor | currency: 'BRL' }}</td>
                <td class="sugg-cell">
                  @switch (l.situacao) {
                    @case ('MATCHED') { <span class="tc-status is-active">Conciliada</span> }
                    @case ('IGNORED') { <span class="tc-status is-inactive">Ignorada</span> <span class="tc-muted small">{{ l.observacao }}</span> }
                    @default {
                      @for (s of l.sugestoes; track s.id) {
                        <button mat-stroked-button class="sugg" [disabled]="!podeEditar()" (click)="conciliar(l, s.tipo, s.id)"
                          [matTooltip]="'Conciliar com esta movimentação'">
                          {{ s.data | dataIso }} · {{ s.descricao }}</button>
                      } @empty { <span class="tc-muted">Sem movimentação compatível no sistema</span> }
                    }
                  }
                </td>
                <td class="acts">
                  @if (podeEditar()) {
                    @if (l.situacao === 'UNMATCHED') {
                      <button mat-icon-button (click)="lancar(l)" matTooltip="Lançar e baixar (tarifa, rendimento…)" aria-label="Lançar"><mat-icon>post_add</mat-icon></button>
                      <button mat-icon-button (click)="ignorar(l)" matTooltip="Ignorar com motivo" aria-label="Ignorar"><mat-icon>block</mat-icon></button>
                    } @else {
                      <button mat-icon-button (click)="desfazerConciliacao(l)" matTooltip="Desfazer" aria-label="Desfazer"><mat-icon>undo</mat-icon></button>
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
export class ConciliacaoTab {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly ocupado = signal(false);
  protected readonly conta = new FormControl<string | null>(null);
  protected readonly situacao = new FormControl('UNMATCHED', { nonNullable: true });
  private readonly valorConta = toSignal(this.conta.valueChanges, { initialValue: null });
  private readonly valorSituacao = toSignal(this.situacao.valueChanges, { initialValue: 'UNMATCHED' });

  protected readonly podeEditar = computed(() => this.contexto.pode('FINANCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly contas = toSignal(toObservable(this.contexto.id).pipe(
    filter((c) => !!c),
    switchMap((c) => this.api.contasBancarias(c!).pipe(catchError(() => of([] as ContaBancaria[])))),
  ));
  protected readonly linhas = toSignal(combineLatest([toObservable(this.valorConta), toObservable(this.valorSituacao), toObservable(this.recarga)]).pipe(
    filter(([a]) => !!a && !!this.contexto.id()),
    switchMap(([a, s]) => this.api.linhasExtrato(this.contexto.id()!, a!, s || undefined).pipe(catchError(() => of([] as LinhaExtrato[])))),
  ));

  enviarArquivo(evento: Event): void {
    const campoArquivo = evento.target as HTMLInputElement;
    const arquivo = campoArquivo.files?.[0];
    campoArquivo.value = '';
    if (!arquivo) return;
    this.enviar(arquivo, false);
  }

  private enviar(arquivo: File, ignorarConferenciaConta: boolean): void {
    this.ocupado.set(true);
    this.api.importarExtrato(this.contexto.id()!, this.conta.value!, arquivo, ignorarConferenciaConta).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.notificacao.sucesso(`${r.linhasNovas} linha(s) importada(s), ${r.duplicadas} repetida(s); ${r.conciliadasAutomaticamente} conciliada(s) automaticamente; ${r.pendentes} pendente(s).`);
        this.recarga.update((t) => t + 1);
      },
      error: (err: unknown) => {
        this.ocupado.set(false);
        const p = paraProblema(err);
        if (p.codigo === 'STATEMENT_ACCOUNT_MISMATCH') {
          pedirConfirmacao(this.dialog, 'Conta diferente', p.detail + ' Deseja importar mesmo assim?', 'Importar assim mesmo', true)
            .subscribe(() => this.enviar(arquivo, true));
        } else {
          this.notificacao.erro(p.detail);
        }
      },
    });
  }

  conciliar(l: LinhaExtrato, tipo: string, idMovimentacao: string): void {
    this.executar(this.api.conciliarLinha(this.contexto.id()!, l.id, tipo, idMovimentacao), 'Linha conciliada.');
  }

  desfazerConciliacao(l: LinhaExtrato): void {
    this.executar(this.api.desfazerConciliacaoLinha(this.contexto.id()!, l.id), 'Conciliação desfeita.');
  }

  ignorar(l: LinhaExtrato): void {
    abrirFormulario(this.dialog, {
      titulo: 'Ignorar linha do extrato',
      campos: [{ chave: 'observacao', rotulo: 'Motivo', obrigatorio: true, tamanhoMaximo: 255 }],
      enviar: (v) => this.api.ignorarLinha(this.contexto.id()!, l.id, v['observacao'] as string),
    }).pipe(filter((r) => !!r)).subscribe(() => this.recarga.update((t) => t + 1));
  }

  lancar(l: LinhaExtrato): void {
    const natureza = l.valor > 0 ? 'INCOME' : 'EXPENSE';
    this.api.contasPlano().subscribe((contas) => abrirFormulario(this.dialog, {
      titulo: l.valor > 0 ? 'Lançar receita do extrato' : 'Lançar despesa do extrato',
      introducao: 'O lançamento é criado já baixado na data do extrato e conciliado com esta linha.',
      campos: [
        { chave: 'descricao', rotulo: 'Descrição', obrigatorio: true, tamanhoMaximo: 200, valor: l.historico },
        { chave: 'idContaPlano', rotulo: 'Conta do plano de contas', tipo: 'select', obrigatorio: true,
          opcoes: contas.filter((a) => a.natureza === natureza && a.analitica && a.ativo).map((a) => ({ valor: a.id, rotulo: `${a.codigo} ${a.nome}` })) },
      ],
      enviar: (v) => this.api.lancarLinha(this.contexto.id()!, l.id, v),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notificacao.sucesso('Lançamento criado e conciliado.');
      this.recarga.update((t) => t + 1);
    }));
  }

  private executar(chamada: ReturnType<FinanceiroApi['conciliarLinha']>, mensagem: string): void {
    chamada.subscribe({
      next: () => {
        this.notificacao.sucesso(mensagem);
        this.recarga.update((t) => t + 1);
      },
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }
}
