import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { paraProblema } from '../../core/api/api.models';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { CamposPapel } from './campos-papel';
import { UsuarioDetalhe } from './usuario.models';
import { UsuarioService } from './usuario.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

/** Novo usuário: a pessoa recebe um convite por e-mail e define a própria senha (ninguém mais a conhece). */
@Component({
  selector: 'tc-usuario-cadastro-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatIconModule, MensagemErroCampo, CamposPapel],
  template: `
    <h2 mat-dialog-title>Novo usuário <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        <p class="tc-muted info"><mat-icon>mail</mat-icon> Enviaremos um convite por e-mail para a pessoa definir a própria senha.</p>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Nome completo</mat-label>
            <input matInput formControlName="nome" required maxlength="150" cdkFocusInitial />
            <mat-error><tc-erro-campo [controle]="form.controls.nome" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" formControlName="email" required autocomplete="off" />
            <mat-error><tc-erro-campo [controle]="form.controls.email" /></mat-error>
          </mat-form-field>
        </div>
        <h3 class="tc-section-title">Acesso inicial</h3>
        <tc-campos-papel [codigoPapel]="codigoPapel" [idCondominio]="idCondominio" />
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Enviando…' : 'Cadastrar e convidar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .info { display: flex; align-items: center; gap: 8px; margin-top: 0; }
  `,
})
export class UsuarioCadastroDialog {
  private readonly service = inject(UsuarioService);
  private readonly ref = inject(MatDialogRef<UsuarioCadastroDialog, UsuarioDetalhe>);

  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly codigoPapel = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly idCondominio = new FormControl<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    atribuicao: inject(FormBuilder).group({ codigoPapel: this.codigoPapel, idCondominio: this.idCondominio }),
  });

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const v = this.form.getRawValue();
    this.service
      .cadastrar({
        nome: v.nome.trim(),
        email: v.email.trim(),
        atribuicao: { codigoPapel: v.atribuicao.codigoPapel ?? '', idCondominio: v.atribuicao.idCondominio ?? null },
      })
      .subscribe({
        next: (usuario) => this.ref.close(usuario),
        error: (err: unknown) => {
          this.salvando.set(false);
          const problema = paraProblema(err);
          if (problema.codigo === 'USER_EMAIL_TAKEN') {
            this.form.controls.email.setErrors({ server: problema.detail });
            return;
          }
          const restante = problema.codigo === 'VALIDATION_ERROR' ? aplicarErrosServidor(this.form, problema) : [problema.detail];
          if (restante.length) this.erro.set(restante.join(' '));
        },
      });
  }
}
