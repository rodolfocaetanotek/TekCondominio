import { CurrencyPipe } from '@angular/common';
import { Component, computed, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

export interface MonthPoint { month: string; income: number; expense: number; }

const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/**
 * Receitas × despesas por mês (colunas agrupadas, um único eixo em R$).
 * Cores: slots 1 e 2 da paleta categórica validada (claro e escuro); legenda sempre visível,
 * dica ao passar o mouse/foco e alternativa em tabela para leitores de tela.
 */
@Component({
  selector: 'tc-monthly-chart',
  imports: [CurrencyPipe, MatButtonModule],
  template: `
    <div class="viz-root">
      <div class="top">
        <div class="legend" aria-hidden="true">
          <span><i class="sw income"></i>Receitas</span>
          <span><i class="sw expense"></i>Despesas</span>
        </div>
        <button mat-button (click)="showTable.set(!showTable())">{{ showTable() ? 'Ver gráfico' : 'Ver tabela' }}</button>
      </div>
      @if (showTable()) {
        <table class="data">
          <caption class="sr-only">Receitas e despesas por mês</caption>
          <thead><tr><th>Mês</th><th class="num">Receitas</th><th class="num">Despesas</th><th class="num">Resultado</th></tr></thead>
          <tbody>
            @for (p of points(); track p.month) {
              <tr><td>{{ label(p.month) }}</td><td class="num">{{ p.income | currency: 'BRL' }}</td><td class="num">{{ p.expense | currency: 'BRL' }}</td>
                <td class="num">{{ p.income - p.expense | currency: 'BRL' }}</td></tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="plot" (mouseleave)="hover.set(null)">
          <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="summary()" preserveAspectRatio="none">
            @for (t of ticks(); track t.value) {
              <line class="grid" [attr.x1]="PAD_L" [attr.x2]="W - 4" [attr.y1]="t.y" [attr.y2]="t.y" />
              <text class="axis" [attr.x]="PAD_L - 6" [attr.y]="t.y + 3" text-anchor="end">{{ t.label }}</text>
            }
            @for (b of bars(); track b.month; let i = $index) {
              <g (mouseenter)="hover.set(i)" (focus)="hover.set(i)" (blur)="hover.set(null)" tabindex="0"
                [attr.aria-label]="label(b.month) + ': receitas ' + money(b.income) + ', despesas ' + money(b.expense)">
                <rect class="hit" [attr.x]="b.x - 4" [attr.y]="PAD_T" [attr.width]="b.w * 2 + 10" [attr.height]="plotH" />
                <path class="bar income" [attr.d]="col(b.x, b.yIncome, b.w)" />
                <path class="bar expense" [attr.d]="col(b.x + b.w + 2, b.yExpense, b.w)" />
                <text class="axis" [attr.x]="b.x + b.w + 1" [attr.y]="H - 6" text-anchor="middle">{{ label(b.month).slice(0, 3) }}</text>
              </g>
            }
            <line class="base" [attr.x1]="PAD_L" [attr.x2]="W - 4" [attr.y1]="PAD_T + plotH" [attr.y2]="PAD_T + plotH" />
          </svg>
          @if (hover() !== null) {
            @let p = points()[hover()!];
            <div class="tip" [style.left.%]="tipLeft()">
              <strong>{{ label(p.month) }}</strong>
              <span><i class="sw income"></i>Receitas {{ p.income | currency: 'BRL' }}</span>
              <span><i class="sw expense"></i>Despesas {{ p.expense | currency: 'BRL' }}</span>
              <span class="muted">Resultado {{ p.income - p.expense | currency: 'BRL' }}</span>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .viz-root {
      --series-1: light-dark(#2a78d6, #3987e5);
      --series-2: light-dark(#eb6834, #d95926);
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
export class MonthlyChart {
  readonly points = input.required<MonthPoint[]>();
  protected readonly W = 640;
  protected readonly H = 240;
  protected readonly PAD_L = 56;
  protected readonly PAD_T = 12;
  protected readonly plotH = this.H - this.PAD_T - 24;
  protected readonly hover = signal<number | null>(null);
  protected readonly showTable = signal(false);

  private readonly max = computed(() => {
    const m = Math.max(1, ...this.points().flatMap((p) => [p.income, p.expense]));
    const mag = Math.pow(10, Math.floor(Math.log10(m)));
    return Math.ceil(m / mag) * mag;
  });

  protected readonly ticks = computed(() => [0, 0.25, 0.5, 0.75, 1].map((f) => {
    const value = this.max() * f;
    return { value, y: this.PAD_T + this.plotH * (1 - f), label: compact(value) };
  }));

  protected readonly bars = computed(() => {
    const n = this.points().length || 1;
    const slot = (this.W - this.PAD_L - 8) / n;
    const w = Math.max(3, Math.min(14, (slot - 10) / 2));
    return this.points().map((p, i) => ({
      ...p,
      x: this.PAD_L + i * slot + (slot - (w * 2 + 2)) / 2,
      w,
      yIncome: this.y(p.income),
      yExpense: this.y(p.expense),
    }));
  });

  protected readonly summary = computed(() => {
    const total = this.points().reduce((s, p) => ({ i: s.i + p.income, e: s.e + p.expense }), { i: 0, e: 0 });
    return `Receitas e despesas dos últimos ${this.points().length} meses: receitas ${this.money(total.i)}, despesas ${this.money(total.e)}.`;
  });

  protected tipLeft(): number {
    const i = this.hover() ?? 0;
    const b = this.bars()[i];
    return Math.min(85, Math.max(15, ((b.x + b.w) / this.W) * 100));
  }

  private y(v: number): number {
    return this.PAD_T + this.plotH * (1 - v / this.max());
  }

  /** Coluna com topo arredondado (4px) e base reta ancorada na linha de base. */
  protected col(x: number, y: number, w: number): string {
    const base = this.PAD_T + this.plotH;
    const h = base - y;
    if (h <= 0.5) return '';
    const r = Math.min(4, w / 2, h);
    return `M${x},${base} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${base} Z`;
  }

  protected label(month: string): string {
    const [y, m] = month.split('-');
    return `${MONTHS[Number(m) - 1]}/${y.slice(2)}`;
  }

  protected money(v: number): string {
    return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}

function compact(v: number): string {
  if (v >= 1_000_000) return (v / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mi';
  if (v >= 1_000) return (v / 1_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil';
  return v.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
}
