import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, filter, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { FinanceiroApi } from '../financeiro/financeiro-api';
import { OperacaoApi, RelatorioTabela } from './operacao-api';

const RELATORIOS = [
  { tipo: 'receitas-despesas', rotulo: 'Receitas e despesas', grupo: 'Financeiro' },
  { tipo: 'balancete', rotulo: 'Balancete financeiro', grupo: 'Financeiro' },
  { tipo: 'fluxo-caixa', rotulo: 'Fluxo de caixa', grupo: 'Financeiro' },
  { tipo: 'pagamentos', rotulo: 'Pagamentos realizados', grupo: 'Financeiro' },
  { tipo: 'contas-pagar', rotulo: 'Contas a pagar', grupo: 'Financeiro' },
  { tipo: 'contas-receber', rotulo: 'Contas a receber', grupo: 'Financeiro' },
  { tipo: 'movimentacao-bancaria', rotulo: 'Movimentação por conta bancária', grupo: 'Financeiro' },
  { tipo: 'orcamento', rotulo: 'Orçamento previsto × realizado', grupo: 'Financeiro' },
  { tipo: 'despesas-por-fornecedor', rotulo: 'Despesas por fornecedor', grupo: 'Financeiro' },
  { tipo: 'inadimplencia', rotulo: 'Inadimplência', grupo: 'Cobrança' },
  { tipo: 'rateios', rotulo: 'Rateios', grupo: 'Cobrança' },
  { tipo: 'ocorrencias', rotulo: 'Histórico de ocorrências', grupo: 'Administração' },
  { tipo: 'contratos', rotulo: 'Contratos (vigentes, a vencer, vencidos)', grupo: 'Administração' },
];

@Component({
  selector: 'tc-relatorios-page',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    CabecalhoPagina, CondominioObrigatorio, EstadoVazio],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Análise" titulo="Relatórios" [subtitulo]="contexto.selecionado()?.nome" />
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        <section class="tc-card filters">
          <mat-form-field class="search kind"><mat-label>Relatório</mat-label>
            <mat-select [formControl]="tipo">
              @for (r of relatorios; track r.tipo) { <mat-option [value]="r.tipo">{{ r.grupo }} · {{ r.rotulo }}</mat-option> }
            </mat-select></mat-form-field>
          @if (tipo.value === 'orcamento') {
            <mat-form-field><mat-label>Ano</mat-label><input matInput type="number" [formControl]="ano" /></mat-form-field>
          } @else {
            <mat-form-field><mat-label>{{ tipo.value === 'inadimplencia' ? 'Posição em' : 'De' }}</mat-label>
              <input matInput type="date" [formControl]="tipo.value === 'inadimplencia' ? ate : de" /></mat-form-field>
            @if (tipo.value !== 'inadimplencia' && tipo.value !== 'contratos') {
              <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [formControl]="ate" /></mat-form-field>
            }
          }
          @if (tipo.value === 'movimentacao-bancaria') {
            <mat-form-field><mat-label>Conta</mat-label><mat-select [formControl]="banco">
              @for (b of bancos(); track b.id) { <mat-option [value]="b.id">{{ b.nome }}</mat-option> }</mat-select></mat-form-field>
          }
          <button mat-flat-button class="run" (click)="executar()"><mat-icon>play_arrow</mat-icon> Gerar</button>
          @if (podeExportar() && tabela()) {
            <span class="exports" role="group" aria-label="Exportar relatório">
              <button mat-stroked-button (click)="exportar('PDF')"><mat-icon>picture_as_pdf</mat-icon> PDF</button>
              <button mat-stroked-button (click)="exportar('XLSX')"><mat-icon>table_view</mat-icon> Excel</button>
              <button mat-stroked-button (click)="exportar('CSV')"><mat-icon>description</mat-icon> CSV</button>
            </span>
          }
        </section>
        @if (carregando()) { <mat-progress-bar class="loading" mode="indeterminate" /> }
        @if (erro(); as mensagem) {
          <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível gerar" [descricao]="mensagem" /></section>
        } @else if (tabela(); as t) {
          <section class="tc-card report">
            <header class="report-head">
              <h2 class="tc-section-title">{{ t.titulo }}</h2>
              @if (t.subtitulo) { <p class="tc-muted">{{ t.subtitulo }}</p> }
            </header>
            <div class="table-scroll" tabindex="0" role="region" [attr.aria-label]="t.titulo">
              <table class="lines">
                <thead><tr>@for (c of t.colunas; track c.chave) { <th [class.num]="ehNumero(c.tipo)">{{ c.rotulo }}</th> }</tr></thead>
                <tbody>
                  @for (r of t.linhas; track $index) {
                    <tr>@for (c of t.colunas; track c.chave; let i = $index) { <td [class.num]="ehNumero(c.tipo)">{{ formatar(r[i], c.tipo) }}</td> }</tr>
                  } @empty { <tr><td [attr.colspan]="t.colunas.length" class="tc-muted empty">Nenhum registro no período.</td></tr> }
                </tbody>
                @if (t.totais) {
                  <tfoot><tr>@for (c of t.colunas; track c.chave; let i = $index) { <td [class.num]="ehNumero(c.tipo)">{{ formatar(t.totais[i], c.tipo) }}</td> }</tr></tfoot>
                }
              </table>
            </div>
            @for (n of t.observacoes ?? []; track n) { <p class="tc-muted small">{{ n }}</p> }
          </section>
        }
      }
    </div>
  `,
  styles: `
    .filters { padding-block: 16px 4px; }
    .kind { flex-basis: 320px; }
    .exports { display: flex; gap: 8px; flex-wrap: wrap; margin-left: auto; margin-bottom: 18px; }
    .run { margin-bottom: 18px; }
    .loading { margin-bottom: 12px; border-radius: 4px; }
    .report { min-width: 0; padding: 0; overflow: hidden; }
    .report-head { padding: 18px 20px 14px; border-bottom: 1px solid var(--tc-border); }
    .report-head h2 { margin: 0; }
    .report-head p { margin: 4px 0 0; }
    .table-scroll { overflow-x: auto; max-width: 100%; }
    .table-scroll:focus-visible { outline-offset: -2px; }
    table.lines th:first-child, table.lines td:first-child { padding-left: 20px; }
    table.lines th:last-child, table.lines td:last-child { padding-right: 20px; }
    .num { text-align: right !important; font-variant-numeric: tabular-nums; white-space: nowrap; }
    tfoot td { font-weight: 700; padding: 10px 12px; background: var(--tc-surface-muted); border-top: 2px solid var(--tc-border-strong); }
    .empty { text-align: center !important; padding-block: 24px !important; }
    .small { font: var(--mat-sys-body-small); margin: 0; padding: 10px 20px 0; }
    .small:last-child { padding-bottom: 16px; }
    @media (max-width: 600px) {
      .run, .exports { flex: 1 1 100%; margin-left: 0; }
      .exports > button { flex: 1 1 0; }
      .report-head { padding: 14px 16px 12px; }
    }
  `,
})
export class RelatoriosPage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly relatorios = RELATORIOS;
  protected readonly tipo = new FormControl('receitas-despesas', { nonNullable: true });
  protected readonly de = new FormControl(hojeIso().slice(0, 8) + '01', { nonNullable: true });
  protected readonly ate = new FormControl(hojeIso(), { nonNullable: true });
  protected readonly ano = new FormControl(new Date().getFullYear(), { nonNullable: true });
  protected readonly banco = new FormControl('', { nonNullable: true });
  protected readonly tabela = signal<RelatorioTabela | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeExportar = computed(() => this.contexto.pode('REPORT_EXPORT'));
  private readonly financeiro = inject(FinanceiroApi);
  protected readonly bancos = toSignal(toObservable(this.contexto.id).pipe(filter((c) => !!c),
    switchMap((c) => this.financeiro.contasBancarias(c!).pipe(catchError(() => of([]))))), { initialValue: [] });

  private parametros(): Record<string, string> {
    const k = this.tipo.value;
    if (k === 'orcamento') return { ano: String(this.ano.value) };
    const p: Record<string, string> = { de: this.de.value, ate: this.ate.value };
    if (k === 'movimentacao-bancaria' && this.banco.value) p['idContaBancaria'] = this.banco.value;
    return p;
  }

  executar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.api.relatorio(this.contexto.id()!, this.tipo.value, this.parametros()).pipe(map((t) => t)).subscribe({
      next: (t) => { this.carregando.set(false); this.tabela.set(t); },
      error: (err: unknown) => { this.carregando.set(false); this.tabela.set(null); this.erro.set(paraProblema(err).detail); },
    });
  }

  exportar(formatar: 'PDF' | 'XLSX' | 'CSV'): void {
    this.api.exportarRelatorio(this.contexto.id()!, this.tipo.value, { ...this.parametros(), formato: formatar })
      .subscribe({ error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  ehNumero(tipo: string): boolean {
    return tipo === 'MONEY' || tipo === 'NUMBER' || tipo === 'PERCENT';
  }

  formatar(v: unknown, tipo: string): string {
    if (v === null || v === undefined || v === '') return '';
    if (tipo === 'MONEY' && typeof v === 'number') return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    if (tipo === 'PERCENT' && typeof v === 'number') return v.toLocaleString('pt-BR', { maximumFractionDigits: 2 }) + '%';
    if (tipo === 'NUMBER' && typeof v === 'number') return v.toLocaleString('pt-BR');
    if (tipo === 'DATE' && typeof v === 'string') return v.slice(8, 10) + '/' + v.slice(5, 7) + '/' + v.slice(0, 4);
    return String(v);
  }
}
