import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../shared/forms/field-error';
import { AuthCard } from './auth-card';
import { PasswordService } from './password.service';

@Component({
  selector: 'tc-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, FieldError, AuthCard],
  template: `
    <tc-auth-card title="Esqueci minha senha">
      @if (sent()) {
        <div class="done" role="status">
          <mat-icon>mark_email_read</mat-icon>
          <p>Se houver uma conta ativa para <strong>{{ email.value }}</strong>, enviaremos em instantes um link para criar
            uma nova senha. Verifique também a caixa de spam.</p>
        </div>
        <a mat-flat-button routerLink="/login" class="wide">Voltar ao login</a>
      } @else {
        <p class="tc-muted">Informe o e-mail cadastrado. Você receberá um link válido por tempo limitado.</p>
        @if (error(); as message) {
          <p class="alert" role="alert">{{ message }}</p>
        }
        <form (ngSubmit)="submit()" novalidate>
          <mat-form-field class="wide">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" [formControl]="email" autocomplete="username" required />
            <mat-icon matPrefix>mail</mat-icon>
            <mat-error><tc-field-error [control]="email" /></mat-error>
          </mat-form-field>
          <button mat-flat-button type="submit" class="wide" [disabled]="loading()">{{ loading() ? 'Enviando…' : 'Enviar link' }}</button>
        </form>
        <a mat-button routerLink="/login" class="wide back">Voltar ao login</a>
      }
    </tc-auth-card>
  `,
  styles: `
    .wide { width: 100%; }
    button.wide, a.wide { height: 48px; }
    .back { margin-top: 8px; }
    .done { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 16px; }
    .done mat-icon { color: var(--mat-sys-primary); flex: none; }
    .done p { margin: 0; }
    .alert { padding: 10px 12px; border-radius: 10px; background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
  `,
})
export class ForgotPassword {
  private readonly service = inject(PasswordService);
  protected readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] });
  protected readonly loading = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  submit(): void {
    this.email.markAsTouched();
    if (this.email.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.forgot(this.email.value.trim()).subscribe({
      next: () => {
        this.loading.set(false);
        this.sent.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toProblem(err).detail);
      },
    });
  }
}
