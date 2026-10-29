import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { paraProblema } from '../../core/api/api.models';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { senhasIguais } from './senhas-iguais';
import { SenhaService } from './senha.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

/** Troca de senha pelo próprio usuário. As demais sessões abertas são encerradas pelo backend. */
@Component({
  selector: 'tc-alterar-senha-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>Alterar senha <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <mat-form-field class="wide">
          <mat-label>Senha atual</mat-label>
          <input matInput type="password" formControlName="senhaAtual" autocomplete="current-password" required cdkFocusInitial />
          <mat-error><tc-erro-campo [controle]="form.controls.senhaAtual" /></mat-error>
        </mat-form-field>
        <mat-form-field class="wide">
          <mat-label>Nova senha</mat-label>
          <input matInput type="password" formControlName="novaSenha" autocomplete="new-password" required />
          <mat-hint>Mínimo de 10 caracteres, com letras e números.</mat-hint>
          <mat-error><tc-erro-campo [controle]="form.controls.novaSenha" /></mat-error>
        </mat-form-field>
        <mat-form-field class="wide">
          <mat-label>Confirme a nova senha</mat-label>
          <input matInput type="password" formControlName="confirmar" autocomplete="new-password" required />
          <mat-error><tc-erro-campo [controle]="form.controls.confirmar" /></mat-error>
        </mat-form-field>
        <p class="tc-muted note">Por segurança, suas outras sessões abertas (outros navegadores/dispositivos) serão encerradas.</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Alterar senha' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .wide { width: 100%; margin-bottom: 8px; }
    .note { font: var(--mat-sys-body-small); margin: 0; }
  `,
})
export class AlterarSenhaDialog {
  private readonly service = inject(SenhaService);
  private readonly ref = inject(MatDialogRef<AlterarSenhaDialog, boolean>);

  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      senhaAtual: ['', [Validators.required]],
      novaSenha: ['', [Validators.required]],
      confirmar: ['', [Validators.required]],
    },
    { validators: senhasIguais('novaSenha', 'confirmar') },
  );

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const v = this.form.getRawValue();
    this.service.alterar(v.senhaAtual, v.novaSenha).subscribe({
      next: () => this.ref.close(true),
      error: (err: unknown) => {
        this.salvando.set(false);
        const problema = paraProblema(err);
        if (problema.codigo === 'WRONG_CURRENT_PASSWORD') {
          this.form.controls.senhaAtual.setErrors({ server: problema.detail });
        } else if (problema.codigo === 'SAME_PASSWORD') {
          this.form.controls.novaSenha.setErrors({ server: problema.detail });
        } else if (problema.codigo === 'VALIDATION_ERROR') {
          const restante = aplicarErrosServidor(this.form, problema);
          if (restante.length) this.erro.set(restante.join(' '));
        } else {
          this.erro.set(problema.detail);
        }
      },
    });
  }
}
