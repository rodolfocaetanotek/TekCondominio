import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { toProblem } from '../../core/api/api.models';
import { MaskDirective } from '../../shared/format/mask.directive';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { Organization, OrganizationService } from './organization.service';
import { DialogClose } from '../../shared/ui/dialog-close';

/** Cadastro de administradora + primeiro administrador. A política de senha é validada pelo backend. */
@Component({
  selector: 'tc-organization-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MaskDirective, FieldError],
  template: `
    <h2 mat-dialog-title>Nova administradora <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-8">
            <mat-label>Nome</mat-label>
            <input matInput formControlName="name" required cdkFocusInitial />
            <mat-error><tc-field-error [control]="form.controls.name" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-4">
            <mat-label>CNPJ</mat-label>
            <input matInput formControlName="cnpj" tcMask="cnpj" class="mono" required />
            <mat-error><tc-field-error [control]="form.controls.cnpj" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-8">
            <mat-label>E-mail da administradora</mat-label>
            <input matInput type="email" formControlName="email" />
            <mat-error><tc-field-error [control]="form.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-4">
            <mat-label>Telefone</mat-label>
            <input matInput type="tel" formControlName="phone" tcMask="phone" />
            <mat-error><tc-field-error [control]="form.controls.phone" /></mat-error>
          </mat-form-field>
        </div>

        <h3 class="tc-section-title">Primeiro administrador</h3>
        <div class="tc-grid" formGroupName="admin">
          <mat-form-field class="span-6">
            <mat-label>Nome</mat-label>
            <input matInput formControlName="name" required />
            <mat-error><tc-field-error [control]="form.controls.admin.controls.name" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail de acesso</mat-label>
            <input matInput type="email" formControlName="email" required autocomplete="off" />
            <mat-error><tc-field-error [control]="form.controls.admin.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Senha inicial</mat-label>
            <input matInput type="password" formControlName="password" required autocomplete="new-password" />
            <mat-hint>Mínimo de 10 caracteres, com letras e números.</mat-hint>
            <mat-error><tc-field-error [control]="form.controls.admin.controls.password" /></mat-error>
          </mat-form-field>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Cadastrar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    h3.tc-section-title { margin-top: 16px; }
  `,
})
export class OrganizationDialog {
  private readonly service = inject(OrganizationService);
  private readonly ref = inject(MatDialogRef<OrganizationDialog, Organization>);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    cnpj: ['', [Validators.required]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    phone: [''],
    admin: this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required]],
    }),
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service
      .create({ ...v, email: v.email.trim() || null, phone: v.phone.trim() || null })
      .subscribe({
        next: (org) => this.ref.close(org),
        error: (err: unknown) => {
          this.saving.set(false);
          const problem = toProblem(err);
          const rest = problem.code === 'VALIDATION_ERROR' ? applyServerErrors(this.form, problem) : [];
          if (problem.code !== 'VALIDATION_ERROR' || rest.length) {
            this.error.set(rest.length ? rest.join(' ') : problem.detail);
          }
        },
      });
  }
}
