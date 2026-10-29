import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { DefinicaoCampo, abrirFormulario } from '../../comum/ui/formulario-dialog';
import { ContaOrcamento, FinanceiroApi } from './financeiro-api';

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

@Component({
  selector: 'tc-orcamento-tab',
  imports: [CurrencyPipe, DecimalPipe, MatButtonModule, MatIconModule],
  template: `
    <div class="head">
      <button mat-icon-button (click)="ano.set(ano() - 1)" aria-label="Ano anterior"><mat-icon>chevron_left</mat-icon></button>
      <strong class="year">{{ ano() }}</strong>
      <button mat-icon-button (click)="ano.set(ano() + 1)" aria-label="Próximo ano"><mat-icon>chevron_right</mat-icon></button>
      <span class="tc-muted hint">Previsto × realizado (regime de caixa). {{ podeEditar() ? 'Clique em uma conta para editar o previsto mês a mês.' : '' }}</span>
    </div>
    <section class="tc-table-card">
      <div class="table-scroll">
        <table class="lines tc-cards-mobile">
          <thead>
            <tr><th>Conta</th><th class="money">Previsto</th><th class="money">Realizado</th><th class="money">Diferença</th><th>Execução</th></tr>
          </thead>
          <tbody>
            @for (r of linhas(); track r.idContaPlano) {
              <tr [class.is-clickable]="podeEditar()" (click)="podeEditar() && editar(r)" [attr.tabindex]="podeEditar() ? 0 : null" (keydown.enter)="podeEditar() && editar(r)">
                <td><span class="tc-muted">{{ r.codigo }}</span> {{ r.nome }}</td>
                <td class="money">{{ r.totalPrevisto | currency: 'BRL' }}</td>
                <td class="money">{{ r.totalRealizado | currency: 'BRL' }}</td>
                <td class="money" [class.negative]="acimaDoPrevisto(r)">{{ r.totalRealizado - r.totalPrevisto | currency: 'BRL' }}</td>
                <td>
                  @if (r.totalPrevisto > 0) {
                    <div class="exec"><div class="meter" [attr.aria-label]="percentual(r) + '% executado'"><span [style.width.%]="min(percentual(r), 100)" [class.over]="acimaDoPrevisto(r)"></span></div>
                    <small [class.over-text]="acimaDoPrevisto(r)">{{ percentual(r) | number: '1.0-0' }}%{{ acimaDoPrevisto(r) ? ' (acima do previsto)' : '' }}</small></div>
                  } @else { <small class="tc-muted">sem previsão</small> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .head { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; flex-wrap: wrap; }
    .year { font: var(--mat-sys-title-large); font-weight: 650; font-variant-numeric: tabular-nums; }
    .hint { flex: 1 1 260px; margin-left: 8px; }
    .exec { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .meter { width: 120px; height: 8px; border-radius: 4px; background: var(--tc-surface-muted); box-shadow: inset 0 0 0 1px var(--tc-border); overflow: hidden; }
    .meter span { display: block; height: 100%; background: var(--mat-sys-primary); border-radius: 4px; }
    .meter span.over { background: var(--tc-danger); }
    .over-text { color: var(--tc-danger); font-weight: 600; }
  `,
})
export class OrcamentoTab {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly ano = signal(new Date().getFullYear());
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.pode('FINANCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  private readonly data = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.ano), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, y]) => this.api.orcamento(c!, y).pipe(catchError(() => of(null)))),
    ),
  );
  protected readonly linhas = computed(() => this.data()?.contas ?? []);

  percentual(r: ContaOrcamento): number {
    return r.totalPrevisto > 0 ? (r.totalRealizado / r.totalPrevisto) * 100 : 0;
  }

  acimaDoPrevisto(r: ContaOrcamento): boolean {
    return r.totalPrevisto > 0 && r.totalRealizado > r.totalPrevisto;
  }

  min(a: number, b: number): number {
    return Math.min(a, b);
  }

  editar(r: ContaOrcamento): void {
    const campos: DefinicaoCampo[] = MESES.map((m, i) => ({ chave: 'm' + (i + 1), rotulo: m, tipo: 'number', valor: r.previsto[i], colunas: 3, min: 0 }));
    abrirFormulario(this.dialog, {
      titulo: `Previsto ${this.ano()} — ${r.codigo} ${r.nome}`,
      introducao: 'Informe o valor previsto de cada mês.',
      campos,
      enviar: (v) => this.api.salvarOrcamento(this.contexto.id()!, this.ano(), MESES.map((_, i) => ({
        idContaPlano: r.idContaPlano, mes: i + 1, valor: Number(v['m' + (i + 1)] ?? 0),
      }))),
    }, '640px').pipe(filter((x) => !!x)).subscribe(() => {
      this.notificacao.sucesso('Orçamento atualizado.');
      this.recarga.update((t) => t + 1);
    });
  }
}
