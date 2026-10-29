import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, of } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { opcoes } from '../../comum/rotulos';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { EstruturaService } from '../estrutura/estrutura.service';
import { FinanceiroApi, RateioRequest, SimulacaoRateio } from '../financeiro/financeiro-api';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

/** Geração das cobranças da competência: simula o rateio por unidade e só grava após confirmação. */
@Component({
  selector: 'tc-rateio-dialog',
  imports: [FecharDialog, CurrencyPipe, DecimalPipe, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MensagemErroCampo, DataIsoPipe],
  template: `
    <h2 mat-dialog-title>Gerar cobranças <tc-fechar-dialog /></h2>
    <mat-dialog-content>
      @if (erro(); as mensagem) { <p class="tc-alert" role="alert">{{ mensagem }}</p> }
      <form [formGroup]="form" class="tc-grid">
        <mat-form-field class="span-4"><mat-label>Competência</mat-label><input matInput type="month" formControlName="competencia" required /></mat-form-field>
        <mat-form-field class="span-8"><mat-label>Descrição</mat-label><input matInput formControlName="descricao" required maxlength="200" />
          <mat-error><tc-erro-campo [controle]="form.controls.descricao" /></mat-error></mat-form-field>
        <mat-form-field class="span-6"><mat-label>Conta de receita</mat-label>
          <mat-select formControlName="idContaPlano" required>
            @for (a of contas(); track a.valor) { <mat-option [value]="a.valor">{{ a.rotulo }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field class="span-6"><mat-label>Critério de rateio</mat-label>
          <mat-select formControlName="criterio">
            @for (m of criterios; track m.valor) { <mat-option [value]="m.valor">{{ m.rotulo }}</mat-option> }
          </mat-select></mat-form-field>
        @if (form.controls.criterio.value !== 'CONSUMPTION') {
          <mat-form-field class="span-6"><mat-label>{{ form.controls.criterio.value === 'FIXED' ? 'Valor por unidade (R$)' : 'Valor total a ratear (R$)' }}</mat-label>
            <input matInput type="number" step="0.01" min="0.01" formControlName="valor" /></mat-form-field>
        }
        <mat-form-field class="span-6"><mat-label>Vencimento</mat-label><input matInput type="date" formControlName="dataVencimento" />
          <mat-hint>Padrão: dia configurado do mês da competência</mat-hint></mat-form-field>
      </form>
      @if (form.controls.criterio.value === 'CONSUMPTION' && unidades().length) {
        <p class="tc-muted">Informe o consumo (R$) de cada unidade; deixe em branco para não cobrar.</p>
        <div class="consumption">
          @for (u of unidades(); track u.id) {
            <label>{{ u.rotulo }} <input type="number" class="consumption-input" step="0.01" min="0" [value]="consumo[u.id] ?? ''" (input)="definirConsumo(u.id, $event)" /></label>
          }
        </div>
      }
      @if (simulacao(); as p) {
        <div class="preview">
          <p><strong>{{ p.unidades }} cobrança(s)</strong> totalizando <strong>{{ p.total | currency: 'BRL' }}</strong>, vencimento {{ p.dataVencimento | dataIso }}.
            @if (p.unidadesSemResponsavel) { <span class="warn">{{ p.unidadesSemResponsavel }} unidade(s) sem responsável financeiro cadastrado.</span> }</p>
          <table class="lines">
            <thead><tr><th>Unidade</th><th>Fração</th><th>Responsável</th><th class="money">Valor</th></tr></thead>
            <tbody>
              @for (l of p.linhas; track l.idUnidade) {
                <tr><td>{{ l.rotuloUnidade }}</td><td>{{ l.fracaoIdeal | number: '1.2-6' }}%</td><td>{{ l.nomeResponsavel ?? '—' }}</td><td class="money">{{ l.valor | currency: 'BRL' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      @if (simulacao()) {
        <button mat-stroked-button (click)="simulacao.set(null)">Ajustar</button>
        <button mat-flat-button (click)="confirmar()" [disabled]="ocupado()">Gerar {{ simulacao()!.unidades }} cobranças</button>
      } @else {
        <button mat-flat-button (click)="simular()" [disabled]="ocupado()">Simular rateio</button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .preview { margin-top: 8px; max-height: 320px; overflow: auto; border: 1px solid var(--tc-border); border-radius: var(--tc-radius-sm); }
    .preview > p { margin: 0; padding: 12px; background: var(--tc-surface-muted); border-bottom: 1px solid var(--tc-border); }
    .preview thead th { position: sticky; top: 0; z-index: 1; }
    .warn { color: var(--tc-danger); display: block; margin-top: 4px; }
    .consumption { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 8px; max-height: 220px; overflow: auto; }
    .consumption label { display: flex; align-items: center; justify-content: space-between; gap: 6px; min-width: 0; }
    .consumption-input { width: 90px; padding: 6px 8px; font: inherit; color: var(--tc-text); background: var(--tc-field-bg);
      border: 1px solid var(--tc-border-strong); border-radius: var(--tc-radius-sm); }
    .consumption-input:focus { outline: 2px solid var(--mat-sys-primary); outline-offset: 0; border-color: transparent; }
  `,
})
export class RateioDialog {
  private readonly data = inject<{ idCondominio: string }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RateioDialog, boolean>);
  private readonly api = inject(FinanceiroApi);
  protected readonly criterios = opcoes('allocationMethod');
  protected readonly simulacao = signal<SimulacaoRateio | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected consumo: Record<string, number> = {};

  protected readonly contas = toSignal(this.api.contasPlano().pipe(
    map((listar) => listar.filter((a) => a.analitica && a.ativo && a.natureza === 'INCOME').map((a) => ({ valor: a.id, rotulo: `${a.codigo} — ${a.nome}` }))),
    catchError(() => of([]))), { initialValue: [] });
  protected readonly unidades = toSignal(inject(EstruturaService).unidades(this.data.idCondominio, { pagina: 0, tamanho: 100, ativo: 'true' })
    .pipe(map((p) => p.conteudo), catchError(() => of([]))), { initialValue: [] });

  private readonly mes = new Date().toISOString().slice(0, 7);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    competencia: [this.mes, Validators.required],
    descricao: ['Taxa condominial ' + this.mes.slice(5) + '/' + this.mes.slice(0, 4), [Validators.required, Validators.maxLength(200)]],
    idContaPlano: ['20000000-0000-0000-0000-000000000101', Validators.required],
    criterio: ['IDEAL_FRACTION'],
    valor: [null as number | null],
    dataVencimento: [''],
  });

  definirConsumo(idUnidade: string, evento: Event): void {
    const v = Number((evento.target as HTMLInputElement).value);
    if (v > 0) this.consumo[idUnidade] = v;
    else delete this.consumo[idUnidade];
  }

  private solicitacao(): RateioRequest {
    const v = this.form.getRawValue();
    return { competencia: v.competencia, descricao: v.descricao.trim(), idContaPlano: v.idContaPlano, criterio: v.criterio,
      valor: v.criterio === 'CONSUMPTION' ? null : v.valor, dataVencimento: v.dataVencimento || null,
      consumo: v.criterio === 'CONSUMPTION' ? this.consumo : undefined };
  }

  simular(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.executar(true);
  }

  confirmar(): void {
    this.executar(false);
  }

  private executar(apenasSimulacao: boolean): void {
    this.ocupado.set(true);
    this.erro.set(null);
    const c = this.data.idCondominio;
    const falhar = (err: unknown) => { this.ocupado.set(false); this.erro.set(paraProblema(err).detail); };
    if (apenasSimulacao) {
      this.api.simularRateio(c, this.solicitacao()).subscribe({
        next: (r) => { this.ocupado.set(false); this.simulacao.set(r); },
        error: falhar,
      });
    } else {
      this.api.gerarRateio(c, this.solicitacao()).subscribe({ next: () => this.ref.close(true), error: falhar });
    }
  }
}
