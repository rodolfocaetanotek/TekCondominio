import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../shared/forms/field-error';
import { todayIso } from '../../shared/ui/dates';
import { PersonPicker } from './person-picker';
import { Occupancy, OCCUPANCY_ROLE_LABELS, OccupancyRole, PersonSummary } from './structure.models';
import { StructureService } from './structure.service';
import { DialogClose } from '../../shared/ui/dialog-close';

/** Vincular pessoa a uma unidade. */
@Component({
  selector: 'tc-occupancy-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, FieldError, PersonPicker],
  template: `
    <h2 mat-dialog-title>Vincular pessoa · {{ data.unitLabel }} <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <tc-person-picker label="Pessoa" (picked)="person.set($event)" />
        @if (!person()) {
          <p class="tc-muted small">Não encontrou? Cadastre a pessoa no menu <strong>Pessoas</strong> e volte aqui.</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Vínculo</mat-label>
            <mat-select formControlName="role">
              @for (r of roles; track r[0]) {
                <mat-option [value]="r[0]">{{ r[1] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Início</mat-label>
            <input matInput type="date" formControlName="startDate" required />
            <mat-error><tc-field-error [control]="form.controls.startDate" /></mat-error>
          </mat-form-field>
        </div>
        <mat-checkbox formControlName="financialResponsible">Responsável financeiro (recebe as cobranças da unidade)</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving() || !person()">Vincular</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.small { font: var(--mat-sys-body-small); margin-top: -8px; }`,
})
export class OccupancyDialog {
  protected readonly data = inject<{ condominiumId: string; unitId: string; unitLabel: string; hasFinancial: boolean }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<OccupancyDialog, Occupancy>);
  private readonly service = inject(StructureService);
  protected readonly roles = Object.entries(OCCUPANCY_ROLE_LABELS) as [OccupancyRole, string][];
  protected readonly person = signal<PersonSummary | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    role: ['OWNER' as OccupancyRole],
    startDate: [todayIso(), Validators.required],
    financialResponsible: [!this.data.hasFinancial],
  });

  save(): void {
    const person = this.person();
    this.form.markAllAsTouched();
    if (!person || this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service
      .addOccupancy(this.data.condominiumId, { unitId: this.data.unitId, personId: person.id, ...v })
      .subscribe({
        next: (o) => this.ref.close(o),
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(toProblem(err).detail);
        },
      });
  }
}

/** Encerrar vínculo (o histórico é mantido). */
@Component({
  selector: 'tc-end-occupancy-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FieldError],
  template: `
    <h2 mat-dialog-title>Encerrar vínculo <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <p>{{ data.occupancy.personName }} — {{ data.occupancy.unitLabel }}</p>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <mat-form-field class="full">
          <mat-label>Data de encerramento</mat-label>
          <input matInput type="date" formControlName="endDate" required />
          <mat-error><tc-field-error [control]="form.controls.endDate" /></mat-error>
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Motivo</mat-label>
          <input matInput formControlName="reason" maxlength="255" placeholder="Ex.: venda do imóvel, fim do contrato de locação" />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">Encerrar</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.full { width: 100%; }`,
})
export class EndOccupancyDialog {
  protected readonly data = inject<{ condominiumId: string; occupancy: Occupancy }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<EndOccupancyDialog, Occupancy>);
  private readonly service = inject(StructureService);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    endDate: [todayIso(), Validators.required],
    reason: [''],
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.service.endOccupancy(this.data.condominiumId, this.data.occupancy.id, v.endDate, v.reason.trim() || null).subscribe({
      next: (o) => this.ref.close(o),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(toProblem(err).detail);
      },
    });
  }
}
