import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { passwordsMatch } from './password-match';
import { PasswordService } from './password.service';

/** Troca de senha pelo próprio usuário. As demais sessões abertas são encerradas pelo backend. */
@Component({
  selector: 'tc-change-password-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FieldError],
  template: `
    <h2 mat-dialog-title>Alterar senha</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="alert" role="alert">{{ message }}</p>
        }
        <mat-form-field class="wide">
          <mat-label>Senha atual</mat-label>
          <input matInput type="password" formControlName="currentPassword" autocomplete="current-password" required cdkFocusInitial />
          <mat-error><tc-field-error [control]="form.controls.currentPassword" /></mat-error>
        </mat-form-field>
        <mat-form-field class="wide">
          <mat-label>Nova senha</mat-label>
          <input matInput type="password" formControlName="newPassword" autocomplete="new-password" required />
          <mat-hint>Mínimo de 10 caracteres, com letras e números.</mat-hint>
          <mat-error><tc-field-error [control]="form.controls.newPassword" /></mat-error>
        </mat-form-field>
        <mat-form-field class="wide">
          <mat-label>Confirme a nova senha</mat-label>
          <input matInput type="password" formControlName="confirm" autocomplete="new-password" required />
          <mat-error><tc-field-error [control]="form.controls.confirm" /></mat-error>
        </mat-form-field>
        <p class="tc-muted note">Por segurança, suas outras sessões abertas (outros navegadores/dispositivos) serão encerradas.</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Alterar senha' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .wide { width: 100%; margin-bottom: 8px; }
    .note { font: var(--mat-sys-body-small); margin: 0; }
    .alert { padding: 10px 12px; border-radius: 10px; background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
  `,
})
export class ChangePasswordDialog {
  private readonly service = inject(PasswordService);
  private readonly ref = inject(MatDialogRef<ChangePasswordDialog, boolean>);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch('newPassword', 'confirm') },
  );

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service.change(v.currentPassword, v.newPassword).subscribe({
      next: () => this.ref.close(true),
      error: (err: unknown) => {
        this.saving.set(false);
        const problem = toProblem(err);
        if (problem.code === 'WRONG_CURRENT_PASSWORD') {
          this.form.controls.currentPassword.setErrors({ server: problem.detail });
        } else if (problem.code === 'SAME_PASSWORD') {
          this.form.controls.newPassword.setErrors({ server: problem.detail });
        } else if (problem.code === 'VALIDATION_ERROR') {
          const rest = applyServerErrors(this.form, problem);
          if (rest.length) this.error.set(rest.join(' '));
        } else {
          this.error.set(problem.detail);
        }
      },
    });
  }
}
