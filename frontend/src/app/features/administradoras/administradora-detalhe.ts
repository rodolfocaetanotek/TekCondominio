import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { ConfirmacaoDialog, DadosConfirmacaoDialog } from '../../comum/ui/confirmacao-dialog';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { Administradora, AdministradoraService, UsuarioAdministradora } from './administradora.service';

/**
 * Ficha da administradora para o administrador da plataforma: usuários, convite de administradores e situação.
 * A gestão completa de usuários (papéis por condomínio) é feita pelos administradores da própria administradora.
 */
@Component({
  selector: 'tc-administradora-detalhe',
  imports: [DatePipe, DocumentoPipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatProgressBarModule, MensagemErroCampo, CabecalhoPagina, EstadoVazio],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/organizacoes" class="back"><mat-icon>arrow_back</mat-icon> Administradoras</a>
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível abrir" [descricao]="mensagem" /></section>
      } @else if (administradora(); as o) {
        <tc-cabecalho-pagina sobretitulo="Administradora" [titulo]="o.nome" [subtitulo]="'CNPJ ' + (o.cnpj | documento: 'cnpj')">
          <span class="tc-status big" [class.is-active]="o.ativo" [class.is-inactive]="!o.ativo">{{ o.ativo ? 'Ativa' : 'Inativa' }}</span>
          <button mat-stroked-button (click)="alternarAtivo(o)" [class.danger-text]="o.ativo">
            <mat-icon>{{ o.ativo ? 'block' : 'check_circle' }}</mat-icon>{{ o.ativo ? 'Inativar' : 'Reativar' }}
          </button>
        </tc-cabecalho-pagina>

        <div class="layout">
          <section class="tc-table-card">
            <h2 class="tc-section-title tc-card-head is-divided">Usuários ({{ usuarios().length }})</h2>
            @for (u of usuarios(); track u.id) {
              <div class="row">
                <div class="who">
                  <strong>{{ u.nome }}</strong>
                  <span class="tc-muted">{{ u.email }} · {{ u.papeis.join(', ') || 'sem papel' }}</span>
                </div>
                <div class="row-end">
                @if (u.convitePendente && u.ativo) {
                  <span class="tc-status is-pending">Convite pendente</span>
                } @else if (!u.ativo) {
                  <span class="tc-status is-inactive">Inativo</span>
                } @else {
                  <span class="tc-muted small">{{ u.ultimoLoginEm ? 'Último acesso ' + (u.ultimoLoginEm | date: 'dd/MM/yyyy') : '' }}</span>
                }
                </div>
              </div>
            } @empty {
              <tc-estado-vazio icone="group" titulo="Nenhum usuário" />
            }
          </section>

          <section class="tc-card">
            <h2 class="tc-section-title">Convidar administrador</h2>
            <p class="tc-muted small">A pessoa recebe um e-mail para definir a própria senha e passa a gerenciar condomínios,
              usuários e papéis desta administradora (menu <strong>Usuários</strong>).</p>
            <form [formGroup]="form" (ngSubmit)="convidar(o)" novalidate>
              <mat-form-field class="full">
                <mat-label>Nome</mat-label>
                <input matInput formControlName="nome" required />
                <mat-error><tc-erro-campo [controle]="form.controls.nome" /></mat-error>
              </mat-form-field>
              <mat-form-field class="full">
                <mat-label>E-mail</mat-label>
                <input matInput type="email" formControlName="email" required autocomplete="off" />
                <mat-error><tc-erro-campo [controle]="form.controls.email" /></mat-error>
              </mat-form-field>
              <div class="tc-actions">
                <button mat-flat-button type="submit" [disabled]="ocupado()"><mat-icon>send</mat-icon> Enviar convite</button>
              </div>
            </form>
          </section>
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; padding: 12px 20px; }
    .row + .row { border-top: 1px solid var(--tc-border); }
    .who { flex: 1 1 220px; min-width: 0; display: grid; gap: 2px; overflow-wrap: anywhere; }
    .row-end { margin-left: auto; }
    form { display: grid; gap: 4px; margin-top: 12px; }
    @media (max-width: 600px) { .row { padding-inline: 16px; } }
    .small { font: var(--mat-sys-body-small); }
    .full { width: 100%; }
    .tc-section-title { margin-top: 0; }
    .danger-text { --mat-button-outlined-label-text-color: var(--tc-danger); }
  `,
})
export class AdministradoraDetalhe implements OnInit {
  private readonly service = inject(AdministradoraService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);

  readonly id = input.required<string>();
  protected readonly administradora = signal<Administradora | null>(null);
  protected readonly usuarios = signal<UsuarioAdministradora[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.service.obter(this.id()).subscribe({
      next: (o) => this.administradora.set(o),
      error: (err: unknown) => this.erro.set(paraProblema(err).detail),
    });
    this.carregarUsuarios();
  }

  convidar(o: Administradora): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.ocupado()) return;
    this.ocupado.set(true);
    const { nome, email } = this.form.getRawValue();
    this.service.convidarAdministrador(o.id, nome, email).subscribe({
      next: (u) => {
        this.ocupado.set(false);
        this.form.reset();
        this.notificacao.sucesso(`Convite enviado para ${u.email}.`);
        this.carregarUsuarios();
      },
      error: (err: unknown) => {
        this.ocupado.set(false);
        const p = paraProblema(err);
        if (p.codigo === 'USER_EMAIL_TAKEN') this.form.controls.email.setErrors({ server: p.detail });
        else this.notificacao.erro(p.detail);
      },
    });
  }

  alternarAtivo(o: Administradora): void {
    const data: DadosConfirmacaoDialog = o.ativo
      ? { titulo: 'Inativar administradora', mensagem: `Todos os usuários de "${o.nome}" perderão o acesso imediatamente. Os dados são preservados.`, rotuloConfirmar: 'Inativar', perigo: true }
      : { titulo: 'Reativar administradora', mensagem: `Reativar o acesso de "${o.nome}"?`, rotuloConfirmar: 'Reativar' };
    this.dialog
      .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter((r) => r === true),
        switchMap(() => this.service.alterarAtivo(o.id, !o.ativo)),
      )
      .subscribe({
        next: (atualizado) => {
          this.administradora.set(atualizado);
          this.notificacao.sucesso(atualizado.ativo ? 'Administradora reativada.' : 'Administradora inativada.');
        },
        error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
      });
  }

  private carregarUsuarios(): void {
    this.service.usuarios(this.id()).subscribe({ next: (u) => this.usuarios.set(u) });
  }
}
