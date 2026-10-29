import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { CartaoAcesso } from './cartao-acesso';
import { senhasIguais } from './senhas-iguais';
import { SenhaService, TokenSenhaInfo } from './senha.service';

/**
 * Definição de senha a partir do link enviado por e-mail (convite ou redefinição).
 * O token chega no fragmento da URL (#token=...) e é removido da barra de endereços assim que lido.
 */
@Component({
  selector: 'tc-definir-senha',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule, MensagemErroCampo, CartaoAcesso],
  template: `
    <tc-cartao-acesso [titulo]="info()?.finalidade === 'INVITE' ? 'Ative seu acesso' : 'Criar nova senha'">
      @if (verificando()) {
        <mat-progress-bar mode="indeterminate" aria-label="Verificando link" />
      } @else if (invalido(); as mensagem) {
        <div class="state" role="alert">
          <mat-icon>link_off</mat-icon>
          <p>{{ mensagem }}</p>
        </div>
        <a mat-flat-button routerLink="/esqueci-senha" class="wide">Solicitar novo link</a>
        <a mat-button routerLink="/login" class="wide">Ir para o login</a>
      } @else if (info(); as i) {
        <p class="tc-muted">
          {{ i.finalidade === 'INVITE' ? 'Bem-vindo(a), ' + i.nome + '! Crie uma senha para' : 'Defina a nova senha de' }}
          <strong>{{ i.email }}</strong>.
        </p>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="enviar()" novalidate>
          <input type="email" [value]="i.email" autocomplete="username" hidden aria-hidden="true" />
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
          <button mat-flat-button type="submit" class="wide" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Salvar senha' }}</button>
        </form>
      }
    </tc-cartao-acesso>
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
export class DefinirSenha implements OnInit {
  private readonly service = inject(SenhaService);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);

  private token = '';
  protected readonly verificando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly info = signal<TokenSenhaInfo | null>(null);
  protected readonly invalido = signal<string | null>(null);
  protected readonly erro = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      novaSenha: ['', [Validators.required]],
      confirmar: ['', [Validators.required]],
    },
    { validators: senhasIguais('novaSenha', 'confirmar') },
  );

  ngOnInit(): void {
    const fragmento = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    this.token = fragmento.get('token') ?? '';
    // Remove o token da barra de endereços e do histórico.
    history.replaceState(history.state, '', window.location.pathname);
    if (!this.token) {
      this.verificando.set(false);
      this.invalido.set('Link incompleto. Abra novamente o link recebido por e-mail.');
      return;
    }
    this.service.consultarToken(this.token).subscribe({
      next: (info) => {
        this.info.set(info);
        this.verificando.set(false);
      },
      error: (err: unknown) => {
        this.invalido.set(paraProblema(err).detail);
        this.verificando.set(false);
      },
    });
  }

  enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    this.service.redefinir(this.token, this.form.controls.novaSenha.value).subscribe({
      next: () => {
        this.notificacao.sucesso('Senha definida. Entre com seu e-mail e a nova senha.');
        void this.router.navigate(['/login']);
      },
      error: (err: unknown) => {
        this.salvando.set(false);
        const problema = paraProblema(err);
        if (problema.codigo === 'INVALID_LINK') {
          this.invalido.set(problema.detail);
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
