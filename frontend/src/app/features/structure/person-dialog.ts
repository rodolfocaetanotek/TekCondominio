import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { toProblem } from '../../core/api/api.models';
import { formatCpfCnpj, formatPhone } from '../../shared/format/br-format';
import { MaskDirective } from '../../shared/format/mask.directive';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { PersonDetail, PersonKind } from './structure.models';
import { StructureService } from './structure.service';

@Component({
  selector: 'tc-person-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MaskDirective, FieldError],
  template: `
    <h2 mat-dialog-title>{{ data.person ? 'Editar pessoa' : 'Nova pessoa' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-8">
            <mat-label>Nome / razão social</mat-label>
            <input matInput formControlName="name" required maxlength="150" cdkFocusInitial />
            <mat-error><tc-field-error [control]="form.controls.name" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-4">
            <mat-label>Tipo</mat-label>
            <mat-select formControlName="kind">
              <mat-option value="INDIVIDUAL">Pessoa física</mat-option>
              <mat-option value="COMPANY">Pessoa jurídica</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>{{ form.controls.kind.value === 'COMPANY' ? 'CNPJ' : 'CPF' }}</mat-label>
            <input matInput formControlName="document" tcMask="cpfCnpj" class="mono" />
            <mat-error><tc-field-error [control]="form.controls.document" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" formControlName="email" />
            <mat-hint>Necessário para acesso ao portal e avisos</mat-hint>
            <mat-error><tc-field-error [control]="form.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Telefone</mat-label>
            <input matInput type="tel" formControlName="phone" tcMask="phone" />
            <mat-error><tc-field-error [control]="form.controls.phone" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Telefone alternativo</mat-label>
            <input matInput type="tel" formControlName="secondaryPhone" tcMask="phone" />
            <mat-error><tc-field-error [control]="form.controls.secondaryPhone" /></mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Observações</mat-label>
            <textarea matInput formControlName="notes" rows="2" maxlength="500"></textarea>
          </mat-form-field>
        </div>
        <p class="tc-muted small">Colete somente os dados necessários à administração do condomínio (LGPD).</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.small { font: var(--mat-sys-body-small); }`,
})
export class PersonDialog {
  protected readonly data = inject<{ person?: PersonDetail }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PersonDialog, PersonDetail>);
  private readonly service = inject(StructureService);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly p = this.data.person;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.p?.name ?? '', [Validators.required, Validators.maxLength(150)]],
    kind: [this.p?.kind ?? ('INDIVIDUAL' as PersonKind)],
    document: [formatCpfCnpj(this.p?.document)],
    email: [this.p?.email ?? '', [Validators.email]],
    phone: [formatPhone(this.p?.phone)],
    secondaryPhone: [formatPhone(this.p?.secondaryPhone)],
    notes: [this.p?.notes ?? ''],
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const blank = (s: string) => s.trim() || null;
    this.service
      .savePerson(
        { name: v.name.trim(), kind: v.kind, document: blank(v.document), email: blank(v.email), phone: blank(v.phone),
          secondaryPhone: blank(v.secondaryPhone), notes: blank(v.notes), version: this.p?.version },
        this.p?.id,
      )
      .subscribe({
        next: (person) => this.ref.close(person),
        error: (err: unknown) => {
          this.saving.set(false);
          const problem = toProblem(err);
          if (problem.code === 'PERSON_DOCUMENT_TAKEN' || problem.code === 'DOCUMENT_KIND_MISMATCH') {
            this.form.controls.document.setErrors({ server: problem.detail });
            return;
          }
          const rest = problem.code === 'VALIDATION_ERROR' ? applyServerErrors(this.form, problem) : [problem.detail];
          if (rest.length) this.error.set(rest.join(' '));
        },
      });
  }
}
