import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { RoleFields } from './role-fields';
import { UsuarioDetalhe } from './user.models';
import { UserService } from './user.service';
import { DialogClose } from '../../shared/ui/dialog-close';

/** Novo usuário: a pessoa recebe um convite por e-mail e define a própria senha (ninguém mais a conhece). */
@Component({
  selector: 'tc-user-create-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatIconModule, FieldError, RoleFields],
  template: `
    <h2 mat-dialog-title>Novo usuário <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <p class="tc-muted info"><mat-icon>mail</mat-icon> Enviaremos um convite por e-mail para a pessoa definir a própria senha.</p>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Nome completo</mat-label>
            <input matInput formControlName="nome" required maxlength="150" cdkFocusInitial />
            <mat-error><tc-field-error [control]="form.controls.nome" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" formControlName="email" required autocomplete="off" />
            <mat-error><tc-field-error [control]="form.controls.email" /></mat-error>
          </mat-form-field>
        </div>
        <h3 class="tc-section-title">Acesso inicial</h3>
        <tc-role-fields [roleCode]="roleCode" [condominiumId]="condominiumId" />
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Enviando…' : 'Cadastrar e convidar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .info { display: flex; align-items: center; gap: 8px; margin-top: 0; }
  `,
})
export class UserCreateDialog {
  private readonly service = inject(UserService);
  private readonly ref = inject(MatDialogRef<UserCreateDialog, UsuarioDetalhe>);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly roleCode = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly condominiumId = new FormControl<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    atribuicao: inject(FormBuilder).group({ codigoPapel: this.roleCode, idCondominio: this.condominiumId }),
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.service
      .create({
        nome: v.nome.trim(),
        email: v.email.trim(),
        atribuicao: { codigoPapel: v.atribuicao.codigoPapel ?? '', idCondominio: v.atribuicao.idCondominio ?? null },
      })
      .subscribe({
        next: (user) => this.ref.close(user),
        error: (err: unknown) => {
          this.saving.set(false);
          const problem = toProblem(err);
          if (problem.codigo === 'USER_EMAIL_TAKEN') {
            this.form.controls.email.setErrors({ server: problem.detail });
            return;
          }
          const rest = problem.codigo === 'VALIDATION_ERROR' ? applyServerErrors(this.form, problem) : [problem.detail];
          if (rest.length) this.error.set(rest.join(' '));
        },
      });
  }
}
