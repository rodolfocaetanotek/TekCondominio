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
import { toProblem } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { openForm } from '../../shared/ui/form-dialog';
import { CostCenter, FinanceApi } from './finance-api';

@Component({
  selector: 'tc-settings-tab',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule, MatIconModule, FieldError],
  template: `
    <div class="cols">
      <section class="tc-card">
        <h3 class="tc-section-title">Cobrança e contas a pagar</h3>
        <form [formGroup]="form" (ngSubmit)="save()" class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Multa por atraso (%)</mat-label>
            <input matInput type="number" step="0.01" min="0" max="2" formControlName="finePercent" />
            <mat-hint>Máximo legal: 2% (CC, art. 1.336, §1º)</mat-hint>
            <mat-error><tc-field-error [control]="form.controls.finePercent" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Juros ao mês (%)</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="interestMonthlyPercent" />
            <mat-hint>Pró-rata dia; padrão 1% a.m.</mat-hint>
            <mat-error><tc-field-error [control]="form.controls.interestMonthlyPercent" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Dia de vencimento padrão</mat-label>
            <input matInput type="number" min="1" max="28" formControlName="billingDueDay" />
            <mat-error><tc-field-error [control]="form.controls.billingDueDay" /></mat-error>
          </mat-form-field>
          <mat-checkbox formControlName="requirePayableApproval" class="span-6">Exigir aprovação antes de pagar despesas</mat-checkbox>
          <p class="tc-muted small span-12">Novas cobranças usam estes parâmetros; cobranças já emitidas mantêm os percentuais da emissão.</p>
          @if (canEditSettings()) {
            <div class="tc-actions span-12"><button mat-flat-button type="submit" [disabled]="form.pristine">Salvar parâmetros</button></div>
          }
        </form>
      </section>

      <section class="tc-card">
        <div class="row"><h3 class="tc-section-title">Centros de custo</h3>
          @if (canEdit()) { <button mat-button (click)="editCenter()"><mat-icon>add</mat-icon> Novo</button> }
        </div>
        @for (c of centers() ?? []; track c.id) {
          <div class="item" [class.off]="!c.active">
            <span>{{ c.name }}</span>
            @if (canEdit()) {
              <button mat-button (click)="editCenter(c)">Editar</button>
              <button mat-button (click)="toggleCenter(c)">{{ c.active ? 'Inativar' : 'Reativar' }}</button>
            }
          </div>
        } @empty { <p class="tc-muted">Nenhum centro de custo (opcional).</p> }
      </section>

      <section class="tc-card wide">
        <div class="row"><h3 class="tc-section-title">Plano de contas</h3>
          @if (auth.can('FINANCE_EDIT')) { <button mat-button (click)="newAccount()"><mat-icon>add</mat-icon> Conta própria</button> }
        </div>
        <div class="chart">
          @for (a of accounts() ?? []; track a.id) {
            <div class="acc" [class.group]="!a.analytic" [class.off]="!a.active">
              <span class="code mono">{{ a.code }}</span><span>{{ a.name }}</span>
              @if (!a.system) { <span class="tc-chip-soft">própria</span> }
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
export class SettingsTab {
  protected readonly auth = inject(AuthService);
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('FINANCE_EDIT') && this.context.selected()?.status === 'ACTIVE');
  protected readonly canEditSettings = computed(() => this.canEdit() && this.context.can('CONDOMINIUM_EDIT'));

  protected readonly form = inject(FormBuilder).nonNullable.group({
    finePercent: [2, [Validators.required, Validators.min(0), Validators.max(2)]],
    interestMonthlyPercent: [1, [Validators.required, Validators.min(0)]],
    billingDueDay: [10, [Validators.required, Validators.min(1), Validators.max(28)]],
    requirePayableApproval: [true],
  });

  protected readonly centers = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c]) => this.api.costCenters(c!).pipe(catchError(() => of([] as CostCenter[])))),
    ),
  );
  protected readonly accounts = toSignal(toObservable(this.tick).pipe(switchMap(() => this.api.chartAccounts().pipe(catchError(() => of([]))))));

  constructor() {
    toObservable(this.context.id).pipe(filter((c) => !!c), switchMap((c) => this.api.settings(c!))).subscribe((s) => {
      this.form.reset(s);
      if (!this.canEditSettings()) this.form.disable();
    });
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.api.saveSettings(this.context.id()!, this.form.getRawValue()).subscribe({
      next: (s) => { this.form.reset(s); this.notify.success('Parâmetros salvos.'); },
      error: (err: unknown) => {
        const p = toProblem(err);
        const rest = p.code === 'VALIDATION_ERROR' ? applyServerErrors(this.form, p) : [p.detail];
        if (rest.length) this.notify.error(rest.join(' '));
      },
    });
  }

  editCenter(c?: CostCenter): void {
    openForm(this.dialog, {
      title: c ? 'Editar centro de custo' : 'Novo centro de custo',
      fields: [{ key: 'name', label: 'Nome', required: true, value: c?.name, maxLength: 80 }],
      submit: (v) => this.api.saveCostCenter(this.context.id()!, String(v['name']), c?.id),
    }, '420px').pipe(filter((r) => !!r)).subscribe(() => this.tick.update((t) => t + 1));
  }

  toggleCenter(c: CostCenter): void {
    this.api.saveCostCenter(this.context.id()!, c.name, c.id, !c.active).subscribe(() => this.tick.update((t) => t + 1));
  }

  newAccount(): void {
    openForm(this.dialog, {
      title: 'Nova conta do plano de contas',
      intro: 'Contas próprias complementam o plano padrão (ex.: 2.16 Portaria terceirizada).',
      fields: [
        { key: 'code', label: 'Código', required: true, span: 4, maxLength: 20 },
        { key: 'name', label: 'Nome', required: true, span: 8, maxLength: 120 },
        { key: 'nature', label: 'Natureza', type: 'select', required: true, value: 'EXPENSE',
          options: [{ value: 'INCOME', label: 'Receita' }, { value: 'EXPENSE', label: 'Despesa' }] },
      ],
      submit: (v) => this.api.createChartAccount({ code: String(v['code']), name: String(v['name']), nature: String(v['nature']) }),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Conta criada.'); this.tick.update((t) => t + 1); });
  }
}
