import { CurrencyPipe } from '@angular/common';
import { Component, computed, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

export interface PontoMensal { mes: string; receitas: number; despesas: number; }

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/**
 * Receitas × despesas por mês (colunas agrupadas, um único eixo em R$).
 * Cores: slots 1 e 2 da paleta categórica validada (claro e escuro); legenda sempre visível,
 * dica ao passar o mouse/foco e alternativa em tabela para leitores de tela.
 */
@Component({
  selector: 'tc-grafico-mensal',
  imports: [CurrencyPipe, MatButtonModule],
  template: `
    <div class="viz-root">
      <div class="top">
        <div class="legend" aria-hidden="true">
          <span><i class="sw income"></i>Receitas</span>
          <span><i class="sw expense"></i>Despesas</span>
        </div>
        <button mat-button (click)="mostrarTabela.set(!mostrarTabela())">{{ mostrarTabela() ? 'Ver gráfico' : 'Ver tabela' }}</button>
      </div>
      @if (mostrarTabela()) {
        <table class="data">
          <caption class="sr-only">Receitas e despesas por mês</caption>
          <thead><tr><th>Mês</th><th class="num">Receitas</th><th class="num">Despesas</th><th class="num">Resultado</th></tr></thead>
          <tbody>
            @for (p of pontos(); track p.mes) {
              <tr><td>{{ rotulo(p.mes) }}</td><td class="num">{{ p.receitas | currency: 'BRL' }}</td><td class="num">{{ p.despesas | currency: 'BRL' }}</td>
                <td class="num">{{ p.receitas - p.despesas | currency: 'BRL' }}</td></tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="plot" (mouseleave)="destaque.set(null)">
          <svg [attr.viewBox]="'0 0 ' + LARGURA + ' ' + ALTURA" role="img" [attr.aria-label]="resumo()" preserveAspectRatio="none">
            @for (t of marcas(); track t.valor) {
              <line class="grid" [attr.x1]="MARGEM_ESQUERDA" [attr.x2]="LARGURA - 4" [attr.y1]="t.y" [attr.y2]="t.y" />
              <text class="axis" [attr.x]="MARGEM_ESQUERDA - 6" [attr.y]="t.y + 3" text-anchor="end">{{ t.rotulo }}</text>
            }
            @for (b of barras(); track b.mes; let i = $index) {
              <g (mouseenter)="destaque.set(i)" (focus)="destaque.set(i)" (blur)="destaque.set(null)" tabindex="0"
                [attr.aria-label]="rotulo(b.mes) + ': receitas ' + moeda(b.receitas) + ', despesas ' + moeda(b.despesas)">
                <rect class="hit" [attr.x]="b.x - 4" [attr.y]="MARGEM_SUPERIOR" [attr.width]="b.largura * 2 + 10" [attr.height]="alturaGrafico" />
                <path class="bar income" [attr.d]="coluna(b.x, b.yIncome, b.largura)" />
                <path class="bar expense" [attr.d]="coluna(b.x + b.largura + 2, b.yExpense, b.largura)" />
                <text class="axis" [attr.x]="b.x + b.largura + 1" [attr.y]="ALTURA - 6" text-anchor="middle">{{ rotulo(b.mes).slice(0, 3) }}</text>
              </g>
            }
            <line class="base" [attr.x1]="MARGEM_ESQUERDA" [attr.x2]="LARGURA - 4" [attr.y1]="MARGEM_SUPERIOR + alturaGrafico" [attr.y2]="MARGEM_SUPERIOR + alturaGrafico" />
          </svg>
          @if (destaque() !== null) {
            @let p = pontos()[destaque()!];
            <div class="tip" [style.left.%]="dicaEsquerda()">
              <strong>{{ rotulo(p.mes) }}</strong>
              <span><i class="sw income"></i>Receitas {{ p.receitas | currency: 'BRL' }}</span>
              <span><i class="sw expense"></i>Despesas {{ p.despesas | currency: 'BRL' }}</span>
              <span class="muted">Resultado {{ p.receitas - p.despesas | currency: 'BRL' }}</span>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .viz-root {
      --series-1: light-dark(#0b7f7a, #4cc3b8);
      --series-2: light-dark(#ee7a0c, #f59a45);
      --ink-muted: var(--mat-sys-on-surface-variant);
      --grid: var(--mat-sys-outline-variant);
    }
    .top { display: flex; justify-content: space-between; align-items: center; }
    .legend { display: flex; gap: 16px; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface); }
    .legend span { display: inline-flex; align-items: center; gap: 6px; }
    .sw { display: inline-block; width: 10px; height: 10px; border-radius: 2px; }
    .sw.income { background: var(--series-1); } .sw.expense { background: var(--series-2); }
    .plot { position: relative; }
    svg { width: 100%; height: 240px; display: block; }
    .grid { stroke: var(--grid); stroke-width: 1; stroke-dasharray: 2 3; }
    .base { stroke: var(--mat-sys-outline); stroke-width: 1; }
    .axis { fill: var(--ink-muted); font-size: 10px; font-family: inherit; }
    .bar.income { fill: var(--series-1); } .bar.expense { fill: var(--series-2); }
    g:focus { outline: none; } g:focus .hit, g:hover .hit { fill: var(--mat-sys-surface-container-high); }
    .hit { fill: transparent; }
    .tip { position: absolute; top: 4px; transform: translateX(-50%); background: var(--mat-sys-inverse-surface); color: var(--mat-sys-inverse-on-surface);
      padding: 8px 10px; border-radius: 8px; font: var(--mat-sys-body-small); display: grid; gap: 2px; pointer-events: none; white-space: nowrap; z-index: 1; }
    .tip span { display: inline-flex; align-items: center; gap: 6px; } .tip .muted { opacity: .8; }
    .data { width: 100%; border-collapse: collapse; font: var(--mat-sys-body-small); }
    .data th, .data td { padding: 4px 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); text-align: left; }
    .num { text-align: right !important; font-variant-numeric: tabular-nums; }
  `,
})
export class GraficoMensal {
  readonly pontos = input.required<PontoMensal[]>();
  protected readonly LARGURA = 640;
  protected readonly ALTURA = 240;
  protected readonly MARGEM_ESQUERDA = 56;
  protected readonly MARGEM_SUPERIOR = 12;
  protected readonly alturaGrafico = this.ALTURA - this.MARGEM_SUPERIOR - 24;
  protected readonly destaque = signal<number | null>(null);
  protected readonly mostrarTabela = signal(false);

  private readonly max = computed(() => {
    const m = Math.max(1, ...this.pontos().flatMap((p) => [p.receitas, p.despesas]));
    const magnitude = Math.pow(10, Math.floor(Math.log10(m)));
    return Math.ceil(m / magnitude) * magnitude;
  });

  protected readonly marcas = computed(() => [0, 0.25, 0.5, 0.75, 1].map((f) => {
    const valor = this.max() * f;
    return { valor, y: this.MARGEM_SUPERIOR + this.alturaGrafico * (1 - f), rotulo: compactar(valor) };
  }));

  protected readonly barras = computed(() => {
    const n = this.pontos().length || 1;
    const horario = (this.LARGURA - this.MARGEM_ESQUERDA - 8) / n;
    const largura = Math.max(3, Math.min(14, (horario - 10) / 2));
    return this.pontos().map((p, i) => ({
      ...p,
      x: this.MARGEM_ESQUERDA + i * horario + (horario - (largura * 2 + 2)) / 2,
      largura,
      yIncome: this.y(p.receitas),
      yExpense: this.y(p.despesas),
    }));
  });

  protected readonly resumo = computed(() => {
    const total = this.pontos().reduce((s, p) => ({ i: s.i + p.receitas, e: s.e + p.despesas }), { i: 0, e: 0 });
    return `Receitas e despesas dos últimos ${this.pontos().length} meses: receitas ${this.moeda(total.i)}, despesas ${this.moeda(total.e)}.`;
  });

  protected dicaEsquerda(): number {
    const i = this.destaque() ?? 0;
    const b = this.barras()[i];
    return Math.min(85, Math.max(15, ((b.x + b.largura) / this.LARGURA) * 100));
  }

  private y(v: number): number {
    return this.MARGEM_SUPERIOR + this.alturaGrafico * (1 - v / this.max());
  }

  /** Coluna com topo arredondado (4px) e base reta ancorada na linha de base. */
  protected coluna(x: number, y: number, largura: number): string {
    const base = this.MARGEM_SUPERIOR + this.alturaGrafico;
    const h = base - y;
    if (h <= 0.5) return '';
    const r = Math.min(4, largura / 2, h);
    return `M${x},${base} V${y + r} Q${x},${y} ${x + r},${y} H${x + largura - r} Q${x + largura},${y} ${x + largura},${y + r} V${base} Z`;
  }

  protected rotulo(mes: string): string {
    const [y, m] = mes.split('-');
    return `${MESES[Number(m) - 1]}/${y.slice(2)}`;
  }

  protected moeda(v: number): string {
    return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}

function compactar(v: number): string {
  if (v >= 1_000_000) return (v / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mi';
  if (v >= 1_000) return (v / 1_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil';
  return v.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
}
