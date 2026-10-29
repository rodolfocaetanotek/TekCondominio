import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../comum/formularios/erro-campo';
import { applyServerErrors } from '../../comum/formularios/erros-servidor';
import { passwordsMatch } from './senhas-iguais';
import { PasswordService } from './senha.service';
import { DialogClose } from '../../comum/ui/fechar-dialog';

/** Troca de senha pelo próprio usuário. As demais sessões abertas são encerradas pelo backend. */
@Component({
  selector: 'tc-change-password-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FieldError],
  template: `
    <h2 mat-dialog-title>Alterar senha <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <mat-form-field class="wide">
          <mat-label>Senha atual</mat-label>
          <input matInput type="password" formControlName="senhaAtual" autocomplete="current-password" required cdkFocusInitial />
          <mat-error><tc-field-error [control]="form.controls.senhaAtual" /></mat-error>
        </mat-form-field>
        <mat-form-field class="wide">
          <mat-label>Nova senha</mat-label>
          <input matInput type="password" formControlName="novaSenha" autocomplete="new-password" required />
          <mat-hint>Mínimo de 10 caracteres, com letras e números.</mat-hint>
          <mat-error><tc-field-error [control]="form.controls.novaSenha" /></mat-error>
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
  `,
})
export class ChangePasswordDialog {
  private readonly service = inject(PasswordService);
  private readonly ref = inject(MatDialogRef<ChangePasswordDialog, boolean>);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      senhaAtual: ['', [Validators.required]],
      novaSenha: ['', [Validators.required]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch('novaSenha', 'confirm') },
  );

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service.change(v.senhaAtual, v.novaSenha).subscribe({
      next: () => this.ref.close(true),
      error: (err: unknown) => {
        this.saving.set(false);
        const problem = toProblem(err);
        if (problem.codigo === 'WRONG_CURRENT_PASSWORD') {
          this.form.controls.senhaAtual.setErrors({ server: problem.detail });
        } else if (problem.codigo === 'SAME_PASSWORD') {
          this.form.controls.novaSenha.setErrors({ server: problem.detail });
        } else if (problem.codigo === 'VALIDATION_ERROR') {
          const rest = applyServerErrors(this.form, problem);
          if (rest.length) this.error.set(rest.join(' '));
        } else {
          this.error.set(problem.detail);
        }
      },
    });
  }
}
