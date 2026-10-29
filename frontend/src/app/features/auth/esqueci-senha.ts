import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { paraProblema } from '../../core/api/api.models';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { CartaoAcesso } from './cartao-acesso';
import { SenhaService } from './senha.service';

@Component({
  selector: 'tc-esqueci-senha',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MensagemErroCampo, CartaoAcesso],
  template: `
    <tc-cartao-acesso titulo="Esqueci minha senha">
      @if (enviado()) {
        <div class="done" role="status">
          <mat-icon>mark_email_read</mat-icon>
          <p>Se houver uma conta ativa para <strong>{{ email.value }}</strong>, enviaremos em instantes um link para criar
            uma nova senha. Verifique também a caixa de spam.</p>
        </div>
        <a mat-flat-button routerLink="/login" class="wide">Voltar ao login</a>
      } @else {
        <p class="tc-muted">Informe o e-mail cadastrado. Você receberá um link válido por tempo limitado.</p>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <form (ngSubmit)="enviar()" novalidate>
          <mat-form-field class="wide">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" [formControl]="email" autocomplete="username" required />
            <mat-icon matPrefix>mail</mat-icon>
            <mat-error><tc-erro-campo [controle]="email" /></mat-error>
          </mat-form-field>
          <button mat-flat-button type="submit" class="wide" [disabled]="carregando()">{{ carregando() ? 'Enviando…' : 'Enviar link' }}</button>
        </form>
        <a mat-button routerLink="/login" class="wide back">Voltar ao login</a>
      }
    </tc-cartao-acesso>
  `,
  styles: `
    .wide { width: 100%; }
    button.wide, a.wide { height: 48px; }
    .back { margin-top: 8px; }
    .done { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 16px; padding: 12px 14px;
      border-radius: var(--tc-radius-sm); background: var(--tc-success-container); }
    .done mat-icon { color: var(--tc-success); flex: none; }
    .done p { margin: 0; }
  `,
})
export class EsqueciSenha {
  private readonly service = inject(SenhaService);
  protected readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] });
  protected readonly carregando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erro = signal<string | null>(null);

  enviar(): void {
    this.email.markAsTouched();
    if (this.email.invalid || this.carregando()) return;
    this.carregando.set(true);
    this.erro.set(null);
    this.service.esqueciSenha(this.email.value.trim()).subscribe({
      next: () => {
        this.carregando.set(false);
        this.enviado.set(true);
      },
      error: (err: unknown) => {
        this.carregando.set(false);
        this.erro.set(paraProblema(err).detail);
      },
    });
  }
}
