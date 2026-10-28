import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { MaskKind } from '../format/br-format';
import { MaskDirective } from '../format/mask.directive';
import { FieldError } from '../forms/field-error';
import { applyServerErrors } from '../forms/server-errors';

export interface FieldDef {
  key: string;
  label: string;
  type?: 'text' | 'number' | 'date' | 'time' | 'datetime' | 'select' | 'textarea' | 'checkbox' | 'email';
  required?: boolean;
  options?: { value: string | null; label: string }[];
  value?: unknown;
  hint?: string;
  span?: 3 | 4 | 6 | 8 | 12;
  mask?: MaskKind;
  maxLength?: number;
  min?: number;
}

export interface FormDialogData<T> {
  title: string;
  fields: FieldDef[];
  submitLabel?: string;
  intro?: string;
  submit: (values: Record<string, unknown>) => Observable<T>;
}

/**
 * Formulário declarativo em diálogo. Os valores vazios são enviados como null; erros de validação da API
 * (problem+json) aparecem no campo correspondente. Fecha com o resultado da chamada.
 */
@Component({
  selector: 'tc-form-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MaskDirective, FieldError],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (data.intro) {
          <p class="tc-muted">{{ data.intro }}</p>
        }
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <div class="tc-grid">
          @for (f of data.fields; track f.key) {
            @switch (f.type ?? 'text') {
              @case ('checkbox') {
                <mat-checkbox [formControlName]="f.key" class="span-12">{{ f.label }}</mat-checkbox>
              }
              @case ('select') {
                <mat-form-field [class]="'span-' + (f.span ?? 12)">
                  <mat-label>{{ f.label }}</mat-label>
                  <mat-select [formControlName]="f.key" [required]="!!f.required">
                    @for (o of f.options ?? []; track o.value) {
                      <mat-option [value]="o.value">{{ o.label }}</mat-option>
                    }
                  </mat-select>
                  @if (f.hint) { <mat-hint>{{ f.hint }}</mat-hint> }
                  <mat-error><tc-field-error [control]="form.get(f.key)" /></mat-error>
                </mat-form-field>
              }
              @case ('textarea') {
                <mat-form-field [class]="'span-' + (f.span ?? 12)">
                  <mat-label>{{ f.label }}</mat-label>
                  <textarea matInput [formControlName]="f.key" rows="4" [required]="!!f.required" [attr.maxlength]="f.maxLength ?? null"></textarea>
                  @if (f.hint) { <mat-hint>{{ f.hint }}</mat-hint> }
                  <mat-error><tc-field-error [control]="form.get(f.key)" /></mat-error>
                </mat-form-field>
              }
              @default {
                <mat-form-field [class]="'span-' + (f.span ?? 12)">
                  <mat-label>{{ f.label }}</mat-label>
                  @if (f.mask) {
                    <input matInput [formControlName]="f.key" [tcMask]="f.mask" [required]="!!f.required" />
                  } @else {
                    <input matInput [formControlName]="f.key" [type]="inputType(f)" [required]="!!f.required"
                      [attr.step]="f.type === 'number' ? '0.01' : null" [attr.min]="f.min ?? null" [attr.maxlength]="f.maxLength ?? null" />
                  }
                  @if (f.hint) { <mat-hint>{{ f.hint }}</mat-hint> }
                  <mat-error><tc-field-error [control]="form.get(f.key)" /></mat-error>
                </mat-form-field>
              }
            }
          }
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : (data.submitLabel ?? 'Salvar') }}</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class FormDialog<T> {
  protected readonly data = inject<FormDialogData<T>>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<FormDialog<T>, T>);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = new FormGroup<Record<string, FormControl>>({});

  constructor() {
    for (const f of this.data.fields) {
      const validators: ValidatorFn[] = [];
      if (f.required && f.type !== 'checkbox') validators.push(Validators.required);
      if (f.type === 'email') validators.push(Validators.email);
      const initial = f.value ?? (f.type === 'checkbox' ? false : null);
      this.form.addControl(f.key, new FormControl(initial, validators));
    }
  }

  inputType(f: FieldDef): string {
    return f.type === 'datetime' ? 'datetime-local' : (f.type ?? 'text');
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const values: Record<string, unknown> = {};
    for (const f of this.data.fields) {
      let v = this.form.controls[f.key].value as unknown;
      if (typeof v === 'string') v = v.trim() === '' ? null : v.trim();
      if (f.type === 'datetime' && typeof v === 'string') v = new Date(v).toISOString();
      values[f.key] = v;
    }
    this.data.submit(values).subscribe({
      next: (result) => this.ref.close(result),
      error: (err: unknown) => {
        this.saving.set(false);
        const p = toProblem(err);
        const rest = p.code === 'VALIDATION_ERROR' ? applyServerErrors(this.form, p) : [p.detail];
        if (rest.length) this.error.set(rest.join(' '));
      },
    });
  }
}

/** Atalho: abre o formulário e devolve o resultado (ou undefined se cancelado). */
export function openForm<T>(dialog: MatDialog, data: FormDialogData<T>, width = '560px'): Observable<T | undefined> {
  return dialog.open<FormDialog<T>, FormDialogData<T>, T>(FormDialog, { data, width, maxWidth: '95vw' }).afterClosed();
}
