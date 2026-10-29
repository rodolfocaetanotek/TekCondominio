import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { ContaBancaria, FinanceiroApi, Extrato } from './financeiro-api';

@Component({
  selector: 'tc-contas-bancarias-tab',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatCheckboxModule, MatFormFieldModule, MatInputModule,
    RotuloPipe, DataIsoPipe, EstadoVazio],
  template: `
    <div class="head">
      <p class="tc-muted">Saldos calculados a partir do saldo inicial e de todas as movimentações registradas.</p>
      @if (podeEditar()) {
        <button mat-flat-button (click)="novaConta()"><mat-icon>add</mat-icon> Nova conta</button>
      }
    </div>
    <div class="accounts">
      @for (a of contas() ?? []; track a.id) {
        <button type="button" class="account tc-kpi tc-card" [class.tone-danger]="a.saldo < 0" [class.selected]="selecionado() === a.id"
          [class.inactive]="!a.ativo" [attr.aria-pressed]="selecionado() === a.id" (click)="selecionado.set(a.id)">
          <span class="icon"><mat-icon aria-hidden="true">{{ a.tipo === 'CASH' ? 'payments' : 'account_balance' }}</mat-icon></span>
          <span class="label">{{ a.nome }}</span>
          <span class="value money" [class.negative]="a.saldo < 0">{{ a.saldo | currency: 'BRL' }}</span>
          <span class="sub">{{ a.tipo | rotulo: 'bankKind' }}{{ a.codigoBanco ? ' · ' + a.codigoBanco + ' ag. ' + a.agencia + ' c/c ' + a.numeroConta : '' }}</span>
        </button>
      } @empty {
        <section class="tc-card empty">
          <tc-estado-vazio icone="account_balance" titulo="Nenhuma conta cadastrada" descricao="Cadastre a conta corrente e o caixa do condomínio para registrar pagamentos." />
        </section>
      }
    </div>

    @if (selecionado()) {
      <section class="tc-card statement">
        <div class="filters">
          <h3 class="tc-section-title">Extrato e conciliação</h3>
          <mat-form-field><mat-label>De</mat-label><input matInput type="date" [formControl]="de" /></mat-form-field>
          <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [formControl]="ate" /></mat-form-field>
        </div>
        @if (extrato(); as s) {
          <div class="summary">
            <div><span class="tc-section-label">Saldo anterior</span><strong class="money">{{ s.saldoInicial | currency: 'BRL' }}</strong></div>
            <div><span class="tc-section-label">Saldo final</span><strong class="money">{{ s.saldoFinal | currency: 'BRL' }}</strong></div>
            <div><span class="tc-section-label">Saldo conciliado</span><strong class="money">{{ s.saldoConciliado | currency: 'BRL' }}</strong></div>
          </div>
          <div class="scroll">
            <table class="lines">
              <thead><tr><th>Conciliado</th><th>Data</th><th>Histórico</th><th class="money">Valor</th><th class="money">Saldo</th></tr></thead>
              <tbody>
                @for (l of s.movimentacoes; track l.id + l.tipo) {
                  <tr>
                    <td><mat-checkbox [checked]="l.conciliada" [disabled]="!podeEditar()" (change)="alternar(l.tipo, l.id, $event.checked)"
                      [attr.aria-label]="'Conciliar ' + l.descricao" /></td>
                    <td>{{ l.data | dataIso }}</td>
                    <td>{{ l.descricao }}</td>
                    <td class="money" [class.negative]="l.valor < 0" [class.positive]="l.valor > 0">{{ l.valor | currency: 'BRL' }}</td>
                    <td class="money">{{ l.saldo | currency: 'BRL' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="tc-muted">Sem movimentações no período.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <p class="tc-muted small">Marque as movimentações conferidas no extrato do banco. Movimentações conciliadas não podem ser estornadas sem desfazer a conciliação.</p>
        }
      </section>
    }
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 0 16px; flex-wrap: wrap; }
    .head p { margin: 0; }
    .accounts { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(240px, 100%), 1fr)); gap: 12px; margin-bottom: 16px; }
    .accounts .empty { grid-column: 1 / -1; }
    .account { width: 100%; text-align: left; cursor: pointer; font: inherit; color: inherit; }
    .account:hover { border-color: color-mix(in srgb, var(--mat-sys-primary) 45%, var(--tc-border)); }
    .account.selected { border-color: var(--mat-sys-primary); box-shadow: 0 0 0 1px var(--mat-sys-primary); }
    .account.inactive { opacity: .6; }
    .account .value { text-align: left; }
    .account .label, .account .sub { overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); }
    .filters h3 { margin: 0; flex: 1 1 auto; }
    .summary { display: flex; gap: 12px 32px; flex-wrap: wrap; margin: 0 0 16px; padding: 12px 16px; border-radius: var(--tc-radius-sm);
      background: var(--tc-surface-muted); border: 1px solid var(--tc-border); }
    .summary div { display: grid; gap: 2px; }
    .summary .tc-section-label { margin: 0; }
    .summary strong { font: var(--mat-sys-title-medium); font-weight: 650; text-align: left; }
    .scroll { overflow-x: auto; }
  `,
})
export class ContasBancariasTab {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);

  protected readonly podeEditar = computed(() => this.contexto.pode('FINANCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly selecionado = signal<string | null>(null);
  private readonly recarga = signal(0);
  protected readonly de = new FormControl(hojeIso().slice(0, 8) + '01', { nonNullable: true });
  protected readonly ate = new FormControl(hojeIso(), { nonNullable: true });
  private readonly valorDe = toSignal(this.de.valueChanges, { initialValue: this.de.value });
  private readonly valorAte = toSignal(this.ate.valueChanges, { initialValue: this.ate.value });

  protected readonly contas = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c]) => this.api.contasBancarias(c!).pipe(catchError(() => of([] as ContaBancaria[])))),
    ),
  );

  protected readonly extrato = toSignal(
    combineLatest([toObservable(this.selecionado), toObservable(this.valorDe), toObservable(this.valorAte), toObservable(this.recarga)]).pipe(
      filter(([id]) => !!id && !!this.contexto.id()),
      switchMap(([id, de, ate]) => this.api.extrato(this.contexto.id()!, id!, de, ate).pipe(catchError(() => of(null as Extrato | null)))),
    ),
  );

  novaConta(): void {
    abrirFormulario(this.dialog, {
      titulo: 'Nova conta bancária ou caixa',
      introducao: 'O saldo inicial e a data de abertura não podem ser alterados depois (integridade do histórico).',
      campos: [
        { chave: 'nome', rotulo: 'Nome', obrigatorio: true, colunas: 8, tamanhoMaximo: 80 },
        { chave: 'tipo', rotulo: 'Tipo', tipo: 'select', obrigatorio: true, valor: 'CHECKING', colunas: 4, opcoes: opcoes('bankKind') },
        { chave: 'codigoBanco', rotulo: 'Banco (código)', colunas: 4, tamanhoMaximo: 5 },
        { chave: 'agencia', rotulo: 'Agência', colunas: 4, tamanhoMaximo: 10 },
        { chave: 'numeroConta', rotulo: 'Conta', colunas: 4, tamanhoMaximo: 20 },
        { chave: 'saldoInicial', rotulo: 'Saldo inicial (R$)', tipo: 'number', valor: 0, colunas: 6 },
        { chave: 'dataAbertura', rotulo: 'Data do saldo inicial', tipo: 'date', valor: hojeIso(), colunas: 6 },
      ],
      enviar: (v) => this.api.cadastrarContaBancaria(this.contexto.id()!, v),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notificacao.sucesso('Conta cadastrada.');
      this.recarga.update((t) => t + 1);
    });
  }

  alternar(tipo: string, id: string, conciliada: boolean): void {
    this.api.conciliar(this.contexto.id()!, [{ tipo, id }], conciliada).subscribe({
      next: () => this.recarga.update((t) => t + 1),
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }
}
