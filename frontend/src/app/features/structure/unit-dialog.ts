import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { Building, Unit, UNIT_TYPE_LABELS, UnitType } from './structure.models';
import { StructureService } from './structure.service';
import { DialogClose } from '../../shared/ui/dialog-close';

export interface UnitDialogData {
  condominiumId: string;
  buildings: Building[];
  unit?: Unit;
}

@Component({
  selector: 'tc-unit-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, FieldError],
  template: `
    <h2 mat-dialog-title>{{ data.unit ? 'Editar unidade' : 'Nova unidade' }} <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Bloco/torre</mat-label>
            <mat-select formControlName="buildingId">
              <mat-option [value]="null">Sem bloco</mat-option>
              @for (b of activeBuildings; track b.id) {
                <mat-option [value]="b.id">{{ b.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Identificação</mat-label>
            <input matInput formControlName="identifier" required maxlength="30" placeholder="Ex.: 101, Casa 12, Loja 3" cdkFocusInitial />
            <mat-error><tc-field-error [control]="form.controls.identifier" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Tipo</mat-label>
            <mat-select formControlName="type" required>
              @for (t of types; track t[0]) {
                <mat-option [value]="t[0]">{{ t[1] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Andar</mat-label>
            <input matInput formControlName="floor" maxlength="10" />
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Fração ideal (%)</mat-label>
            <input matInput type="number" step="0.000001" min="0" max="100" formControlName="idealFraction" />
            <mat-hint>Usada no rateio por fração ideal</mat-hint>
            <mat-error><tc-field-error [control]="form.controls.idealFraction" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Área privativa (m²)</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="area" />
            <mat-error><tc-field-error [control]="form.controls.area" /></mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Observações</mat-label>
            <textarea matInput formControlName="notes" rows="2" maxlength="500"></textarea>
          </mat-form-field>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class UnitDialog {
  protected readonly data = inject<UnitDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<UnitDialog, Unit>);
  private readonly service = inject(StructureService);

  protected readonly types = Object.entries(UNIT_TYPE_LABELS) as [UnitType, string][];
  protected readonly activeBuildings = this.data.buildings.filter((b) => b.active || b.id === this.data.unit?.buildingId);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    buildingId: [this.data.unit?.buildingId ?? (null as string | null)],
    identifier: [this.data.unit?.identifier ?? '', [Validators.required, Validators.maxLength(30)]],
    type: [this.data.unit?.type ?? ('APARTMENT' as UnitType), Validators.required],
    floor: [this.data.unit?.floor ?? ''],
    idealFraction: [this.data.unit?.idealFraction ?? (null as number | null)],
    area: [this.data.unit?.area ?? (null as number | null)],
    notes: [this.data.unit?.notes ?? ''],
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service
      .saveUnit(
        this.data.condominiumId,
        {
          buildingId: v.buildingId ?? null,
          identifier: (v.identifier ?? '').trim(),
          type: v.type ?? 'APARTMENT',
          floor: v.floor?.trim() || null,
          idealFraction: v.idealFraction ?? null,
          area: v.area ?? null,
          notes: v.notes?.trim() || null,
          version: this.data.unit?.version,
        },
        this.data.unit?.id,
      )
      .subscribe({
        next: (u) => this.ref.close(u),
        error: (err: unknown) => {
          this.saving.set(false);
          const p = toProblem(err);
          const rest = p.code === 'VALIDATION_ERROR' ? applyServerErrors(this.form, p) : [p.detail];
          if (rest.length) this.error.set(rest.join(' '));
        },
      });
  }
}
