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
import { toProblem } from '../../core/api/api.models';
import { options } from '../../comum/rotulos';
import { FieldError } from '../../comum/formularios/erro-campo';
import { IsoDatePipe } from '../../comum/ui/data-iso.pipe';
import { StructureService } from '../estrutura/estrutura.service';
import { FinanceApi, RateioRequest, SimulacaoRateio } from '../financeiro/financeiro-api';
import { DialogClose } from '../../comum/ui/fechar-dialog';

/** Geração das cobranças da competência: simula o rateio por unidade e só grava após confirmação. */
@Component({
  selector: 'tc-allocation-dialog',
  imports: [DialogClose, CurrencyPipe, DecimalPipe, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, FieldError, IsoDatePipe],
  template: `
    <h2 mat-dialog-title>Gerar cobranças <tc-dialog-close /></h2>
    <mat-dialog-content>
      @if (error(); as message) { <p class="tc-alert" role="alert">{{ message }}</p> }
      <form [formGroup]="form" class="tc-grid">
        <mat-form-field class="span-4"><mat-label>Competência</mat-label><input matInput type="month" formControlName="competencia" required /></mat-form-field>
        <mat-form-field class="span-8"><mat-label>Descrição</mat-label><input matInput formControlName="descricao" required maxlength="200" />
          <mat-error><tc-field-error [control]="form.controls.descricao" /></mat-error></mat-form-field>
        <mat-form-field class="span-6"><mat-label>Conta de receita</mat-label>
          <mat-select formControlName="idContaPlano" required>
            @for (a of accounts(); track a.value) { <mat-option [value]="a.value">{{ a.label }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field class="span-6"><mat-label>Critério de rateio</mat-label>
          <mat-select formControlName="criterio">
            @for (m of methods; track m.value) { <mat-option [value]="m.value">{{ m.label }}</mat-option> }
          </mat-select></mat-form-field>
        @if (form.controls.criterio.value !== 'CONSUMPTION') {
          <mat-form-field class="span-6"><mat-label>{{ form.controls.criterio.value === 'FIXED' ? 'Valor por unidade (R$)' : 'Valor total a ratear (R$)' }}</mat-label>
            <input matInput type="number" step="0.01" min="0.01" formControlName="valor" /></mat-form-field>
        }
        <mat-form-field class="span-6"><mat-label>Vencimento</mat-label><input matInput type="date" formControlName="dataVencimento" />
          <mat-hint>Padrão: dia configurado do mês da competência</mat-hint></mat-form-field>
      </form>
      @if (form.controls.criterio.value === 'CONSUMPTION' && units().length) {
        <p class="tc-muted">Informe o consumo (R$) de cada unidade; deixe em branco para não cobrar.</p>
        <div class="consumption">
          @for (u of units(); track u.id) {
            <label>{{ u.rotulo }} <input type="number" class="consumption-input" step="0.01" min="0" [value]="consumption[u.id] ?? ''" (input)="setConsumption(u.id, $event)" /></label>
          }
        </div>
      }
      @if (preview(); as p) {
        <div class="preview">
          <p><strong>{{ p.unidades }} cobrança(s)</strong> totalizando <strong>{{ p.total | currency: 'BRL' }}</strong>, vencimento {{ p.dataVencimento | isoDate }}.
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
      @if (preview()) {
        <button mat-stroked-button (click)="preview.set(null)">Ajustar</button>
        <button mat-flat-button (click)="confirm()" [disabled]="busy()">Gerar {{ preview()!.unidades }} cobranças</button>
      } @else {
        <button mat-flat-button (click)="simulate()" [disabled]="busy()">Simular rateio</button>
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
export class AllocationDialog {
  private readonly data = inject<{ condominiumId: string }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<AllocationDialog, boolean>);
  private readonly api = inject(FinanceApi);
  protected readonly methods = options('allocationMethod');
  protected readonly preview = signal<SimulacaoRateio | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected consumption: Record<string, number> = {};

  protected readonly accounts = toSignal(this.api.chartAccounts().pipe(
    map((list) => list.filter((a) => a.analitica && a.ativo && a.natureza === 'INCOME').map((a) => ({ value: a.id, label: `${a.codigo} — ${a.nome}` }))),
    catchError(() => of([]))), { initialValue: [] });
  protected readonly units = toSignal(inject(StructureService).units(this.data.condominiumId, { page: 0, size: 100, active: 'true' })
    .pipe(map((p) => p.conteudo), catchError(() => of([]))), { initialValue: [] });

  private readonly month = new Date().toISOString().slice(0, 7);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    competencia: [this.month, Validators.required],
    descricao: ['Taxa condominial ' + this.month.slice(5) + '/' + this.month.slice(0, 4), [Validators.required, Validators.maxLength(200)]],
    idContaPlano: ['20000000-0000-0000-0000-000000000101', Validators.required],
    criterio: ['IDEAL_FRACTION'],
    valor: [null as number | null],
    dataVencimento: [''],
  });

  setConsumption(unitId: string, event: Event): void {
    const v = Number((event.target as HTMLInputElement).value);
    if (v > 0) this.consumption[unitId] = v;
    else delete this.consumption[unitId];
  }

  private request(): RateioRequest {
    const v = this.form.getRawValue();
    return { competencia: v.competencia, descricao: v.descricao.trim(), idContaPlano: v.idContaPlano, criterio: v.criterio,
      valor: v.criterio === 'CONSUMPTION' ? null : v.valor, dataVencimento: v.dataVencimento || null,
      consumo: v.criterio === 'CONSUMPTION' ? this.consumption : undefined };
  }

  simulate(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.run(true);
  }

  confirm(): void {
    this.run(false);
  }

  private run(previewOnly: boolean): void {
    this.busy.set(true);
    this.error.set(null);
    const c = this.data.condominiumId;
    const fail = (err: unknown) => { this.busy.set(false); this.error.set(toProblem(err).detail); };
    if (previewOnly) {
      this.api.previewAllocation(c, this.request()).subscribe({
        next: (r) => { this.busy.set(false); this.preview.set(r); },
        error: fail,
      });
    } else {
      this.api.generateAllocation(c, this.request()).subscribe({ next: () => this.ref.close(true), error: fail });
    }
  }
}
