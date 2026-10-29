import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { paraProblema } from '../../core/api/api.models';
import { MascaraDirective } from '../../comum/formato/mascara.directive';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { Administradora, AdministradoraService } from './administradora.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

/** Cadastro de administradora + primeiro administrador. A política de senha é validada pelo backend. */
@Component({
  selector: 'tc-administradora-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MascaraDirective, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>Nova administradora <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-8">
            <mat-label>Nome</mat-label>
            <input matInput formControlName="nome" required cdkFocusInitial />
            <mat-error><tc-erro-campo [controle]="form.controls.nome" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-4">
            <mat-label>CNPJ</mat-label>
            <input matInput formControlName="cnpj" tcMascara="cnpj" class="mono" required />
            <mat-error><tc-erro-campo [controle]="form.controls.cnpj" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-8">
            <mat-label>E-mail da administradora</mat-label>
            <input matInput type="email" formControlName="email" />
            <mat-error><tc-erro-campo [controle]="form.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-4">
            <mat-label>Telefone</mat-label>
            <input matInput type="tel" formControlName="telefone" tcMascara="telefone" />
            <mat-error><tc-erro-campo [controle]="form.controls.telefone" /></mat-error>
          </mat-form-field>
        </div>

        <h3 class="tc-section-title">Primeiro administrador</h3>
        <div class="tc-grid" formGroupName="administrador">
          <mat-form-field class="span-6">
            <mat-label>Nome</mat-label>
            <input matInput formControlName="nome" required />
            <mat-error><tc-erro-campo [controle]="form.controls.administrador.controls.nome" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail de acesso</mat-label>
            <input matInput type="email" formControlName="email" required autocomplete="off" />
            <mat-error><tc-erro-campo [controle]="form.controls.administrador.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Senha inicial</mat-label>
            <input matInput type="password" formControlName="senha" required autocomplete="new-password" />
            <mat-hint>Mínimo de 10 caracteres, com letras e números.</mat-hint>
            <mat-error><tc-erro-campo [controle]="form.controls.administrador.controls.senha" /></mat-error>
          </mat-form-field>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Cadastrar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    h3.tc-section-title { margin-top: 16px; }
  `,
})
export class AdministradoraDialog {
  private readonly service = inject(AdministradoraService);
  private readonly ref = inject(MatDialogRef<AdministradoraDialog, Administradora>);

  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);

  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly form = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    cnpj: ['', [Validators.required]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    telefone: [''],
    administrador: this.fb.group({
      nome: ['', [Validators.required, Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      senha: ['', [Validators.required]],
    }),
  });

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const v = this.form.getRawValue();
    this.service
      .cadastrar({ ...v, email: v.email.trim() || null, telefone: v.telefone.trim() || null })
      .subscribe({
        next: (administradora) => this.ref.close(administradora),
        error: (err: unknown) => {
          this.salvando.set(false);
          const problema = paraProblema(err);
          const restante = problema.codigo === 'VALIDATION_ERROR' ? aplicarErrosServidor(this.form, problema) : [];
          if (problema.codigo !== 'VALIDATION_ERROR' || restante.length) {
            this.erro.set(restante.length ? restante.join(' ') : problema.detail);
          }
        },
      });
  }
}
