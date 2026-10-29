import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { CentroCusto, FinanceiroApi } from './financeiro-api';

@Component({
  selector: 'tc-parametros-tab',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule, MatIconModule, MensagemErroCampo],
  template: `
    <div class="cols">
      <section class="tc-card">
        <h3 class="tc-section-title">Cobrança e contas a pagar</h3>
        <form [formGroup]="form" (ngSubmit)="salvar()" class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Multa por atraso (%)</mat-label>
            <input matInput type="number" step="0.01" min="0" max="2" formControlName="percentualMulta" />
            <mat-hint>Máximo legal: 2% (CC, art. 1.336, §1º)</mat-hint>
            <mat-error><tc-erro-campo [controle]="form.controls.percentualMulta" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Juros ao mês (%)</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="percentualJurosMensal" />
            <mat-hint>Pró-rata dia; padrão 1% a.m.</mat-hint>
            <mat-error><tc-erro-campo [controle]="form.controls.percentualJurosMensal" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Dia de vencimento padrão</mat-label>
            <input matInput type="number" min="1" max="28" formControlName="diaVencimento" />
            <mat-error><tc-erro-campo [controle]="form.controls.diaVencimento" /></mat-error>
          </mat-form-field>
          <mat-checkbox formControlName="exigeAprovacaoContasPagar" class="span-6">Exigir aprovação antes de pagar despesas</mat-checkbox>
          <p class="tc-muted small span-12">Novas cobranças usam estes parâmetros; cobranças já emitidas mantêm os percentuais da emissão.</p>
          @if (podeEditarParametros()) {
            <div class="tc-actions span-12"><button mat-flat-button type="submit" [disabled]="form.pristine">Salvar parâmetros</button></div>
          }
        </form>
      </section>

      <section class="tc-card">
        <div class="row"><h3 class="tc-section-title">Centros de custo</h3>
          @if (podeEditar()) { <button mat-button (click)="editarCentro()"><mat-icon>add</mat-icon> Novo</button> }
        </div>
        @for (c of centros() ?? []; track c.id) {
          <div class="item" [class.off]="!c.ativo">
            <span>{{ c.nome }}</span>
            @if (podeEditar()) {
              <button mat-button (click)="editarCentro(c)">Editar</button>
              <button mat-button (click)="alternarCentro(c)">{{ c.ativo ? 'Inativar' : 'Reativar' }}</button>
            }
          </div>
        } @empty { <p class="tc-muted">Nenhum centro de custo (opcional).</p> }
      </section>

      <section class="tc-card wide">
        <div class="row"><h3 class="tc-section-title">Plano de contas</h3>
          @if (auth.pode('FINANCE_EDIT')) { <button mat-button (click)="novaConta()"><mat-icon>add</mat-icon> Conta própria</button> }
        </div>
        <div class="chart">
          @for (a of contas() ?? []; track a.id) {
            <div class="acc" [class.group]="!a.analitica" [class.off]="!a.ativo">
              <span class="code mono">{{ a.codigo }}</span><span>{{ a.nome }}</span>
              @if (!a.sistema) { <span class="tc-chip-soft">própria</span> }
            </div>
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    .cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr)); gap: 16px; align-items: start; }
    .wide { grid-column: 1 / -1; }
    .tc-section-title { margin-top: 0; }
    .row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
    .row h3 { margin: 0; }
    .item { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; padding: 6px 0; border-bottom: 1px solid var(--tc-border); }
    .item:last-child { border-bottom: 0; }
    .item span { flex: 1 1 140px; min-width: 0; }
    .off { opacity: .55; }
    .small { font: var(--mat-sys-body-small); margin: 0; }
    .chart { columns: 2 280px; column-gap: 32px; margin-top: 8px; }
    .acc { display: flex; gap: 8px; padding: 3px 0; break-inside: avoid; }
    .acc.group { margin-top: 12px; padding-bottom: 4px; border-bottom: 1px solid var(--tc-border); font-size: 11.5px; font-weight: 600;
      letter-spacing: 0.06em; text-transform: uppercase; color: var(--tc-text-muted); }
    .acc.group:first-child { margin-top: 0; }
    .code { flex: 0 0 44px; color: var(--tc-text-muted); font-variant-numeric: tabular-nums; }
  `,
})
export class ParametrosTab {
  protected readonly auth = inject(AuthService);
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.pode('FINANCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly podeEditarParametros = computed(() => this.podeEditar() && this.contexto.pode('CONDOMINIUM_EDIT'));

  protected readonly form = inject(FormBuilder).nonNullable.group({
    percentualMulta: [2, [Validators.required, Validators.min(0), Validators.max(2)]],
    percentualJurosMensal: [1, [Validators.required, Validators.min(0)]],
    diaVencimento: [10, [Validators.required, Validators.min(1), Validators.max(28)]],
    exigeAprovacaoContasPagar: [true],
  });

  protected readonly centros = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c]) => this.api.centrosCusto(c!).pipe(catchError(() => of([] as CentroCusto[])))),
    ),
  );
  protected readonly contas = toSignal(toObservable(this.recarga).pipe(switchMap(() => this.api.contasPlano().pipe(catchError(() => of([]))))));

  constructor() {
    toObservable(this.contexto.id).pipe(filter((c) => !!c), switchMap((c) => this.api.parametros(c!))).subscribe((s) => {
      this.form.reset(s);
      if (!this.podeEditarParametros()) this.form.disable();
    });
  }

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.api.salvarParametros(this.contexto.id()!, this.form.getRawValue()).subscribe({
      next: (s) => { this.form.reset(s); this.notificacao.sucesso('Parâmetros salvos.'); },
      error: (err: unknown) => {
        const p = paraProblema(err);
        const restante = p.codigo === 'VALIDATION_ERROR' ? aplicarErrosServidor(this.form, p) : [p.detail];
        if (restante.length) this.notificacao.erro(restante.join(' '));
      },
    });
  }

  editarCentro(c?: CentroCusto): void {
    abrirFormulario(this.dialog, {
      titulo: c ? 'Editar centro de custo' : 'Novo centro de custo',
      campos: [{ chave: 'nome', rotulo: 'Nome', obrigatorio: true, valor: c?.nome, tamanhoMaximo: 80 }],
      enviar: (v) => this.api.salvarCentroCusto(this.contexto.id()!, String(v['nome']), c?.id),
    }, '420px').pipe(filter((r) => !!r)).subscribe(() => this.recarga.update((t) => t + 1));
  }

  alternarCentro(c: CentroCusto): void {
    this.api.salvarCentroCusto(this.contexto.id()!, c.nome, c.id, !c.ativo).subscribe(() => this.recarga.update((t) => t + 1));
  }

  novaConta(): void {
    abrirFormulario(this.dialog, {
      titulo: 'Nova conta do plano de contas',
      introducao: 'Contas próprias complementam o plano padrão (ex.: 2.16 Portaria terceirizada).',
      campos: [
        { chave: 'codigo', rotulo: 'Código', obrigatorio: true, colunas: 4, tamanhoMaximo: 20 },
        { chave: 'nome', rotulo: 'Nome', obrigatorio: true, colunas: 8, tamanhoMaximo: 120 },
        { chave: 'natureza', rotulo: 'Natureza', tipo: 'select', obrigatorio: true, valor: 'EXPENSE',
          opcoes: [{ valor: 'INCOME', rotulo: 'Receita' }, { valor: 'EXPENSE', rotulo: 'Despesa' }] },
      ],
      enviar: (v) => this.api.cadastrarContaPlano({ codigo: String(v['codigo']), nome: String(v['nome']), natureza: String(v['natureza']) }),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Conta criada.'); this.recarga.update((t) => t + 1); });
  }
}
