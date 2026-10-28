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
import { options } from '../../shared/labels';
import { FieldError } from '../../shared/forms/field-error';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { StructureService } from '../structure/structure.service';
import { AllocationPreview, AllocationRequest, FinanceApi } from '../finance/finance-api';
import { DialogClose } from '../../shared/ui/dialog-close';

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
        <mat-form-field class="span-4"><mat-label>Competência</mat-label><input matInput type="month" formControlName="competence" required /></mat-form-field>
        <mat-form-field class="span-8"><mat-label>Descrição</mat-label><input matInput formControlName="description" required maxlength="200" />
          <mat-error><tc-field-error [control]="form.controls.description" /></mat-error></mat-form-field>
        <mat-form-field class="span-6"><mat-label>Conta de receita</mat-label>
          <mat-select formControlName="chartAccountId" required>
            @for (a of accounts(); track a.value) { <mat-option [value]="a.value">{{ a.label }}</mat-option> }
          </mat-select></mat-form-field>
        <mat-form-field class="span-6"><mat-label>Critério de rateio</mat-label>
          <mat-select formControlName="method">
            @for (m of methods; track m.value) { <mat-option [value]="m.value">{{ m.label }}</mat-option> }
          </mat-select></mat-form-field>
        @if (form.controls.method.value !== 'CONSUMPTION') {
          <mat-form-field class="span-6"><mat-label>{{ form.controls.method.value === 'FIXED' ? 'Valor por unidade (R$)' : 'Valor total a ratear (R$)' }}</mat-label>
            <input matInput type="number" step="0.01" min="0.01" formControlName="amount" /></mat-form-field>
        }
        <mat-form-field class="span-6"><mat-label>Vencimento</mat-label><input matInput type="date" formControlName="dueDate" />
          <mat-hint>Padrão: dia configurado do mês da competência</mat-hint></mat-form-field>
      </form>
      @if (form.controls.method.value === 'CONSUMPTION' && units().length) {
        <p class="tc-muted">Informe o consumo (R$) de cada unidade; deixe em branco para não cobrar.</p>
        <div class="consumption">
          @for (u of units(); track u.id) {
            <label>{{ u.label }} <input type="number" class="consumption-input" step="0.01" min="0" [value]="consumption[u.id] ?? ''" (input)="setConsumption(u.id, $event)" /></label>
          }
        </div>
      }
      @if (preview(); as p) {
        <div class="preview">
          <p><strong>{{ p.units }} cobrança(s)</strong> totalizando <strong>{{ p.total | currency: 'BRL' }}</strong>, vencimento {{ p.dueDate | isoDate }}.
            @if (p.unitsWithoutResponsible) { <span class="warn">{{ p.unitsWithoutResponsible }} unidade(s) sem responsável financeiro cadastrado.</span> }</p>
          <table class="lines">
            <thead><tr><th>Unidade</th><th>Fração</th><th>Responsável</th><th class="money">Valor</th></tr></thead>
            <tbody>
              @for (l of p.lines; track l.unitId) {
                <tr><td>{{ l.unitLabel }}</td><td>{{ l.idealFraction | number: '1.2-6' }}%</td><td>{{ l.responsibleName ?? '—' }}</td><td class="money">{{ l.amount | currency: 'BRL' }}</td></tr>
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
        <button mat-flat-button (click)="confirm()" [disabled]="busy()">Gerar {{ preview()!.units }} cobranças</button>
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
  protected readonly preview = signal<AllocationPreview | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected consumption: Record<string, number> = {};

  protected readonly accounts = toSignal(this.api.chartAccounts().pipe(
    map((list) => list.filter((a) => a.analytic && a.active && a.nature === 'INCOME').map((a) => ({ value: a.id, label: `${a.code} — ${a.name}` }))),
    catchError(() => of([]))), { initialValue: [] });
  protected readonly units = toSignal(inject(StructureService).units(this.data.condominiumId, { page: 0, size: 100, active: 'true' })
    .pipe(map((p) => p.conteudo), catchError(() => of([]))), { initialValue: [] });

  private readonly month = new Date().toISOString().slice(0, 7);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    competence: [this.month, Validators.required],
    description: ['Taxa condominial ' + this.month.slice(5) + '/' + this.month.slice(0, 4), [Validators.required, Validators.maxLength(200)]],
    chartAccountId: ['20000000-0000-0000-0000-000000000101', Validators.required],
    method: ['IDEAL_FRACTION'],
    amount: [null as number | null],
    dueDate: [''],
  });

  setConsumption(unitId: string, event: Event): void {
    const v = Number((event.target as HTMLInputElement).value);
    if (v > 0) this.consumption[unitId] = v;
    else delete this.consumption[unitId];
  }

  private request(): AllocationRequest {
    const v = this.form.getRawValue();
    return { competence: v.competence, description: v.description.trim(), chartAccountId: v.chartAccountId, method: v.method,
      amount: v.method === 'CONSUMPTION' ? null : v.amount, dueDate: v.dueDate || null,
      consumption: v.method === 'CONSUMPTION' ? this.consumption : undefined };
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
