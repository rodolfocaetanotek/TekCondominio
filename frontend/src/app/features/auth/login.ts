import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';

@Component({
  selector: 'tc-login',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule, MensagemErroCampo],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Parâmetros de rota (?retorno=/caminho&expirada=1). */
  readonly retorno = input<string>();
  readonly expirada = input<string>();

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly mostrarSenha = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    senha: ['', [Validators.required, Validators.maxLength(200)]],
  });

  enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.carregando()) {
      return;
    }
    this.carregando.set(true);
    this.erro.set(null);
    const { email, senha } = this.form.getRawValue();
    this.auth.login(email, senha).subscribe({
      next: () => {
        const alvo = this.retorno();
        // Apenas caminhos internos: evita redirecionamento aberto.
        void this.router.navigateByUrl(alvo && alvo.startsWith('/') && !alvo.startsWith('//') ? alvo : '/');
      },
      error: (err: unknown) => {
        this.carregando.set(false);
        this.erro.set(paraProblema(err).detail);
      },
    });
  }
}
