import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { AuthCard } from './auth-card';
import { passwordsMatch } from './password-match';
import { PasswordService, TokenSenhaInfo } from './password.service';

/**
 * Definição de senha a partir do link enviado por e-mail (convite ou redefinição).
 * O token chega no fragmento da URL (#token=...) e é removido da barra de endereços assim que lido.
 */
@Component({
  selector: 'tc-set-password',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule, FieldError, AuthCard],
  template: `
    <tc-auth-card [title]="info()?.finalidade === 'INVITE' ? 'Ative seu acesso' : 'Criar nova senha'">
      @if (checking()) {
        <mat-progress-bar mode="indeterminate" aria-label="Verificando link" />
      } @else if (invalid(); as message) {
        <div class="state" role="alert">
          <mat-icon>link_off</mat-icon>
          <p>{{ message }}</p>
        </div>
        <a mat-flat-button routerLink="/esqueci-senha" class="wide">Solicitar novo link</a>
        <a mat-button routerLink="/login" class="wide">Ir para o login</a>
      } @else if (info(); as i) {
        <p class="tc-muted">
          {{ i.finalidade === 'INVITE' ? 'Bem-vindo(a), ' + i.nome + '! Crie uma senha para' : 'Defina a nova senha de' }}
          <strong>{{ i.email }}</strong>.
        </p>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <input type="email" [value]="i.email" autocomplete="username" hidden aria-hidden="true" />
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
          <button mat-flat-button type="submit" class="wide" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar senha' }}</button>
        </form>
      }
    </tc-auth-card>
  `,
  styles: `
    .wide { width: 100%; }
    button.wide, a.wide { height: 48px; margin-top: 8px; }
    mat-form-field { margin-bottom: 8px; }
    .state { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 12px; padding: 12px 14px;
      border-radius: var(--tc-radius-sm); background: var(--tc-danger-container); color: var(--mat-sys-on-error-container); }
    .state mat-icon { color: var(--tc-danger); flex: none; }
    .state p { margin: 0; }
  `,
})
export class SetPassword implements OnInit {
  private readonly service = inject(PasswordService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  private token = '';
  protected readonly checking = signal(true);
  protected readonly saving = signal(false);
  protected readonly info = signal<TokenSenhaInfo | null>(null);
  protected readonly invalid = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      novaSenha: ['', [Validators.required]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch('novaSenha', 'confirm') },
  );

  ngOnInit(): void {
    const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    this.token = fragment.get('token') ?? '';
    // Remove o token da barra de endereços e do histórico.
    history.replaceState(history.state, '', window.location.pathname);
    if (!this.token) {
      this.checking.set(false);
      this.invalid.set('Link incompleto. Abra novamente o link recebido por e-mail.');
      return;
    }
    this.service.describe(this.token).subscribe({
      next: (info) => {
        this.info.set(info);
        this.checking.set(false);
      },
      error: (err: unknown) => {
        this.invalid.set(toProblem(err).detail);
        this.checking.set(false);
      },
    });
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.service.reset(this.token, this.form.controls.novaSenha.value).subscribe({
      next: () => {
        this.notify.success('Senha definida. Entre com seu e-mail e a nova senha.');
        void this.router.navigate(['/login']);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const problem = toProblem(err);
        if (problem.codigo === 'INVALID_LINK') {
          this.invalid.set(problem.detail);
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
