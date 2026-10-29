import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { catchError, filter, of, switchMap } from 'rxjs';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { MatIconModule } from '@angular/material/icon';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';

interface Semana { inicio: string; fim: string; entradasPrevistas: number; saidasPrevistas: number; saldoProjetado: number; }
interface ProjecaoCaixa { dataBase: string; saldoAtual: number; aReceberVencido: number; aPagarVencido: number; menorSaldoProjetado: number; dataMenorSaldo: string; semanas: Semana[]; observacoes: string[]; }
interface DespesaForaDoPadrao { mes: string; idContaPlano: string; codigoConta: string; nomeConta: string; valor: number; mediaHistorica: number; razao?: number; explicacao: string; }

/** Projeção de caixa e despesas fora do padrão — calculadas apenas com dados registrados. */
@Component({
  selector: 'tc-indicadores-tab',
  imports: [CurrencyPipe, DecimalPipe, MatIconModule, EstadoVazio, DataIsoPipe],
  template: `
    @if (projecao(); as p) {
      <section class="tc-card">
        <h3 class="tc-section-title">Projeção de caixa — próximas {{ p.semanas.length }} semanas</h3>
        <div class="tc-kpis inset">
          <div class="tc-kpi tc-card tone-info">
            <span class="icon"><mat-icon aria-hidden="true">account_balance_wallet</mat-icon></span>
            <span class="label">Saldo atual (todas as contas)</span><span class="value">{{ p.saldoAtual | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card" [class.tone-danger]="p.menorSaldoProjetado < 0">
            <span class="icon"><mat-icon aria-hidden="true">{{ p.menorSaldoProjetado < 0 ? 'warning' : 'show_chart' }}</mat-icon></span>
            <span class="label">Menor saldo projetado</span><span class="value">{{ p.menorSaldoProjetado | currency: 'BRL' }}</span>
            <span class="sub">semana de {{ p.dataMenorSaldo | dataIso }}</span>
          </div>
          <div class="tc-kpi tc-card tone-success">
            <span class="icon"><mat-icon aria-hidden="true">call_received</mat-icon></span>
            <span class="label">A receber vencido (fora da projeção)</span><span class="value">{{ p.aReceberVencido | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-warning">
            <span class="icon"><mat-icon aria-hidden="true">call_made</mat-icon></span>
            <span class="label">A pagar vencido (fora da projeção)</span><span class="value">{{ p.aPagarVencido | currency: 'BRL' }}</span>
          </div>
        </div>
        <div class="scroll">
          <table class="lines">
            <thead><tr><th>Semana</th><th class="money">Entradas previstas</th><th class="money">Saídas previstas</th><th class="money">Saldo projetado</th></tr></thead>
            <tbody>
              @for (largura of p.semanas; track largura.inicio) {
                <tr><td>{{ largura.inicio | dataIso }} – {{ largura.fim | dataIso }}</td>
                  <td class="money positive">{{ largura.entradasPrevistas | currency: 'BRL' }}</td>
                  <td class="money negative">{{ largura.saidasPrevistas | currency: 'BRL' }}</td>
                  <td class="money" [class.negative]="largura.saldoProjetado < 0"><strong>{{ largura.saldoProjetado | currency: 'BRL' }}</strong></td></tr>
              }
            </tbody>
          </table>
        </div>
        @for (n of p.observacoes; track n) { <p class="small tc-muted">{{ n }}</p> }
      </section>
    }
    <section class="tc-card">
      <h3 class="tc-section-title">Despesas fora do padrão (últimos 3 meses encerrados)</h3>
      @for (a of despesasForaDoPadrao() ?? []; track a.mes + a.codigoConta) {
        <div class="row"><div><strong>{{ a.codigoConta }} {{ a.nomeConta }}</strong> · {{ a.mes.slice(5) }}/{{ a.mes.slice(0, 4) }}<br />
          <span class="small">{{ a.explicacao }}</span></div>
          @if (a.razao) { <strong class="money negative">{{ a.razao | number: '1.1-1' }}×</strong> }</div>
      } @empty {
        <tc-estado-vazio icone="insights" titulo="Nenhuma despesa fora do padrão"
          descricao="(É preciso histórico de pelo menos 3 meses com pagamentos na conta.)" />
      }
    </section>
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .tc-section-title { margin-top: 0; }
    .small { font: var(--mat-sys-body-small); }
    .scroll { overflow-x: auto; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--tc-border); }
    .row:last-child { border-bottom: 0; }
    .row > div { min-width: 0; }
  `,
})
export class IndicadoresTab {
  private readonly contexto = inject(CondominioContexto);
  private readonly http = inject(HttpClient);
  private readonly condominio = toObservable(this.contexto.id).pipe(filter((c) => !!c));
  protected readonly projecao = toSignal(this.condominio.pipe(switchMap((c) =>
    this.http.get<ProjecaoCaixa>(`/api/v1/condominios/${c}/indicadores/projecao-caixa`).pipe(catchError(() => of(null))))));
  protected readonly despesasForaDoPadrao = toSignal(this.condominio.pipe(switchMap((c) =>
    this.http.get<DespesaForaDoPadrao[]>(`/api/v1/condominios/${c}/indicadores/despesas-fora-do-padrao`).pipe(catchError(() => of([] as DespesaForaDoPadrao[]))))));
}
